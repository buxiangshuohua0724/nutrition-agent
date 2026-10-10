/**
 * 后台管理接口（统一前缀 `/admin`，需 `CONTENT_ADMIN` 或 `SYS_ADMIN`）
 *
 * 契约依据：`docs/api-contract.md` 4.7
 *   普通用户访问必须返回 `40301`（PRD 12.4）——前端只做菜单可见性控制，
 *   **真正的权限判定必须在后端**，前端的路由守卫仅是体验优化。
 *
 * 关键校验：`PUT /admin/variants/{id}/nutrition` 若缺少 `sourceId` 必须 `40001` 拒绝；
 *   这是"来源可追溯率 100%"在代码层的强制点，因此本模块的方法签名强制要求 sourceId。
 */

import { del, get, post, put, upload } from './client'
import { withEmptyPageFallback } from './demo-data'

/**
 * 后台食品列表（含草稿）。
 *
 * 接口：`GET /admin/foods`（管理角色）
 * 查询参数：`keyword`、`categoryId`、`publishStatus`、`reviewStatus`、`page`、`pageSize`
 * 响应：分页结构，`list[]` 为
 *   `{ id, code, name, categoryId, categoryName, coverImageUrl,
 *      publishStatus, reviewStatus, variantCount, nutrientFilledCount,
 *      nutrientMissingCount, updatedAt }`
 *
 * @param {object} [params] 查询参数
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export async function getAdminFoods(params = {}, options = {}) {
  const { keyword = '', categoryId = null, publishStatus = '', reviewStatus = '', page = 1, pageSize = 20 } = params
  return withEmptyPageFallback(
    () => get('/admin/foods', { keyword, categoryId, publishStatus, reviewStatus, page, pageSize }, options),
    { page, pageSize },
  )
}

/**
 * 后台食品详情（含草稿与未发布的形态）。
 *
 * 接口：`GET /admin/foods/{id}`（管理角色）
 * 响应：`FoodDetail`
 *
 * 与公开的 `GET /foods/{id}` 的区别只有一个：公开接口对未发布的食品一律 40401，
 * 后台必须能打开草稿，否则「新建 → 编辑 → 提交审核」这条链路走不通。
 *
 * @param {number|string} id 食品 id
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>}
 */
export function getAdminFood(id, options = {}) {
  return get(`/admin/foods/${encodeURIComponent(id)}`, undefined, options)
}

/**
 * 新增食品。
 *
 * 接口：`POST /admin/foods`（管理角色）
 * 请求体：食品主体字段（`name`、`code`、`categoryId`、`intro`、`aliases` 等）
 * 响应：创建后的食品对象（含 id）
 *
 * @param {object} payload 食品数据
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>}
 */
export function createFood(payload, options = {}) {
  return post('/admin/foods', payload, options)
}

/**
 * 编辑食品。
 *
 * 接口：`PUT /admin/foods/{id}`（管理角色）
 *
 * @param {number|string} id 食品 id
 * @param {object} payload 待更新字段
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>}
 */
export function updateFood(id, payload, options = {}) {
  return put(`/admin/foods/${encodeURIComponent(id)}`, payload, options)
}

/**
 * 下架食品（逻辑删除）。
 *
 * 接口：`DELETE /admin/foods/{id}`（管理角色）
 * 界面必须二次确认后再调用。
 *
 * @param {number|string} id 食品 id
 * @param {object} [options] 请求选项
 * @returns {Promise<unknown>}
 */
export function deleteFood(id, options = {}) {
  return del(`/admin/foods/${encodeURIComponent(id)}`, options)
}

/**
 * 审核食品（通过 / 驳回）。
 *
 * 接口：`POST /admin/foods/{id}/review`（管理角色）
 * 请求体：`{ approved: boolean, comment?: string }`
 *
 * @param {number|string} id 食品 id
 * @param {{approved: boolean, comment?: string}} payload 审核结论
 * @param {object} [options] 请求选项
 * @returns {Promise<unknown>}
 */
export function reviewFood(id, payload, options = {}) {
  return post(`/admin/foods/${encodeURIComponent(id)}/review`, payload, options)
}

/**
 * 发布食品。
 *
 * 接口：`POST /admin/foods/{id}/publish`（管理角色）
 *
 * @param {number|string} id 食品 id
 * @param {object} [options] 请求选项
 * @returns {Promise<unknown>}
 */
export function publishFood(id, options = {}) {
  return post(`/admin/foods/${encodeURIComponent(id)}/publish`, undefined, options)
}

/**
 * 新增食品形态（variant）。
 *
 * 接口：`POST /admin/foods/{id}/variants`（管理角色）
 * 请求体：`{ state, stateLabel, cookingMethod?, nameSuffix?, edibleRatio?, description?, isDefault? }`
 *
 * @param {number|string} id 食品 id
 * @param {object} payload 形态数据
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>}
 */
export function createVariant(id, payload, options = {}) {
  return post(`/admin/foods/${encodeURIComponent(id)}/variants`, payload, options)
}

/**
 * 形态营养数据（含草稿）。
 *
 * 接口：`GET /admin/variants/{id}/nutrition`（管理角色）
 * 响应：`NutritionTable`（含未发布的草稿行）
 *
 * @param {number|string} variantId 形态 id
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>}
 */
