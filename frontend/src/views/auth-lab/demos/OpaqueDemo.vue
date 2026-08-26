<!-- Opaque Token 认证演示：不透明令牌（Redis 查证）。
     token 存 localStorage（key: opaque_token），请求带 Authorization: Bearer；
     服务端可控，登出时删 Redis key 即撤销。 -->
<script>
import authOpaque from '../../../api/authOpaque'
import PageHeader from '../../../components/PageHeader.vue'
import StatusBar from '../../../components/StatusBar.vue'
import FormField from '../../../components/FormField.vue'
import AppButton from '../../../components/AppButton.vue'
import CodeBlock from '../../../components/CodeBlock.vue'

export default {
  name: 'OpaqueDemo',
  components: { PageHeader, StatusBar, FormField, AppButton, CodeBlock },
  data () {
    return {
      username: '',
      password: '',
      token: localStorage.getItem('opaque_token') || '',
      loading: false,
      profile: null,
      responseText: ''
    }
  },
  computed: {
    isLoggedIn () { return !!this.token }
  },
  methods: {
    async login () {
      this.loading = true
      try {
        const resp = await authOpaque.login({ username: this.username, password: this.password })
        this.responseText = JSON.stringify(resp.data, null, 2)
        if (resp.ok && resp.data && resp.data.token) {
          this.token = resp.data.token
          localStorage.setItem('opaque_token', this.token)
          await this.getProfile()
        }
      } catch (e) { this.responseText = '错误: ' + e.message } finally { this.loading = false }
    },
    async callPublic () {
      try {
        const resp = await authOpaque.publicHello()
        this.responseText = JSON.stringify(resp.data, null, 2)
      } catch (e) { this.responseText = '错误: ' + e.message }
    },
    async getProfile () {
      try {
        const resp = await authOpaque.profile(this.token)
        this.responseText = 'HTTP ' + resp.status + '\n' + JSON.stringify(resp.data, null, 2)
        if (resp.ok) this.profile = resp.data
      } catch (e) { this.responseText = '错误: ' + e.message }
    },
    async logout () {
      try {
        const resp = await authOpaque.logout(this.token)
        this.token = ''
        this.profile = null
        localStorage.removeItem('opaque_token')
        this.responseText = resp.ok ? '已登出，Token 已从 Redis 删除' : '登出失败'
      } catch (e) { this.responseText = '错误: ' + e.message }
    }
  }
}
</script>

<template>
  <div class="demo">
    <PageHeader title="🔏 Opaque Token 认证" subtitle="不透明令牌（Redis 查证）认证演示"/>
    <div class="demo__main">
      <p class="demo__desc">服务端生成随机 Token → Redis 存映射 → 每次请求查 Redis 验证。国内大厂最常用。</p>
      <p class="demo__note">vs JWT：Opaque Token 可随时撤销（删 Redis key），但需查 Redis；JWT 本地验签更快但无法撤销。</p>

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

      <CodeBlock v-if="token" class="demo__token">Token: {{ token }}</CodeBlock>

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
.demo__note {
  font-size: var(--font-xs);
  color: var(--color-text-muted);
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
