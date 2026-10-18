<!--
  JWT 认证演示（端口 18083，前缀 /api/jwt）。
  服务端签发 JWT，客户端存 localStorage（key: jwt_token），请求带 Authorization: Bearer。
  JWT 无状态、不可撤销，因此登出仅本地清除 token，服务端无感。
-->
<script setup>
import { ref, computed } from 'vue'
import authJwt from '../../../api/authJwt'
import PageHeader from '../../../components/PageHeader.vue'
import StatusBar from '../../../components/StatusBar.vue'
import FormField from '../../../components/FormField.vue'
import AppButton from '../../../components/AppButton.vue'
import CodeBlock from '../../../components/CodeBlock.vue'

const username = ref('')
const password = ref('')
const token = ref(localStorage.getItem('jwt_token') || '')
const loading = ref(false)
const profile = ref(null)
const responseText = ref('')

const isLoggedIn = computed(() => !!token.value)
const tokenPreview = computed(() => (token.value ? token.value.substring(0, 40) + '...' : ''))

async function login () {
  loading.value = true
  try {
    const resp = await authJwt.login({ username: username.value, password: password.value })
    responseText.value = JSON.stringify(resp.data, null, 2)
    if (resp.ok && resp.data && resp.data.token) {
      token.value = resp.data.token
      localStorage.setItem('jwt_token', token.value)
      await getProfile()
    }
  } catch (e) { responseText.value = '错误: ' + e.message } finally { loading.value = false }
}

async function callPublic () {
  try {
    const resp = await authJwt.publicHello()
    responseText.value = JSON.stringify(resp.data, null, 2)
  } catch (e) { responseText.value = '错误: ' + e.message }
}

async function getProfile () {
  try {
    const resp = await authJwt.profile(token.value)
    responseText.value = 'HTTP ' + resp.status + '\n' + JSON.stringify(resp.data, null, 2)
    if (resp.ok) profile.value = resp.data
  } catch (e) { responseText.value = '错误: ' + e.message }
}

function logout () {
  token.value = ''
  profile.value = null
  localStorage.removeItem('jwt_token')
  responseText.value = '已登出（JWT 无法撤销，过期前仍有效）'
}
</script>

<template>
  <div class="demo">
    <PageHeader title="🎫 JWT 认证" subtitle="无状态 JWT Token 认证演示"/>
    <div class="demo__main">
      <p class="demo__desc">无状态认证：服务端签发 JWT → 客户端存 localStorage → 每次请求带 Authorization 头。</p>

      <StatusBar :variant="isLoggedIn ? 'success' : 'warning'">
        <template v-if="isLoggedIn">已登录 — {{ profile?.username || username }}</template>
        <template v-else>未登录 — 请先登录</template>
      </StatusBar>

      <div class="demo__form">
        <FormField v-model="username" label="用户名" placeholder="用户名"/>
        <FormField v-model="password" type="password" label="密码" placeholder="密码" @enter="login"/>
      </div>

      <div class="demo__actions">
        <AppButton @click="login" :loading="loading">Login</AppButton>
        <AppButton variant="secondary" @click="callPublic">Public API</AppButton>
        <AppButton variant="secondary" @click="getProfile">Get Profile</AppButton>
        <AppButton variant="danger" @click="logout">Logout</AppButton>
      </div>

      <CodeBlock v-if="tokenPreview" class="demo__token">Token: {{ tokenPreview }}</CodeBlock>

      <div v-if="profile" class="demo__profile">
        <div v-for="(v, k) in profile" :key="k" class="demo__row"><b>{{ k }}:</b> <span>{{ v }}</span></div>
      </div>

      <CodeBlock v-if="responseText">{{ responseText }}</CodeBlock>
    </div>
  </div>
</template>

<style scoped>
.demo__main {
  max-width: var(--container-narrow);
}
.demo__desc {
  font-size: var(--font-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-3);
}
.demo__form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: var(--space-4) 0;
}
.demo__actions {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  margin-bottom: var(--space-4);
}
.demo__token {
  margin-top: var(--space-2);
}
.demo__profile {
  margin: var(--space-3) 0;
  padding: var(--space-3) var(--space-4);
  background: var(--color-surface);
  border: var(--border-weak);
  border-radius: var(--radius);
  font-size: var(--font-sm);
}
.demo__row {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-1) 0;
  border-bottom: var(--border-weak);
}
.demo__row:last-child { border-bottom: none; }
.demo__row b { color: var(--color-text-secondary); white-space: nowrap; }
</style>
