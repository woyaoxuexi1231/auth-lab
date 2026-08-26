/* ============================================================
   api/authJwt.js — 无状态 JWT 认证
   演示页需展示 HTTP 状态，故统一返回 raw {status, ok, text, data}。
   token 由调用方存 localStorage（key: jwt_token）。
   ============================================================ */
import { request } from './http'

const base = import.meta.env.VITE_API_JWT

export const authJwt = {
  /** JSON 登录 → 响应体含 {token} */
  login (credentials) {
    return request(`${base}/auth/login`, { method: 'POST', body: credentials, raw: true })
  },
  publicHello () {
    return request(`${base}/public/hello`, { raw: true })
  },
  profile (token) {
    return request(`${base}/profile`, { headers: { Authorization: `Bearer ${token}` }, raw: true })
  },
  /** JWT 无法撤销，仅由视图清除本地 token */
  logout () {
    return Promise.resolve({ status: 0, ok: true, text: '', data: null })
  }
}

export default authJwt
