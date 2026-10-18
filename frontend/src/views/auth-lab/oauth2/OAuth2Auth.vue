<!--
  OAuth2 三方认证演示（主入口，弹窗流程）。
  本地账号：POST /api/oauth2-client/auth/login（form-urlencoded，可能 302 → redirect manual + raw）。
  三方登录：window.open('/api/oauth2-client/authorization/{provider}')，
            回传结果用双通道 —— 500ms 轮询弹窗关闭 + postMessage(type: 'oauth2-login-result')；
            弹窗被浏览器拦截时兜底当前窗口跳转。
  postMessage 做来源白名单校验；provider 列表单一来源于 api/oauth2.js 的 providers。
-->
<script setup>
import { computed, reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import oauth2, { providers, providerLabel, defaultAvatar } from '../../../api/oauth2'
import PageHeader from '../../../components/PageHeader.vue'
import StatusBar from '../../../components/StatusBar.vue'
import AppCard from '../../../components/AppCard.vue'
import FormField from '../../../components/FormField.vue'
import AppButton from '../../../components/AppButton.vue'
import CodeBlock from '../../../components/CodeBlock.vue'

const route = useRoute()

const loading = ref(false)
const loggedIn = ref(false)
const profile = ref(null)
const responseText = ref('')
const loginError = ref('')
const loginForm = reactive({ username: '', password: '' })

let popupWindow = null
let popupTimer = null

const isPopup = computed(() => !!window.opener)
const unboundProviders = computed(() => {
  if (!profile.value || !profile.value.bindings) return providers
  const bound = profile.value.bindings.map(b => b.provider)
  return providers.filter(p => !bound.includes(p.id))
})

function clearPopupTimer () {
  if (popupTimer) { window.clearInterval(popupTimer); popupTimer = null }
}

async function doLogin () {
  loginError.value = ''
  if (!loginForm.username || !loginForm.password) { loginError.value = '请输入用户名和密码'; return }
  loading.value = true
  try {
    const resp = await oauth2.login(loginForm)
    if (resp.ok) { await checkAuthState() } else { loginError.value = (resp.data && resp.data.error) || '登录失败' }
  } catch (e) { loginError.value = '网络错误: ' + e.message } finally { loading.value = false }
}

function oauthLogin (provider) {
  loading.value = true; responseText.value = ''
  const popup = window.open(oauth2.authorizationUrl(provider), 'oauth2-login-popup', 'width=520,height=680,left=200,top=80')
  if (!popup) { window.location.href = oauth2.authorizationUrl(provider); return }
  popupWindow = popup
  popup.focus()
  clearPopupTimer()
  popupTimer = window.setInterval(() => {
    try {
      if (popupWindow && popupWindow.closed) {
        popupWindow = null; clearPopupTimer(); loading.value = false; checkAuthState()
      }
    } catch (e) { /* 跨窗口属性访问可能被拦截，忽略 */ }
  }, 500)
}

async function checkAuthState () {
  try {
    const resp = await oauth2.profile({ manual: true })
    if (resp.ok) {
      const data = resp.data
      loggedIn.value = true
      profile.value = {
        name: data.name,
        email: data.email,
        avatar: data.avatarUrl || data.avatar_url || '',
        provider: data.provider || data.authType || '本地',
        bindings: data.bindings || [],
        hasPassword: data.hasPassword
      }
      if (isPopup.value) notifyOpenerAndClose({ status: 'success' })
    }
  } catch (e) { /* 未登录属正常，保持未登录态 */ }
  loading.value = false
}

async function getProfile () {
  try {
    const data = await oauth2.profile()
    responseText.value = JSON.stringify(data, null, 2)
    profile.value.bindings = data.bindings || []
  } catch (e) { responseText.value = '错误: ' + e.message }
}

async function logout () {
  loggedIn.value = false; profile.value = null
  oauth2.logout()
  responseText.value = '已退出登录'
}

function canUnbind (binding) {
  if (profile.value.hasPassword) return true
  return !!(profile.value.bindings && profile.value.bindings.length > 1)
}

async function unbindAccount (binding) {
  if (!confirm('确定解绑 ' + providerLabel(binding.provider) + '？')) return
  try {
    const resp = await oauth2.unbind(binding.id)
    responseText.value = resp.ok ? '解绑成功' : '失败: ' + ((resp.data && resp.data.error) || '')
    if (resp.ok) getProfile()
  } catch (e) { responseText.value = '解绑失败: ' + e.message }
}

function handlePopupMessage (event) {
  if (event.origin !== window.location.origin && event.origin !== import.meta.env.VITE_AUTH_SERVER_ORIGIN) return
  if (!event.data || event.data.type !== 'oauth2-login-result') return
  clearPopupTimer(); loading.value = false
  if (event.data.status === 'success') { checkAuthState() } else { responseText.value = 'OAuth2 错误: ' + (event.data.error || 'unknown') }
}

function notifyOpenerAndClose (payload) {
  if (window.opener && !window.opener.closed) {
    window.opener.postMessage({ type: 'oauth2-login-result', ...payload }, window.location.origin)
  }
  setTimeout(() => { if (window.opener && !window.opener.closed) window.close() }, 1500)
}

function formatDate (s) { return s ? s.substring(0, 10) : '-' }

onMounted(() => {
  window.addEventListener('message', handlePopupMessage)
  if (route.query.error) { responseText.value = 'OAuth2 错误: ' + route.query.error; return }
  checkAuthState()
})

onBeforeUnmount(() => {
  window.removeEventListener('message', handlePopupMessage)
  clearPopupTimer()
})
</script>

<template>
  <div class="oauth2">
    <PageHeader title="🔗 OAuth2 三方认证" subtitle="本地账号 + 第三方绑定认证演示"/>
    <main class="oauth2__main">
      <div v-if="isPopup" class="oauth2__popup">
        <p v-if="loading">处理中...</p>
        <p v-else-if="loggedIn">登录成功</p>
        <p v-else>授权完成，窗口即将关闭...</p>
      </div>
      <template v-else>
        <template v-if="!loggedIn">
          <h2 class="oauth2__section">本地账号登录</h2>
          <div class="oauth2__form">
            <FormField v-model="loginForm.username" placeholder="用户名"/>
            <FormField v-model="loginForm.password" type="password" placeholder="密码" @enter="doLogin"/>
          </div>
          <div v-if="loginError" class="oauth2__error">{{ loginError }}</div>
          <div class="oauth2__actions">
            <AppButton @click="doLogin" :loading="loading">登录</AppButton>
            <router-link to="/auth-lab/register" class="oauth2__link-btn">注册新账号</router-link>
          </div>
          <div class="oauth2__divider">或通过第三方登录</div>
          <div class="oauth2__actions">
            <AppButton variant="secondary" @click="oauthLogin('lab-client')" :disabled="loading">Lab 登录</AppButton>
            <AppButton variant="secondary" @click="oauthLogin('github')" :disabled="loading">GitHub 登录</AppButton>
            <AppButton variant="secondary" @click="oauthLogin('google')" :disabled="loading">Google 登录</AppButton>
          </div>
        </template>
        <template v-else>
          <StatusBar variant="success">已登录</StatusBar>
          <AppCard class="oauth2__profile-card">
            <img :src="profile.avatar || defaultAvatar" class="oauth2__avatar-lg" alt="头像"/>
            <div>
              <div class="oauth2__name">{{ profile.name }}</div>
              <div v-if="profile.email" class="oauth2__muted">{{ profile.email }}</div>
              <div class="oauth2__muted-sm">{{ profile.provider || '本地账号' }} 登录</div>
            </div>
          </AppCard>
          <div v-if="profile.bindings && profile.bindings.length" class="oauth2__block">
            <h4 class="oauth2__subtitle">已绑定的第三方账号</h4>
            <div v-for="b in profile.bindings" :key="b.id" class="oauth2__row">
              <img :src="b.avatarUrl || defaultAvatar" class="oauth2__avatar" alt=""/>
              <span class="oauth2__chip">{{ providerLabel(b.provider) }}</span>
              <span>{{ b.providerUsername || '-' }}</span>
              <span class="oauth2__date">{{ formatDate(b.bindTime) }}</span>
              <button @click="unbindAccount(b)" :disabled="!canUnbind(b)" class="oauth2__unbind">{{ canUnbind(b) ? '解绑' : '-' }}</button>
            </div>
          </div>
          <div v-if="unboundProviders.length" class="oauth2__block">
            <h4 class="oauth2__subtitle">待绑定的第三方账号</h4>
            <div v-for="p in unboundProviders" :key="p.id" class="oauth2__row">
              <span class="oauth2__chip">{{ p.label }}</span>
              <AppButton size="sm" variant="secondary" @click="oauthLogin(p.id)" class="oauth2__bind-btn">绑定</AppButton>
            </div>
          </div>
          <div class="oauth2__actions">
            <AppButton variant="secondary" @click="getProfile">刷新</AppButton>
            <AppButton variant="danger" @click="logout">退出</AppButton>
          </div>
        </template>
      </template>
      <CodeBlock v-if="responseText">{{ responseText }}</CodeBlock>
    </main>
  </div>
</template>

<style scoped>
.oauth2__main {
  max-width: var(--container-narrow);
}
.oauth2__popup {
  text-align: center;
  padding: var(--space-8) 0;
  color: var(--color-text-secondary);
}
.oauth2__section {
  font-size: var(--font-md);
  font-weight: var(--weight-regular);
  letter-spacing: var(--tracking-label);
  margin-bottom: var(--space-3);
}
.oauth2__form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}
.oauth2__error {
  color: var(--color-error);
  font-size: var(--font-sm);
  margin: var(--space-2) 0;
}
.oauth2__actions {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  align-items: center;
}
.oauth2__link-btn {
  display: inline-flex;
  align-items: center;
  padding: var(--space-2) var(--space-4);
  font-size: var(--font-sm);
  letter-spacing: var(--tracking-label);
  color: var(--color-text);
  background: var(--color-surface);
  border: var(--border);
  border-radius: var(--radius);
}
.oauth2__link-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  text-decoration: none;
}
.oauth2__divider {
  position: relative;
  text-align: center;
  font-size: var(--font-xs);
  color: var(--color-text-muted);
  margin: var(--space-5) 0;
}
.oauth2__divider::before,
.oauth2__divider::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 35%;
  height: 1px;
  background: var(--color-border);
}
.oauth2__divider::before { left: 0; }
.oauth2__divider::after { right: 0; }

