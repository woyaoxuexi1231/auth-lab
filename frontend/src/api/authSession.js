/* ============================================================
   api/authSession.js — Session 认证（Cookie + Redis）
   演示页需展示 HTTP 状态，故统一返回 raw {status, ok, text, data}。
   ============================================================ */
import { request } from './http'

const base = import.meta.env.VITE_API_SESSION

export const authSession = {
  /** form-urlencoded 登录；raw 时返回 {status,ok,text,data}（session login 展示原文用） */
  login (credentials, raw = true) {
    return request(`${base}/login`, { method: 'POST', form: true, body: credentials, raw })
  },
  publicHello () {
    return request(`${base}/public/hello`, { raw: true })
  },
  profile () {
    return request(`${base}/profile`, { raw: true })
  },
  logout () {
    return request(`${base}/logout`, { method: 'POST', raw: true })
  }
}

export default authSession