export function getVariantNutrition(variantId, options = {}) {
  return get(`/admin/variants/${encodeURIComponent(variantId)}/nutrition`, undefined, options)
}

/**
 * 修改营养数据（**必须携带来源**）。
 *
 * 接口：`PUT /admin/variants/{id}/nutrition`（管理角色）
 * 请求体：`{ items: [{ nutrientCode, valueStatus, amount, unit, sourceId }] }`
 * 缺少 `sourceId` 时后端返回 `40001` —— 这是"来源可追溯率 100%"的强制点，
 * 因此本函数在发起请求前先做参数检查，避免无谓往返。
 *
 * @param {number|string} variantId 形态 id
 * @param {{items: Array<{nutrientCode: string, valueStatus: string, amount: number|null, unit: string, sourceId: number}>}} payload
 * @param {object} [options] 请求选项
 * @returns {Promise<unknown>} 失败时抛出 ApiError（code 40001 表示缺来源或参数非法）
 */
export function updateVariantNutrition(variantId, payload, options = {}) {
  const items = (payload && Array.isArray(payload.items) ? payload.items : []).map((item) => {
    if (item && (item.sourceId === undefined || item.sourceId === null || item.sourceId === '')) {
      const error = new Error(`营养素「${item.nutrientCode}」缺少数据来源（sourceId），后端会以 40001 拒绝`)
      error.name = 'ApiError'
      error.code = 40001
      error.traceId = ''
      throw error
    }
    return item
  })
  return put(`/admin/variants/${encodeURIComponent(variantId)}/nutrition`, { items }, options)
}

/**
 * 上传食品图片。
 *
 * 接口：`POST /admin/foods/{id}/images`（管理角色，`multipart/form-data`，字段名 `file`）
 *
 * @param {number|string} id 食品 id
 * @param {File|Blob} file 图片文件
 * @param {object} [options] 请求选项（可含 fields 附加说明）
 * @returns {Promise<object|null>}
 */
export function uploadFoodImage(id, file, options = {}) {
  return upload(`/admin/foods/${encodeURIComponent(id)}/images`, file, options)
}

/**
 * 删除食品图片。
 *
 * 接口：`DELETE /admin/images/{id}`（管理角色）
 *
 * @param {number|string} imageId 图片 id
 * @param {object} [options] 请求选项
 * @returns {Promise<unknown>}
 */
export function deleteImage(imageId, options = {}) {
  return del(`/admin/images/${encodeURIComponent(imageId)}`, options)
}

/**
 * 知识列表。
 *
 * 接口：`GET /admin/knowledge`（管理角色）
 * 查询参数：`keyword`、`category`、`publishStatus`、`page`、`pageSize`
 * 响应：分页结构，`list[]` 为 `{ id, title, category, publishStatus, reviewStatus, version, updatedAt }`
 *
 * @param {object} [params] 查询参数
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export async function getAdminKnowledge(params = {}, options = {}) {
  const { keyword = '', category = '', page = 1, pageSize = 20 } = params
  return withEmptyPageFallback(
    () => get('/admin/knowledge', { keyword, category, page, pageSize }, options),
    { page, pageSize },
  )
}

/**
 * 新增知识。
 *
 * 接口：`POST /admin/knowledge`（管理角色）
 *
 * @param {object} payload 知识数据
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>}
 */
export function createKnowledge(payload, options = {}) {
  return post('/admin/knowledge', payload, options)
}

/**
 * 编辑知识。
 *
 * 接口：`PUT /admin/knowledge/{id}`（管理角色）
 *
 * @param {number|string} id 知识 id
 * @param {object} payload 待更新字段
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>}
 */
export function updateKnowledge(id, payload, options = {}) {
  return put(`/admin/knowledge/${encodeURIComponent(id)}`, payload, options)
}

/**
 * 重建检索索引。
 *
 * 接口：`POST /admin/knowledge/reindex`（管理角色）
 *
 * @param {object} [options] 请求选项
 * @returns {Promise<unknown>}
 */
export function reindexKnowledge(options = {}) {
  return post('/admin/knowledge/reindex', undefined, options)
}

/**
 * 审计日志。
 *
 * 接口：`GET /admin/audit-logs`（管理角色）
 *
 * @param {object} [params] 查询参数（`operatorId`、`action`、`from`、`to`、`page`、`pageSize`）
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export async function getAuditLogs(params = {}, options = {}) {
  const { page = 1, pageSize = 20, ...rest } = params
  return withEmptyPageFallback(
    () => get('/admin/audit-logs', { ...rest, page, pageSize }, options),
    { page, pageSize },
  )
}

/**
 * 用户管理（仅 SYS_ADMIN）。
 *
 * 接口：`GET /admin/users`（管理角色）
 *
 * @param {object} [params] 查询参数（`keyword`、`role`、`page`、`pageSize`）
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export async function getAdminUsers(params = {}, options = {}) {
  const { page = 1, pageSize = 20, ...rest } = params
  return withEmptyPageFallback(
    () => get('/admin/users', { ...rest, page, pageSize }, options),
    { page, pageSize },
  )
}
