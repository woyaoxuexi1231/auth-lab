/* ============================================================
   api/http.js — 统一 fetch 封装
   credentials:'include'；JSON/form 自动处理；非 2xx 抛 ApiError。
   raw:true 时不抛错，返回 { status, ok, text, data }（session login、
   OAuth2 302 流程、需读取后端错误文案的场景用）。
   ============================================================ */

export class ApiError extends Error {
  constructor (status, message, data) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.data = data
  }
}

function parseText (text) {
  if (!text) return null
  try { return JSON.parse(text) } catch { return text }
}

/**
 * @param {string} path 请求路径（含 base 前缀）
 * @param {object} [opts]
 * @param {string} [opts.method='GET']
 * @param {object|URLSearchParams|string} [opts.body]
 * @param {object} [opts.headers]
 * @param {boolean} [opts.form] 以 application/x-www-form-urlencoded 提交（body 为普通对象时）
 * @param {string} [opts.redirect] 透传 fetch（'manual' 用于 OAuth2 302 流程）
 * @param {boolean} [opts.raw] 返回 { status, ok, text, data }，不抛错
 */
export async function request (path, { method = 'GET', body, headers = {}, form, redirect, raw } = {}) {
  const opts = { method, headers: { ...headers }, credentials: 'include' }
  if (redirect) opts.redirect = redirect

  if (body != null) {
    if (form) {
      opts.headers['Content-Type'] = 'application/x-www-form-urlencoded'
      opts.body = body instanceof URLSearchParams ? body : new URLSearchParams(body)
    } else if (typeof body === 'string' || body instanceof FormData || body instanceof URLSearchParams) {
      opts.body = body
    } else {
      opts.headers['Content-Type'] = 'application/json'
      opts.body = JSON.stringify(body)
    }
  }

  let resp
  try {
    resp = await fetch(path, opts)
  } catch (e) {
    throw new ApiError(0, '网络错误: ' + e.message)
  }

  if (raw) {
    let text = ''
    try { text = await resp.text() } catch { /* opaqueredirect 读取 body 会抛错 */ }
    return { status: resp.status, ok: resp.ok, text, data: parseText(text) }
  }

  let text = ''
  try { text = await resp.text() } catch { text = '' }
  const data = parseText(text)

  if (!resp.ok) {
    const detail = data && typeof data === 'object' ? data.detail : null
    const message =
      (data && typeof data === 'object' && data.error) ||
      (typeof detail === 'string' && detail) ||
      (typeof data === 'string' && data) ||
      `请求失败 (HTTP ${resp.status})`
    throw new ApiError(resp.status, message, data)
  }
  return data
}
