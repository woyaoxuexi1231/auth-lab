<!--
  OAuth2 本地账号注册页（路由 /auth-lab/register，组件名沿用 LoginPage）。
  进入时先查 /auth/status，已登录则直接回 /auth-lab/oauth2，避免重复注册。
-->
<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import oauth2 from '../../../api/oauth2'
import PageHeader from '../../../components/PageHeader.vue'
import FormField from '../../../components/FormField.vue'
import AppButton from '../../../components/AppButton.vue'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const successMsg = ref('')
const form = reactive({ username: '', password: '', email: '' })

async function checkIfAlreadyLoggedIn () {
  try {
    const data = await oauth2.status()
    if (data.authenticated) router.push('/auth-lab/oauth2')
  } catch (e) { /* 未登录或服务未起，留在注册页 */ }
}

async function register () {
  error.value = ''; successMsg.value = ''
  if (!form.username || !form.password) { error.value = '用户名和密码不能为空'; return }
  loading.value = true
  try {
    const resp = await oauth2.register(form)
    if (resp.ok) {
      successMsg.value = (resp.data && resp.data.message) || '注册成功'
      setTimeout(() => router.push('/auth-lab/oauth2'), 800)
    } else {
      error.value = (resp.data && resp.data.error) || '注册失败'
    }
  } catch (e) { error.value = '网络错误: ' + e.message } finally { loading.value = false }
}

onMounted(checkIfAlreadyLoggedIn)
</script>

<template>
  <div class="login-page">
    <PageHeader title="🔗 OAuth2 三方认证" subtitle="注册新账号"/>
    <main class="login-page__main">
      <h2 class="login-page__section">注册新账号</h2>
      <div class="login-page__form">
        <FormField v-model="form.username" placeholder="用户名"/>
        <FormField v-model="form.password" type="password" placeholder="密码"/>
        <FormField v-model="form.email" type="email" placeholder="邮箱（可选）"/>
      </div>
      <AppButton block :loading="loading" @click="register">{{ loading ? '注册中...' : '注册' }}</AppButton>
      <div v-if="error" class="login-page__error">{{ error }}</div>
      <div v-if="successMsg" class="login-page__ok">{{ successMsg }}</div>
      <p class="login-page__foot">已有账号？<router-link to="/auth-lab/oauth2">去登录</router-link></p>
    </main>
  </div>
</template>

<style scoped>
.login-page__main {
  max-width: var(--container-narrow);
}
.login-page__section {
  font-size: var(--font-md);
  font-weight: var(--weight-regular);
  letter-spacing: var(--tracking-label);
  margin-bottom: var(--space-3);
}
.login-page__form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}
.login-page__error {
  color: var(--color-error);
  font-size: var(--font-sm);
  margin-top: var(--space-2);
}
.login-page__ok {
  color: var(--color-success);
  font-size: var(--font-sm);
  margin-top: var(--space-2);
}
.login-page__foot {
  margin-top: var(--space-4);
  font-size: var(--font-sm);
  color: var(--color-text-muted);
  text-align: center;
}
</style>
