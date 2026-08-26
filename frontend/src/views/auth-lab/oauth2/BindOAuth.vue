<!-- OAuth2 绑定页：注册/登录 tabs；成功后 opener postMessage 通知主窗口。
     fetch 换 api/oauth2.js；tabs → SegmentedNav；样式重排。 -->
<script>
import oauth2, { providerLabel as providerLabelOf, defaultAvatar } from '../../../api/oauth2'
import PageHeader from '../../../components/PageHeader.vue'
import AppCard from '../../../components/AppCard.vue'
import AppButton from '../../../components/AppButton.vue'
import FormField from '../../../components/FormField.vue'
import SegmentedNav from '../../../components/SegmentedNav.vue'

export default {
  name: 'BindOAuth',
  components: { PageHeader, AppCard, AppButton, FormField, SegmentedNav },
  data () {
    return {
      loading: false, tab: 'register', noSession: false, error: '', successMsg: '', alreadyLoggedIn: '',
      tabs: [{ value: 'register', label: '注册新账号' }, { value: 'login', label: '已有账号' }],
      defaultAvatar,
      pending: { provider: '', providerUsername: '', email: '', avatarUrl: '' },
      regForm: { username: '', password: '', email: '' },
      loginForm: { username: '', password: '' }
    }
  },
  computed: {
    providerLabel () { return providerLabelOf(this.pending.provider) }
  },
  async mounted () {
    try { const s = await oauth2.status(); if (s.authenticated) this.alreadyLoggedIn = s.username } catch (e) { /* */ }
    try {
      const r = await oauth2.oauthPending()
      if (r.empty) { this.noSession = true } else {
        this.pending.provider = r.provider || ''
        this.pending.providerUsername = r.providerUsername || ''
        this.pending.email = r.email || ''
        this.pending.avatarUrl = r.avatarUrl || ''
        this.regForm.email = r.email || ''
      }
    } catch (e) { this.error = '加载失败: ' + e.message }
  },
  methods: {
    notifyOpener (payload) {
      if (window.opener && !window.opener.closed) {
        window.opener.postMessage({ type: 'oauth2-login-result', ...payload }, window.location.origin)
      }
    },
    async directBind () {
      this.error = ''; this.loading = true
      try {
        const resp = await oauth2.bindCurrent()
        if (resp.ok) {
          this.successMsg = '绑定成功！窗口即将自动关闭...'
          this.notifyOpener({ status: 'success' })
          setTimeout(() => { if (window.opener) window.close() }, 1500)
        } else { this.error = (resp.data && resp.data.error) || '绑定失败' }
      } catch (e) { this.error = '网络错误: ' + e.message } finally { this.loading = false }
    },
    async registerAndBind () {
      this.error = ''
      if (!this.regForm.username || !this.regForm.password) { this.error = '用户名和密码不能为空'; return }
      this.loading = true
      try {
        const resp = await oauth2.bindRegister(this.regForm)
        if (resp.ok) {
          this.successMsg = '注册并绑定成功！窗口即将自动关闭...'
          this.notifyOpener({ status: 'success' })
          setTimeout(() => { if (window.opener) window.close() }, 1500)
        } else { this.error = (resp.data && resp.data.error) || '注册失败' }
      } catch (e) { this.error = '网络错误: ' + e.message } finally { this.loading = false }
    },
    async loginAndBind () {
      this.error = ''
      if (!this.loginForm.username || !this.loginForm.password) { this.error = '用户名和密码不能为空'; return }
      this.loading = true
      try {
        const resp = await oauth2.bindLogin(this.loginForm)
        if (resp.ok) {
          this.successMsg = '登录并绑定成功！窗口即将自动关闭...'
          this.notifyOpener({ status: 'success' })
          setTimeout(() => { if (window.opener) window.close() }, 1500)
        } else { this.error = (resp.data && resp.data.error) || '绑定失败' }
      } catch (e) { this.error = '网络错误: ' + e.message } finally { this.loading = false }
    }
  }
}
</script>

<template>
  <div class="bind-oauth">
    <PageHeader title="🔗 OAuth2 三方认证" subtitle="绑定第三方账号"/>
    <main class="bind-oauth__main">
      <div v-if="noSession" class="bind-oauth__expired">
        <p>绑定会话已过期，请重新通过第三方登录。</p>
        <router-link to="/auth-lab/oauth2">去登录</router-link>
      </div>
      <div v-else>
        <h2 class="bind-oauth__section">绑定第三方账号</h2>
        <AppCard class="bind-oauth__pending">
          <img :src="pending.avatarUrl || defaultAvatar" class="bind-oauth__avatar" alt=""/>
          <div>
            <span class="bind-oauth__chip">{{ providerLabel }}</span> {{ pending.providerUsername || '未知用户' }}
            <span v-if="pending.email" class="bind-oauth__muted"> · {{ pending.email }}</span>
          </div>
        </AppCard>

        <div v-if="alreadyLoggedIn" class="bind-oauth__loggedin">
          <p>当前已登录：<strong>{{ alreadyLoggedIn }}</strong></p>
          <AppButton @click="directBind" :loading="loading">{{ loading ? '绑定中...' : '绑定到当前账号' }}</AppButton>
        </div>

        <template v-if="!alreadyLoggedIn">
          <SegmentedNav :items="tabs" :model-value="tab" @change="tab = $event"/>
          <div v-if="tab === 'register'" class="bind-oauth__form">
            <FormField v-model="regForm.username" placeholder="用户名"/>
            <FormField v-model="regForm.password" type="password" placeholder="密码"/>
            <FormField v-model="regForm.email" type="email" :placeholder="pending.email || '邮箱（可选）'"/>
            <AppButton block :loading="loading" @click="registerAndBind">注册并绑定</AppButton>
          </div>
          <div v-else class="bind-oauth__form">
            <FormField v-model="loginForm.username" placeholder="已有账号的用户名"/>
            <FormField v-model="loginForm.password" type="password" placeholder="已有账号的密码"/>
            <AppButton block :loading="loading" @click="loginAndBind">登录并绑定</AppButton>
          </div>
        </template>

        <div v-if="error" class="bind-oauth__error">{{ error }}</div>
        <div v-if="successMsg" class="bind-oauth__ok">{{ successMsg }}</div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.bind-oauth__main {
  max-width: var(--container-narrow);
}
.bind-oauth__expired {
  text-align: center;
  padding: var(--space-8) 0;
  color: var(--color-text-muted);
}
.bind-oauth__expired p { margin-bottom: var(--space-2); }
.bind-oauth__section {
  font-size: var(--font-md);
  font-weight: var(--weight-regular);
  letter-spacing: var(--tracking-label);
  margin-bottom: var(--space-3);
}
.bind-oauth__pending {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-sm);
}
.bind-oauth__avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-border-weak);
  flex-shrink: 0;
}
.bind-oauth__chip {
  display: inline-block;
  padding: 1px var(--space-2);
  border: var(--border);
  border-radius: var(--radius-sm);
  font-size: var(--font-xs);
  color: var(--color-text-muted);
  background: var(--color-surface);
  margin-right: var(--space-1);
}
.bind-oauth__muted { color: var(--color-text-muted); }
.bind-oauth__loggedin {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  font-size: var(--font-base);
}
.bind-oauth__form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-top: var(--space-4);
}
.bind-oauth__error {
  color: var(--color-error);
  font-size: var(--font-sm);
  text-align: center;
  margin-top: var(--space-3);
}
.bind-oauth__ok {
  color: var(--color-success);
  font-size: var(--font-base);
  text-align: center;
  margin-top: var(--space-3);
}
</style>