.oauth2__profile-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin: var(--space-4) 0;
}
.oauth2__name { font-size: var(--font-md); font-weight: var(--weight-medium); }
.oauth2__muted { font-size: var(--font-sm); color: var(--color-text-secondary); }
.oauth2__muted-sm { font-size: var(--font-xs); color: var(--color-text-muted); }
.oauth2__avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-border-weak);
  flex-shrink: 0;
}
.oauth2__avatar-lg {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-border-weak);
  flex-shrink: 0;
}
.oauth2__block { margin: var(--space-3) 0; }
.oauth2__subtitle {
  font-size: var(--font-sm);
  font-weight: var(--weight-medium);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-2);
}
.oauth2__row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) 0;
  border-bottom: var(--border-weak);
  font-size: var(--font-sm);
}
.oauth2__row:last-child { border-bottom: none; }
.oauth2__chip {
  display: inline-block;
  padding: 1px var(--space-2);
  border: var(--border);
  border-radius: var(--radius-sm);
  font-size: var(--font-xs);
  color: var(--color-text-muted);
  background: var(--color-surface);
}
.oauth2__date {
  color: var(--color-text-muted);
  font-size: var(--font-xs);
  margin-left: auto;
}
.oauth2__unbind {
  padding: 1px var(--space-2);
  font-size: var(--font-xs);
  border: 1px solid var(--color-error);
  color: var(--color-error);
  background: var(--color-surface);
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.oauth2__unbind:disabled {
  opacity: 0.5;
  cursor: default;
}
.oauth2__bind-btn { margin-left: auto; }
</style>
