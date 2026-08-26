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
        '/api': {
          target: 'http://localhost:18090',
          changeOrigin: true
        }
      }
    }
  }
})
