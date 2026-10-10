/**
 * 图片识别接口
 *
 * 契约依据：`docs/api-contract.md` 4.5
 *   - `POST /recognitions`（公开，游客有限次）：上传图片识别，`multipart/form-data`，字段名 `file`
 *   - `POST /recognitions/{id}/confirm`（公开）：确认候选食品
 *   - `GET /recognitions/{id}`（公开）：查询识别记录
 *
 * 约束：
 *   - 允许 `image/jpeg`、`image/png`、`image/webp`；大小 ≤ 10MB；超限返回 `41501` / `41301`
 *   - **`candidates` 不含任何营养数值字段**（契约 3.6 与 README 工程约束 2），
 *     因此候选列表只能展示名称、置信度与封面图，前端不得为候选补算营养数据。
 *   - `status` 可能为 `NEED_MANUAL_SEARCH`，此时必须提供手动搜索入口。
 */

import { get, post, upload } from './client'
import { DEMO_RECOGNITION, DEMO_RECOGNITION_MANUAL, demoClone, withDemo } from './demo-data'

/**
 * 上传图片进行识别。
 *
 * 接口：`POST /recognitions`（公开，游客有限次；限流 登录 20 次/小时、游客 3 次/天）
 * 请求：`multipart/form-data`，字段名 `file`
 * 响应：`ImageRecognitionResult`（契约 3.6）
 *   `{ recognitionId, status, imageUrl, candidates[], confirmedFoodId, notice, failureReason, latencyMs }`
 *   `candidates[]` 元素：`{ foodId, foodName, confidence, reason, coverImageUrl }`（无营养字段）
 *
 * @param {File|Blob} file 图片文件
 * @param {object} [options] 请求选项（signal 用于中断，silent 静默）
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export function recognizeImage(file, options = {}) {
  return withDemo(
    () => upload('/recognitions', file, options),
    // 演示数据提供两组结果（有候选 / 需手动搜索），默认返回带候选的那组；
    // 页面上的"演示数据"标识会明确告知这不是真实识别结果。
    () => DEMO_RECOGNITION,
  )
}

/**
 * 确认识别候选食品。
 *
 * 接口：`POST /recognitions/{id}/confirm`（公开）
 * 请求体：`{ foodId: number }`
 * 响应：更新后的 `ImageRecognitionResult`（`confirmedFoodId` 被写入）
 *
 * @param {number|string} recognitionId 识别记录 id
 * @param {number|string} foodId 用户确认的食品 id
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>} 更新后的识别结果
 */
export function confirmRecognition(recognitionId, foodId, options = {}) {
  return post(`/recognitions/${encodeURIComponent(recognitionId)}/confirm`, { foodId: Number(foodId) }, options)
}

/**
 * 查询识别记录。
 *
 * 接口：`GET /recognitions/{id}`（公开）
 * 响应：`ImageRecognitionResult`
 *
 * @param {number|string} recognitionId 识别记录 id
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export function getRecognition(recognitionId, options = {}) {
  return withDemo(
    () => get(`/recognitions/${encodeURIComponent(recognitionId)}`, undefined, options),
    () => demoClone(DEMO_RECOGNITION, { recognitionId: Number(recognitionId) || DEMO_RECOGNITION.recognitionId }),
  )
}

/**
 * 手动搜索兜底示例（仅演示数据使用）。
 *
 * 当视觉服务返回 `NEED_MANUAL_SEARCH` 时，页面必须提供手动搜索入口；
 * 这里额外导出一份演示结果，便于在开发模式下验证该分支的界面。
 * @returns {object} `ImageRecognitionResult`（`status` 为 `NEED_MANUAL_SEARCH`）
 */
export function demoManualSearchResult() {
  return DEMO_RECOGNITION_MANUAL
}
