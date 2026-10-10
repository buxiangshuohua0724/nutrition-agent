/**
 * 知识库检索接口
 *
 * 契约依据：`docs/api-contract.md` 4.4
 *   `GET /knowledge/search`（公开）—— 关键词检索已发布知识
 *
 * 响应 `data.list[]` 元素结构：
 *   `{ docId, chunkId, title, heading, snippet, category, score,
 *      source: { name, publisher, version, url, applicableScope } }`
 *
 * 只检索 `publish_status = PUBLISHED`、`review_status = APPROVED` 且未过 `valid_until` 的内容。
 */

import { get } from './client'
import { DEMO_KNOWLEDGE, withDemo } from './demo-data'

/**
 * 关键词检索知识库。
 *
 * 接口：`GET /knowledge/search`（公开）
 * 查询参数：`keyword`（必填）、`page`（默认 1）、`pageSize`（默认 20）、`category`（可选）
 * 响应：分页结构，`list[]` 为知识片段
 * 知识库无相关内容时后端返回 `40403`（前端按空态展示即可）。
 *
 * @param {{keyword: string, page?: number, pageSize?: number, category?: string}} params
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export function searchKnowledge(params = {}, options = {}) {
  const { keyword = '', page = 1, pageSize = 20, category = '' } = params
  return withDemo(
    () => get('/knowledge/search', { keyword, page, pageSize, category }, options),
    () => ({
      list: DEMO_KNOWLEDGE.map((item) => ({ ...item })),
      page,
      pageSize,
      total: DEMO_KNOWLEDGE.length,
      totalPages: 1,
    }),
  )
}
