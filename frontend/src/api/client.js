/**
 * HTTP 客户端 —— 原生 fetch 封装（不引入 axios）
 *
 * 契约依据：`docs/api-contract.md`
 *   - 基础路径 `/api/v1`，可用 `VITE_API_BASE` 覆盖
 *   - 统一响应 `{ code, message, data, traceId }`，`code !== 0` 一律视为失败
 *   - 鉴权头 `Authorization: Bearer <token>`
 *   - 业务判断以 `code` 为准，不依赖 HTTP 状态码
 *
 * 设计要点：
 *   1. 本模块不直接 import Pinia store，避免循环依赖；通过
 *      `configureApiClient({ getToken, onUnauthorized })` 由应用启动时注入。
 *   2. 错误统一抛 `ApiError`，其 `message` 已按错误码表翻译为中文用户提示。
 *   3. 401 / 40101 触发清令牌 + 跳登录；429 / 42901 提示"调用过于频繁"。
 */

import {
  CODE_SUCCESS,
  HTTP_FALLBACK_MESSAGE,
  isRateLimited,
  messageOfCode,
  shouldLogout,
} from './error-codes'

/** 接口基础路径 */
export const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'

/** localStorage 中令牌的键名（与 stores/auth.js 保持一致） */
export const TOKEN_STORAGE_KEY = 'na_token'

/** 统一响应中代表成功的业务码 */
export { CODE_SUCCESS }

/**
 * 接口错误对象。
 * @property {number|null} code 业务错误码（网络层失败时为 null）
 * @property {number|null} httpStatus HTTP 状态码
 * @property {string} traceId 后端追踪号，便于排查
 * @property {unknown} data 后端返回的 data 字段（失败时通常为 null）
 * @property {boolean} isNetworkError 是否为网络层失败（未拿到响应）
 */
export class ApiError extends Error {
  constructor({ code, message, traceId = '', httpStatus = null, data = null, isNetworkError = false }) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
    this.httpStatus = httpStatus
    this.data = data
    this.isNetworkError = isNetworkError
  }
}

/** 客户端运行时依赖，由应用启动时注入 */
const runtime = {
  /** @type {() => string|null} 读取当前令牌 */
  getToken: () => {
    try {
      return localStorage.getItem(TOKEN_STORAGE_KEY)
    } catch {
      return null
    }
  },
  /** @type {() => void} 登录态失效时的处理（清令牌 + 跳登录） */
  onUnauthorized: () => {},
  /** @type {(payload: {type: string, message: string}) => void} 全局提示 */
  notify: () => {},
}

/** 注入/覆盖客户端依赖（getToken / onUnauthorized / notify） */
export function configureApiClient(options = {}) {
  if (typeof options.getToken === 'function') runtime.getToken = options.getToken
  if (typeof options.onUnauthorized === 'function') runtime.onUnauthorized = options.onUnauthorized
  if (typeof options.notify === 'function') runtime.notify = options.notify
}

/** 拼接完整请求地址：baseURL 已含 `/api/v1`，因此直接字符串拼接 */
function buildUrl(path, query) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const url = `${API_BASE}${normalizedPath}`
  if (!query) return url
  const search = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    search.append(key, String(value))
  })
  const qs = search.toString()
  return qs ? `${url}?${qs}` : url
}

/** 组装请求头：JSON 请求带 Content-Type；有令牌时带 Bearer */
function buildHeaders(options = {}, hasJsonBody = false) {
  const headers = { Accept: 'application/json', ...(options.headers || {}) }
  if (hasJsonBody && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json;charset=utf-8'
  }
  const token = runtime.getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

/** 处理登录态失效：清令牌 + 跳登录 + 提示 */
function handleUnauthorized(message, traceId) {
  runtime.notify({ type: 'error', message })
  runtime.onUnauthorized({ message, traceId })
}

/** 把非 JSON 的响应体安全转成对象 */
async function parseBody(response) {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return { __raw: text }
  }
}

/**
 * 核心请求方法。
 * @param {string} path 形如 `/foods/search` 的路径（不含 /api/v1 前缀）
 * @param {object} [options]
 * @param {string} [options.method] HTTP 方法，默认 GET
 * @param {object} [options.query] 查询参数对象
 * @param {unknown} [options.body] JSON 请求体
 * @param {FormData} [options.formData] 表单请求体（上传用，与 body 互斥）
 * @param {object} [options.headers] 额外请求头
 * @param {AbortSignal} [options.signal] 中断信号
 * @param {boolean} [options.silent] 失败时是否跳过全局提示
 * @returns {Promise<unknown>} 成功时 resolve 统一响应中的 `data`
 */
