/**
 * 分类与食品接口
 *
 * 契约依据：`docs/api-contract.md` 4.1
 *   全部为公开接口，无需登录。
 */

import { get } from './client'
import {
  DEMO_CATEGORIES,
  DEMO_DEFAULT_VARIANT,
  DEMO_FEATURED,
  DEMO_FOODS,
  DEMO_FOOD_DETAILS_BY_ID,
  DEMO_NUTRITION_TABLES_BY_VARIANT,
  demoClone,
  withDemo,
} from './demo-data'

/**
 * 把数组切成契约里的分页结构。
 *
 * 只做分页计数，**不碰任何营养数值**。
 *
 * @param {Array<object>} items 全量条目
 * @param {number} page 页码，从 1 开始
 * @param {number} pageSize 每页条数
 * @returns {{list: Array<object>, page: number, pageSize: number, total: number, totalPages: number}}
 */
function paginate(items, page, pageSize) {
  const total = items.length
  const totalPages = total > 0 ? Math.ceil(total / pageSize) : 0
  const start = (page - 1) * pageSize
  return {
    list: items.slice(start, start + pageSize),
    page,
    pageSize,
    total,
    totalPages,
  }
}

/** 是否为空值（null / undefined / 空串） */
function isBlank(value) {
  return value === null || value === undefined || value === ''
}

/**
 * 演示态的食品检索，复刻后端的匹配口径：
 * 标准名、英文名、别名、食品编码，全部不区分大小写。
 *
 * @param {{keyword?: string, categoryId?: number|null}} params
 * @returns {Array<object>} 命中的 FoodSummary
 */
function matchDemoFoods({ keyword = '', categoryId = null } = {}) {
  const kw = String(keyword || '').trim().toLowerCase()
  return DEMO_FOODS.filter((food) => {
    if (!isBlank(categoryId) && Number(food.categoryId) !== Number(categoryId)) return false
    if (!kw) return true
    const detail = DEMO_FOOD_DETAILS_BY_ID[food.id]
    const haystack = [
      food.name,
      food.enName,
      food.code,
      ...((detail && detail.aliases) || []).map((item) => item.alias),
    ]
    return haystack.some((text) => String(text || '').toLowerCase().includes(kw))
  })
}

/**
 * 分类列表（含食品数量）。
 *
 * 接口：`GET /categories`（公开）
 * 响应：`FoodCategory[]`
 *   `{ id: number, name: string, slug: string, foodCount: number, iconUrl?: string|null }`
 *
 * @param {object} [options] 请求选项（silent 等）
 * @returns {Promise<{data: Array<object>|null, isDemoData: boolean, error: Error|null}>}
 */
export function getCategories(options = {}) {
  return withDemo(
    () => get('/categories', undefined, options),
    () => DEMO_CATEGORIES.map((category) => ({ ...category })),
  )
}

/**
 * 分类下食品分页。
 *
 * 接口：`GET /categories/{slug}/foods`（公开）
 * 查询参数：`page`（默认 1）、`pageSize`（默认 20，上限 100）
 * 响应：分页结构 `{ list: FoodSummary[], page, pageSize, total, totalPages }`
 *
 * @param {string} slug 分类 slug
 * @param {{page?: number, pageSize?: number}} [params]
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export async function getCategoryFoods(slug, params = {}, options = {}) {
  const { page = 1, pageSize = 20 } = params
  return withDemo(
    () => get(`/categories/${encodeURIComponent(slug)}/foods`, { page, pageSize }, options),
    () => paginate(matchDemoFoods({ categoryId: categoryIdOfSlug(slug) }), page, pageSize),
  )
}

/**
 * slug → 分类 id（演示态用）。
 * 未知 slug 返回一个不可能匹配的值，让结果为空而不是"整个食品库"。
 * @param {string} slug
 * @returns {number}
 */
function categoryIdOfSlug(slug) {
  const matched = DEMO_CATEGORIES.find((item) => String(item.slug) === String(slug))
  return matched ? matched.id : -1
}

/**
 * 搜索食品。
 *
 * 接口：`GET /foods/search`（公开）
 * 查询参数：
 *   - `keyword`：支持标准名、英文名、别名模糊匹配
 *   - `categoryId`：分类筛选
 *   - `page`：页码，默认 1
 *   - `pageSize`：每页条数，默认 20，上限 100
 *   - `sort`：`relevance` | `name` | `energyAsc` | `energyDesc`，默认 `relevance`
 * 响应：分页结构 `{ list: FoodSummary[], page, pageSize, total, totalPages }`
 *
 * 注意：后端只返回已发布且已审核的食品。
 *
 * @param {{keyword?: string, categoryId?: number|null, page?: number, pageSize?: number, sort?: string}} [params]
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null, keyword: string}>}
 */
export async function searchFoods(params = {}, options = {}) {
  const { keyword = '', categoryId = null, page = 1, pageSize = 20, sort = 'relevance' } = params
  const result = await withDemo(
    () => get('/foods/search', { keyword, categoryId, page, pageSize, sort }, options),
    () => paginate(sortDemoFoods(matchDemoFoods({ keyword, categoryId }), sort), page, pageSize),
  )
  return { ...result, keyword }
}

/**
 * 演示态排序。与契约 4.2 的 `sort` 取值一致，`relevance` 保持食品清单原序
 * （演示数据没有相关度评分，不臆造一个）。
 *
 * @param {Array<object>} items
 * @param {string} sort
 * @returns {Array<object>} 新数组
 */
