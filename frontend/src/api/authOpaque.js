/* ============================================================
   api/authOpaque.js — 不透明令牌（Redis 查证）认证
   演示页需展示 HTTP 状态，故统一返回 raw {status, ok, text, data}。
   token 由调用方存 localStorage（key: opaque_token）。
   ============================================================ */
import { request } from './http'

const base = import.meta.env.VITE_API_OPAQUE

export const authOpaque = {
  /** JSON 登录 → 响应体含 {token} */
  login (credentials) {
    return request(`${base}/auth/login`, { method: 'POST', body: credentials, raw: true })
  },
  logout (token) {
    return request(`${base}/auth/logout`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, raw: true })
  },
  publicHello () {
    return request(`${base}/public/hello`, { raw: true })
  },
  profile (token) {
    return request(`${base}/profile`, { headers: { Authorization: `Bearer ${token}` }, raw: true })
  }
}

export default authOpaque