export async function request(path, options = {}) {
  const { method = 'GET', query, body, formData, headers: extraHeaders, signal, silent = false } = options
  const url = buildUrl(path, query)
  const hasJsonBody = body !== undefined && body !== null

  let response
  try {
    response = await fetch(url, {
      method,
      headers: buildHeaders({ headers: extraHeaders }, hasJsonBody),
      body: formData !== undefined && formData !== null ? formData : hasJsonBody ? JSON.stringify(body) : undefined,
      signal,
      credentials: 'same-origin',
    })
  } catch (error) {
    // AbortError 交由调用方处理，不要吞掉
    if (error && error.name === 'AbortError') throw error
    const apiError = new ApiError({
      code: null,
      message: '网络请求失败，请检查网络或后端服务是否已启动',
      data: null,
      isNetworkError: true,
    })
    if (!silent) runtime.notify({ type: 'error', message: apiError.message })
    throw apiError
  }

  const payload = await parseBody(response)
  const httpStatus = response.status

  // 响应体不是统一结构（例如网关直接返回 HTML）
  if (!payload || typeof payload !== 'object' || payload.code === undefined) {
    const message =
      HTTP_FALLBACK_MESSAGE[httpStatus] ||
      (payload && payload.__raw ? '服务返回了非预期的内容，请稍后重试' : '服务返回内容为空')
    const apiError = new ApiError({ code: null, message, httpStatus, data: null })
    if (!silent) runtime.notify({ type: 'error', message })
    throw apiError
  }

  const { code, message, data, traceId } = payload

  if (Number(code) === CODE_SUCCESS) {
    return data
  }

  // code !== 0：按错误码表翻译提示；表里没有时退回后端 message 或 HTTP 兜底文案
  const mappedMessage = messageOfCode(code)
  const finalMessage = mappedMessage || message || HTTP_FALLBACK_MESSAGE[httpStatus] || '请求失败，请稍后重试'
  const apiError = new ApiError({ code, message: finalMessage, traceId, httpStatus, data })

  if (!silent) {
    if (shouldLogout(code)) {
      handleUnauthorized(finalMessage, traceId)
    } else if (isRateLimited(code)) {
      runtime.notify({ type: 'warning', message: '调用过于频繁，请稍后再试' })
    } else {
      runtime.notify({ type: 'error', message: finalMessage })
    }
  } else if (shouldLogout(code)) {
    // 静默模式也必须处理登录态失效，否则用户会一直看到 401
    handleUnauthorized(finalMessage, traceId)
  }

  throw apiError
}

/**
 * GET 请求。
 * @param {string} path 接口路径
 * @param {object} [query] 查询参数
 * @param {object} [options] 其它选项（silent / signal 等）
 */
export function get(path, query, options = {}) {
  return request(path, { ...options, method: 'GET', query })
}

/**
 * POST 请求。
 * @param {string} path 接口路径
 * @param {unknown} [body] JSON 请求体
 * @param {object} [options] 其它选项
 */
export function post(path, body, options = {}) {
  return request(path, { ...options, method: 'POST', body })
}

/**
 * PUT 请求。
 * @param {string} path 接口路径
 * @param {unknown} [body] JSON 请求体
 * @param {object} [options] 其它选项
 */
export function put(path, body, options = {}) {
  return request(path, { ...options, method: 'PUT', body })
}

/**
 * DELETE 请求。
 * @param {string} path 接口路径
 * @param {object} [options] 其它选项
 */
export function del(path, options = {}) {
  return request(path, { ...options, method: 'DELETE' })
}

/**
 * 文件上传（multipart/form-data）。
 * 契约：`POST /recognitions`、`POST /admin/foods/{id}/images` 等，字段名统一为 `file`。
 * @param {string} path 接口路径
 * @param {File|Blob} file 文件对象
 * @param {object} [options]
 * @param {string} [options.fieldName='file'] 表单字段名
 * @param {Record<string, string>} [options.fields] 附加表单字段
 * @param {object} [options.extra] 其它请求选项（signal / silent）
 * @returns {Promise<unknown>} 统一响应中的 `data`
 */
export function upload(path, file, options = {}) {
  const { fieldName = 'file', fields = {}, ...extra } = options
  const formData = new FormData()
  formData.append(fieldName, file)
  Object.entries(fields).forEach(([key, value]) => {
    if (value !== undefined && value !== null) formData.append(key, String(value))
  })
  // 不要手动设置 Content-Type，浏览器需要自动补上 boundary
  return request(path, { ...extra, method: 'POST', formData })
}

/**
 * 组装静态文件绝对地址（`/files/**` 为公开接口）。
 * @param {string|null|undefined} url 后端返回的相对地址
 * @returns {string|null} 可直接用于 <img src> 的地址；无地址时返回 null
 */
export function resolveFileUrl(url) {
  if (!url) return null
  if (/^https?:\/\//i.test(url) || url.startsWith('//')) return url
  // /api/v1/files/... 与 /files/... 都直接返回，交给 vite 代理
  return url.startsWith('/') ? url : `/${url}`
}

export default { get, post, put, del: del, upload, request, ApiError, configureApiClient, resolveFileUrl }
