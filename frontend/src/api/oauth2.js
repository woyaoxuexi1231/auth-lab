/* ============================================================
   api/oauth2.js — OAuth2 客户端（本地账号 + 三方绑定）
   登录/登出走 302 流程：redirect:'manual' + raw，由视图按 resp.ok / resp.data.error 处理。
   弹窗/postMessage 逻辑见视图层。
   ============================================================ */
import { request } from './http'

const clientBase = import.meta.env.VITE_API_CLIENT
const logoutUrl = import.meta.env.VITE_LOGOUT
const authorizationBase = import.meta.env.VITE_OAUTH2_AUTHORIZATION

export const oauth2 = {
  /** 本地账号登录（form-urlencoded，可能 302 → redirect manual + raw） */
  login (form) {
    return request(`${clientBase}/auth/login`, {
      method: 'POST', form: true, body: form, redirect: 'manual', raw: true
    })
  },
  /** 注册新账号（JSON），raw 以便读取后端 error 文案 */
  register (form) {
    return request(`${clientBase}/auth/register`, { method: 'POST', body: form, raw: true })
  },
  /** 当前认证状态 → {authenticated, username} */
  status () {
    return request(`${clientBase}/auth/status`)
  },
  /** 个人资料。manual=true 时用 redirect:'manual'（弹窗内 checkAuthState 用），否则普通请求。 */
  profile (opts = {}) {
    if (opts.manual) return request(`${clientBase}/profile`, { redirect: 'manual', raw: true })
    return request(`${clientBase}/profile`)
  },
  /** 待绑定会话 → {empty, sessionId, provider, providerUsername, email, avatarUrl} */
  oauthPending () {
    return request(`${clientBase}/oauth-pending`)
  },
  bindRegister (form) {
    return request(`${clientBase}/bind/register`, { method: 'POST', body: form, raw: true })
  },
  bindLogin (form) {
    return request(`${clientBase}/bind/login`, { method: 'POST', body: form, raw: true })
  },
  bindCurrent () {
    return request(`${clientBase}/bind/current`, { method: 'POST', raw: true })
  },
  unbind (id) {
    return request(`${clientBase}/bind/${id}`, { method: 'DELETE', raw: true })
  },
  /** OAuth2 登出（可能 302），fire-and-forget */
  logout () {
    return request(logoutUrl, { method: 'POST', redirect: 'manual', raw: true }).catch(() => {})
  },
  /** 第三方授权 URL */
  authorizationUrl (provider) {
    return authorizationBase + provider
  }
}

/** 提供商显示名（中性标签） */
export function providerLabel (p) {
  return ({ 'lab-client': 'Lab Auth', github: 'GitHub', google: 'Google' })[p] || p
}

/** 默认头像（SVG 占位） */
export const defaultAvatar =
  'data:image/svg+xml,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect fill="#ddd" width="100" height="100"/><text x="50" y="65" text-anchor="middle" font-size="40" fill="#999">?</text></svg>'
  )

export default oauth2
