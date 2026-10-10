/**
 * 分类与食品接口
 *
 * 契约依据：`docs/api-contract.md` 4.1
 *   全部为公开接口，无需登录。
 */

import { get } from './client'
import {
  DEMO_CATEGORIES,
  DEMO_FEATURED,
  DEMO_FOODS,
  DEMO_FOOD_DETAIL,
  DEMO_NUTRITION_TABLE,
  DEMO_NUTRITION_TABLE_COOKED,
  demoClone,
  withDemo,
  withEmptyPageFallback,
} from './demo-data'

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
  return withEmptyPageFallback(
    () => get(`/categories/${encodeURIComponent(slug)}/foods`, { page, pageSize }, options),
    { page, pageSize },
  )
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
  const result = await withEmptyPageFallback(
    () => get('/foods/search', { keyword, categoryId, page, pageSize, sort }, options),
    { page, pageSize },
  )
  return { ...result, keyword }
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
      // 演示详情原型只对应西红柿；若用户访问的是别的食品，
      // 先用演示摘要列表里的同名食品覆盖**纯标识与文本字段**，
      // 使其与 `/food/:id` 一致。营养数值一律不在此处改动。
      const requested = Number(id)
      const summary = DEMO_FOODS.find((food) => Number(food.id) === requested)
      const overrides = { id: requested || DEMO_FOOD_DETAIL.id }
      if (summary) {
        overrides.code = summary.code
        overrides.name = summary.name
        overrides.enName = summary.enName
        overrides.intro = summary.intro
        overrides.category = {
          id: summary.categoryId,
          name: summary.categoryName,
          slug: summary.categorySlug,
        }
      }
      return demoClone(DEMO_FOOD_DETAIL, overrides)
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
      // 演示数据含两个形态，按请求的 variantId 选取；未指定时用默认形态
      const isCooked = String(variantId) === String(DEMO_NUTRITION_TABLE_COOKED.variantId)
      const base = isCooked ? DEMO_NUTRITION_TABLE_COOKED : DEMO_NUTRITION_TABLE
      const overrides = { foodId: Number(id) || base.foodId }
      if (variantId !== null && variantId !== undefined && variantId !== '') {
        overrides.variantId = Number(variantId) || base.variantId
      }
      return demoClone(base, overrides)
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