function sortDemoFoods(items, sort) {
  const list = items.slice()
  if (sort === 'name') {
    return list.sort((a, b) => String(a.name).localeCompare(String(b.name), 'zh-Hans-CN'))
  }
  if (sort === 'energyAsc' || sort === 'energyDesc') {
    const dir = sort === 'energyAsc' ? 1 : -1
    // 能量缺失（null）的条目一律排在最后，绝不当成 0 参与排序
    return list.sort((a, b) => {
      const left = a.energyKcal === null || a.energyKcal === undefined ? null : Number(a.energyKcal)
      const right = b.energyKcal === null || b.energyKcal === undefined ? null : Number(b.energyKcal)
      if (left === null && right === null) return 0
      if (left === null) return 1
      if (right === null) return -1
      return (left - right) * dir
    })
  }
  return list
}

/**
 * 食品详情。
 *
 * 接口：`GET /foods/{id}`（公开）
 * 响应：`FoodDetail`（契约 3.2），包含 `aliases`、`images`、`variants`、`category` 等。
 * 食品不存在时后端返回 `40401`。
 *
 * @param {number|string} id 食品 id
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export function getFoodDetail(id, options = {}) {
  return withDemo(
    () => get(`/foods/${encodeURIComponent(id)}`, undefined, options),
    () => {
      // 演示数据集与种子库一一对应，能按 id 精确命中。
      // **命中不了就返回 null**：后端对不存在的食品返回 40401，
      // 演示态必须表达同一种事实，而不是"拿别的食品（曾经是 foodId=12 苹果）顶上、
      // 再把 id 覆盖成请求值"——那会凭空造出一个库里并不存在的食品，
      // 叠加识别候选的错误引用后，/food/41 会正儿八经地显示苹果的营养数据。
      // 返回 null 后由调用方按「食品不存在 / 暂无数据」渲染。
      const requested = Number(id)
      const matched = DEMO_FOOD_DETAILS_BY_ID[requested]
      return matched ? demoClone(matched) : null
    },
  )
}

/**
 * 指定形态的营养表。
 *
 * 接口：`GET /foods/{id}/nutrition`（公开）
 * 查询参数：`variantId`（可选，省略时取默认形态）
 * 响应：`NutritionTable`（契约 3.3，三态 `valueStatus`）
 * 营养数据不存在时后端返回 `40402`。
 *
 * @param {number|string} id 食品 id
 * @param {{variantId?: number|string|null}} [params]
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export function getFoodNutrition(id, params = {}, options = {}) {
  const { variantId = null } = params
  return withDemo(
    () => get(`/foods/${encodeURIComponent(id)}/nutrition`, { variantId }, options),
    () => {
      const foodId = Number(id)
      const requested = variantId === null || variantId === undefined || variantId === '' ? null : Number(variantId)
      // 演示数据集覆盖种子库全部 37 个形态，能按 variantId 精确命中。
      const requestedTable = requested === null ? null : DEMO_NUTRITION_TABLES_BY_VARIANT[requested] || null
      // 形态必须属于被查询的食品：`variantId=19` 是**苹果**的形态，拿它配 `foodId=1`
      // 会拼出一份"西红柿的 id 加苹果的数值"的假营养表，页面会一本正经地显示错数据。
      // 这种请求不是"没找到"，而是自相矛盾，按不存在处理（调用方会渲染「暂无数据」）。
      if (requestedTable && Number(requestedTable.foodId) !== foodId) {
        return null
      }
      const table = requestedTable || DEMO_NUTRITION_TABLES_BY_VARIANT[DEMO_DEFAULT_VARIANT[foodId]] || null
      if (!table) {
        // 没有对应形态的数据是**真实状态**（而不是"拿别的食品的数据顶上"）。
        // 返回 null，让调用方按 NUTRITION_NOT_FOUND 的分支渲染「暂无数据」。
        return null
      }
      // 只覆盖纯标识字段，营养数值一律来自演示数据集。
      // 这里用 `table.foodId`（= foodId）而不是 `foodId || table.foodId`：
      // 后者在 id 非法（NaN）时会悄悄把食品换成苹果，与"数据属于哪个食品"这一
      // 前提直接冲突——宁可不返回数据，也不返回另一只食品的营养表。
      return demoClone(table, { foodId: table.foodId })
    },
  )
}

/**
 * 热门 / 推荐 / 近期新增食品。
 *
 * 接口：`GET /foods/featured`（公开）
 * 查询参数：`type`（可选：`hot` | `new` | `recommended`）、`limit`（可选）
 * 响应：`FoodSummary[]`
 *
 * @param {{type?: string, limit?: number}} [params]
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: Array<object>|null, isDemoData: boolean, error: Error|null}>}
 */
export function getFeaturedFoods(params = {}, options = {}) {
  const { type = 'hot', limit = 5 } = params
  return withDemo(
    () => get('/foods/featured', { type, limit }, options),
    () => DEMO_FEATURED.slice(0, limit).map((food) => ({ ...food })),
  )
}

/**
 * 相关食品 / 食谱推荐。
 *
 * 接口：`GET /foods/{id}/related`（公开）
 * 响应：`FoodSummary[]`
 *
 * @param {number|string} id 食品 id
 * @param {{limit?: number}} [params]
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: Array<object>|null, isDemoData: boolean, error: Error|null}>}
 */
export function getRelatedFoods(id, params = {}, options = {}) {
  const { limit = 6 } = params
  return withDemo(
    () => get(`/foods/${encodeURIComponent(id)}/related`, { limit }, options),
    () =>
      DEMO_FEATURED.filter((food) => String(food.id) !== String(id))
        .slice(0, limit)
        .map((food) => ({ ...food })),
  )
}
