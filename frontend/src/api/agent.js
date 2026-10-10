/**
 * AI Agent 接口
 *
 * 契约依据：`docs/api-contract.md` 4.3
 *   - `POST /agent/chat`：公开（游客有限次），返回 `AgentAnswer`（契约 3.5）
 *   - `GET /agent/sessions`：登录
 *   - `POST /agent/sessions`：公开（游客可）
 *   - `GET /agent/sessions/{id}/messages`：登录/游客
 *   - `DELETE /agent/sessions/{id}`：登录
 *   - `GET /agent/capabilities`：公开
 *
 * 三条硬规则（与契约一致，前端不得违反）：
 *   1. `sources` 由后端工具层注入，前端原样展示，不自己编造出处；
 *   2. `uncertainty` 非空时必须**可见地**展示；
 *   3. 降级（`degraded: true`）时必须提示用户，不能把降级结果当正常结果。
 */

import { del, get, post } from './client'
import { DEMO_AGENT_ANSWER, withDemo } from './demo-data'

/**
 * 提交问题，返回结构化回答。
 *
 * 接口：`POST /agent/chat`（公开，游客有限次）
 * 请求体：`{ sessionId?: number|null, question: string, foodId?: number|null, variantId?: number|null, weight?: number|null }`
 * 响应：`AgentAnswer`（契约 3.5）
 *   `{ answer, intent, foodReferences[], nutritionFacts[], recommendations[],
 *      warnings[], sources[], uncertainty[], toolCalls[], model, latencyMs, degraded }`
 *
 * 限流：登录 30 次/分钟；游客 5 次/天 → 超限返回 `42901`。
 *
 * @param {{sessionId?: number|string|null, question: string, foodId?: number|string|null, variantId?: number|string|null, weight?: number|null}} payload
 * @param {object} [options] 请求选项（signal 用于中断请求）
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export function chat(payload, options = {}) {
  const { sessionId = null, question, foodId = null, variantId = null, weight = null } = payload || {}
  const body = {
    sessionId: sessionId === null ? null : Number(sessionId),
    question,
    foodId: foodId === null ? null : Number(foodId),
    variantId: variantId === null ? null : Number(variantId),
    weight: weight === null || weight === undefined ? null : Number(weight),
  }
  return withDemo(
    () => post('/agent/chat', body, options),
    () => DEMO_AGENT_ANSWER,
  )
}

/**
 * 新建会话。
 *
 * 接口：`POST /agent/sessions`（公开，游客可）
 * 请求体：`{ title?: string }`（可选）
 * 响应：`{ id: number, title: string, createdAt: string }`
 *
 * @param {{title?: string}} [payload]
 * @param {object} [options] 请求选项
 * @returns {Promise<object>} 会话对象（失败时抛出 ApiError）
 */
export function createSession(payload = {}, options = {}) {
  return post('/agent/sessions', payload, options)
}

/**
 * 会话列表（登录）。
 *
 * 接口：`GET /agent/sessions`（登录）
 * 响应：分页结构或数组，元素为 `{ id, title, messageCount, createdAt, updatedAt }`
 *
 * @param {object} [options] 请求选项
 * @returns {Promise<Array<object>>} 会话数组（失败时抛出 ApiError）
 */
export async function getSessions(options = {}) {
  const data = await get('/agent/sessions', undefined, options)
  if (Array.isArray(data)) return data
  if (data && Array.isArray(data.list)) return data.list
  return []
}

/**
 * 会话消息（登录 / 游客）。
 *
 * 接口：`GET /agent/sessions/{id}/messages`
 * 响应：`Message[]`，元素为
 *   `{ id, sessionId, role: 'USER'|'ASSISTANT', content, structuredAnswer?: AgentAnswer, createdAt }`
 *
 * @param {number|string} id 会话 id
 * @param {object} [options] 请求选项
 * @returns {Promise<Array<object>>}
 */
export async function getSessionMessages(id, options = {}) {
  const data = await get(`/agent/sessions/${encodeURIComponent(id)}/messages`, undefined, options)
  if (Array.isArray(data)) return data
  if (data && Array.isArray(data.list)) return data.list
  return []
}

/**
 * 删除会话（登录）。越权访问他人会话由后端返回 `40301`。
 *
 * 接口：`DELETE /agent/sessions/{id}`
 *
 * @param {number|string} id 会话 id
 * @param {object} [options] 请求选项
 * @returns {Promise<unknown>}
 */
export function deleteSession(id, options = {}) {
  return del(`/agent/sessions/${encodeURIComponent(id)}`, options)
}

/**
 * 当前能力与限制（用于前端提示与模型未配置时的降级展示）。
 *
 * 接口：`GET /agent/capabilities`（公开）
 * 响应示例：`{ modelConfigured: boolean, visionConfigured: boolean,
 *              guestChatQuota: number, guestRecognizeQuota: number, limitations: string[] }`
 *
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>}
 */
export function getCapabilities(options = {}) {
  return get('/agent/capabilities', undefined, options)
}
