import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    base: env.VITE_APP_BASE,
    server: {
      port: parseInt(env.VITE_DEV_PORT || '13008'),
      proxy: {
        // 无网关：按路径前缀分发到各认证服务直连端口（与后端各服务端口一致）
        '/api/session': { target: 'http://localhost:18082', changeOrigin: true },
        '/api/jwt': { target: 'http://localhost:18083', changeOrigin: true },
        '/api/opaque': { target: 'http://localhost:18088', changeOrigin: true },
        '/api/oauth2-client': { target: 'http://localhost:18087', changeOrigin: true },
        '/api/oauth2-auth': { target: 'http://localhost:18085', changeOrigin: true },
        '/api/oauth2-resource': { target: 'http://localhost:18086', changeOrigin: true },
        // OIDC 元数据端点：resource-server 由 issuer-uri 发现 JWKS 时请求 {issuer}/.well-known/openid-configuration
        '/.well-known': { target: 'http://localhost:18085', changeOrigin: true }
      }
    }
  }
})
