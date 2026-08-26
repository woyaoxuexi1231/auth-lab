import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/auth-lab/session' },
  {
    path: '/auth-lab',
    component: () => import('../views/auth-lab/AuthLabLayout.vue'),
    redirect: '/auth-lab/session',
    children: [
      { path: 'session', component: () => import('../views/auth-lab/demos/SessionDemo.vue') },
      { path: 'jwt', component: () => import('../views/auth-lab/demos/JwtDemo.vue') },
      { path: 'opaque', component: () => import('../views/auth-lab/demos/OpaqueDemo.vue') },
      { path: 'oauth2', alias: ['/oauth2', '/login'], component: () => import('../views/auth-lab/oauth2/OAuth2Auth.vue') },
      { path: 'register', component: () => import('../views/auth-lab/oauth2/LoginPage.vue') },
      { path: 'bind', alias: '/bind', component: () => import('../views/auth-lab/oauth2/BindOAuth.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(import.meta.env.VITE_APP_BASE),
  routes
})

export default router
