<!-- JWT 认证演示：无状态 Token。
     token 存 localStorage（key: jwt_token），请求带 Authorization: Bearer；
     JWT 无法撤销，登出仅本地清除。 -->
<script>
import authJwt from '../../../api/authJwt'
import PageHeader from '../../../components/PageHeader.vue'
import StatusBar from '../../../components/StatusBar.vue'
import FormField from '../../../components/FormField.vue'
import AppButton from '../../../components/AppButton.vue'
import CodeBlock from '../../../components/CodeBlock.vue'

export default {
  name: 'JwtDemo',
  components: { PageHeader, StatusBar, FormField, AppButton, CodeBlock },
  data () {
    return {
      username: '',
      password: '',
      token: localStorage.getItem('jwt_token') || '',
      loading: false,
      profile: null,
      responseText: ''
    }
  },
  computed: {
    isLoggedIn () { return !!this.token },
    tokenPreview () { return this.token ? this.token.substring(0, 40) + '...' : '' }
  },
  methods: {
    async login () {
      this.loading = true
      try {
        const resp = await authJwt.login({ username: this.username, password: this.password })
        this.responseText = JSON.stringify(resp.data, null, 2)
        if (resp.ok && resp.data && resp.data.token) {
          this.token = resp.data.token
          localStorage.setItem('jwt_token', this.token)
          await this.getProfile()
        }
      } catch (e) { this.responseText = '错误: ' + e.message } finally { this.loading = false }
    },
    async callPublic () {
      try {
        const resp = await authJwt.publicHello()
        this.responseText = JSON.stringify(resp.data, null, 2)
      } catch (e) { this.responseText = '错误: ' + e.message }
    },
    async getProfile () {
      try {
        const resp = await authJwt.profile(this.token)
        this.responseText = 'HTTP ' + resp.status + '\n' + JSON.stringify(resp.data, null, 2)
        if (resp.ok) this.profile = resp.data
      } catch (e) { this.responseText = '错误: ' + e.message }
    },
    logout () {
      this.token = ''
      this.profile = null
      localStorage.removeItem('jwt_token')
      this.responseText = '已登出（JWT 无法撤销，过期前仍有效）'
    }
  }
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
