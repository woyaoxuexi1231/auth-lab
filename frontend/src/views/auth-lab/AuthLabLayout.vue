<!-- 认证实验室二级菜单布局：四个 Demo 的切换导航 + 子路由出口。
     每个 Demo 页都带这个导航，避免进入某个 Demo 后无法切换。
     register/bind 属于 OAuth2 流程，导航高亮落在 OAuth2 Demo。 -->
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SegmentedNav from '../../components/SegmentedNav.vue'

const route = useRoute()
const router = useRouter()

const demoItems = [
  { value: '/auth-lab/session', label: '🍪 Session' },
  { value: '/auth-lab/jwt', label: '🎫 JWT' },
  { value: '/auth-lab/opaque', label: '🔏 Opaque' },
  { value: '/auth-lab/oauth2', label: '🔗 OAuth2' }
]

const oauth2FlowPaths = ['/auth-lab/oauth2', '/auth-lab/register', '/auth-lab/bind']

const activeDemo = computed(() => {
  if (oauth2FlowPaths.includes(route.path)) return '/auth-lab/oauth2'
  return demoItems.some(i => i.value === route.path) ? route.path : demoItems[0].value
})

function goDemo (path) { router.push(path) }
</script>

<template>
  <div class="authlab">
    <SegmentedNav
      :items="demoItems"
      :model-value="activeDemo"
      class="authlab__nav"
      @change="goDemo"
    />
    <router-view/>
  </div>
</template>

<style scoped>
.authlab__nav {
  margin: var(--space-6) 0;
}
</style>
