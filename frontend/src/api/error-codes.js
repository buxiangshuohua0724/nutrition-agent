/**
 * 错误码表 —— 严格对应 `docs/api-contract.md` 第二章
 *
 * 契约共 21 条记录（含 code = 0 成功），因此这里除成功码外有 20 个错误码。
 * 每条都给出面向用户的中文提示，前端任何地方都不应再自己拼接错误文案。
 */

/** 成功 */
export const CODE_SUCCESS = 0

/** HTTP 状态码 → 兜底提示（响应体没有可解析的 code 时使用） */
export const HTTP_FALLBACK_MESSAGE = {
  400: '请求参数有误',
  401: '登录状态已失效，请重新登录',
  403: '没有访问该资源的权限',
  404: '请求的资源不存在',
  409: '操作与当前数据状态冲突',
  413: '上传内容过大',
  415: '上传格式不受支持',
  429: '调用过于频繁，请稍后再试',
  500: '服务器内部错误，请稍后重试',
  502: '网关错误，请稍后重试',
  503: '服务暂时不可用，请稍后重试',
  504: '服务响应超时，请稍后重试',
}

/**
 * 21 条错误码（含成功码 0）+ 语义标记。
 * `action` 用于客户端行为分流：
 *   - `logout`：清除令牌并跳转登录
 *   - `rateLimit`：限流提示
 */
export const ERROR_CODE_MAP = {
  // ---- 成功 ----
  0: { http: 200, message: 'ok', action: null },

  // ---- 4xx 客户端 ----
  40001: { http: 400, message: '请求参数校验失败，请检查填写内容' },
  40002: { http: 400, message: '重量或份量输入不合法，请填写大于 0 的数值' },
  40101: { http: 401, message: '未登录或登录已过期，请重新登录', action: 'logout' },
  40301: { http: 403, message: '没有权限执行该操作' },
  40401: { http: 404, message: '未找到该食品' },
  40402: { http: 404, message: '该食品暂无营养数据' },
  40403: { http: 404, message: '知识库中没有相关内容' },
  40404: { http: 404, message: '会话或消息不存在' },
  40901: { http: 409, message: '操作冲突，可能是重复收藏或别名已存在' },
  41301: { http: 413, message: '图片文件过大，请上传不超过 10MB 的图片' },
  41501: { http: 415, message: '图片格式不支持，请上传 JPEG / PNG / WebP 图片' },
  42901: { http: 429, message: '调用过于频繁，请稍后再试', action: 'rateLimit' },

  // ---- 5xx 服务端 ----
  50001: { http: 500, message: '服务器内部错误，请稍后重试' },
  50301: { http: 503, message: 'AI 模型服务响应超时，请稍后重试' },
  50302: { http: 503, message: 'AI 返回内容格式异常，请重新提问' },
  50303: { http: 503, message: 'AI 工具调用失败，请稍后重试' },
  50304: { http: 503, message: '图片识别服务暂时不可用，请稍后重试' },
  50305: { http: 503, message: '该问题超出系统能力范围，请换一种问法或咨询专业人士' },
  50306: { http: 503, message: '该问题涉及高风险健康场景，请咨询医生等专业人士' },
}

/**
 * 取错误码对应的中文提示。
 * @param {number|string|null|undefined} code 业务错误码
 * @returns {string|null} 契约内提示；未知错误码返回 null，由调用方决定兜底文案
 */
export function messageOfCode(code) {
  if (code === null || code === undefined) {
    return null
  }
  const entry = ERROR_CODE_MAP[Number(code)]
  return entry ? entry.message : null
}

/**
 * 取错误码在契约中对应的 HTTP 状态码（仅用于日志/调试展示）。
 * @param {number|string} code 业务错误码
 * @returns {number|null}
 */
export function httpStatusOfCode(code) {
  const entry = ERROR_CODE_MAP[Number(code)]
  return entry ? entry.http : null
}

/**
 * 该错误码是否要求清除登录态并跳转登录页。
 * @param {number|string} code 业务错误码
 * @returns {boolean}
 */
export function shouldLogout(code) {
  return Number(code) === 40101
}

/**
 * 该错误码是否表示限流。
 * @param {number|string} code 业务错误码
 * @returns {boolean}
 */
export function isRateLimited(code) {
  return Number(code) === 42901
}
