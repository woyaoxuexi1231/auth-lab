<!--
  Session 认证演示（端口 18082，前缀 /api/session）。
  登录用 form-urlencoded，raw 取 HTTP 状态与响应原文；
  不做任何 token 持久化 —— 登录态完全由服务端 Redis Session + Cookie 承载。
-->
<script setup>
import { ref } from 'vue'
import authSession from '../../../api/authSession'
import PageHeader from '../../../components/PageHeader.vue'
import StatusBar from '../../../components/StatusBar.vue'
import FormField from '../../../components/FormField.vue'
import AppButton from '../../../components/AppButton.vue'
import CodeBlock from '../../../components/CodeBlock.vue'

const username = ref('')
const password = ref('')
const loading = ref(false)
const loggedIn = ref(false)
const profile = ref(null)
const responseText = ref('')

async function login () {
  loading.value = true
  try {
    const resp = await authSession.login({ username: username.value, password: password.value }, true)
    responseText.value = 'HTTP ' + resp.status + '\n' + (resp.text || '')
    if (resp.ok) { loggedIn.value = true; await getProfile() }
  } catch (e) { responseText.value = '错误: ' + e.message } finally { loading.value = false }
}

async function callPublic () {
  try {
    const resp = await authSession.publicHello()
    responseText.value = JSON.stringify(resp.data, null, 2)
  } catch (e) { responseText.value = '错误: ' + e.message }
}

async function getProfile () {
  try {
    const resp = await authSession.profile()
    responseText.value = 'HTTP ' + resp.status + '\n' + JSON.stringify(resp.data, null, 2)
    if (resp.ok) { profile.value = resp.data; loggedIn.value = true }
  } catch (e) { responseText.value = '错误: ' + e.message }
}

async function logout () {
  try {
    await authSession.logout()
    loggedIn.value = false
    profile.value = null
    responseText.value = '已登出'
  } catch (e) { responseText.value = '错误: ' + e.message }
}
</script>

<template>
  <div class="demo">
    <PageHeader title="🍪 Session 认证" subtitle="Cookie + Redis Session 认证演示"/>
    <div class="demo__main">
      <p class="demo__desc">最经典的认证方式：服务端创建 Session → Cookie 返回 Session ID → 浏览器自动携带。</p>

      <StatusBar :variant="loggedIn ? 'success' : 'warning'">
        <template v-if="loggedIn">已登录 — {{ profile?.username || username }}</template>
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
