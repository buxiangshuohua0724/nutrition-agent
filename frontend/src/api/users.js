/**
 * 用户相关接口
 *
 * 契约依据：`docs/api-contract.md` 4.6
 *   - `POST /auth/register` 公开
 *   - `POST /auth/login` 公开
 *   - `POST /auth/logout` 登录
 *   - `GET /users/me` 登录
 *   - `PUT /users/me/profile` 登录
 *   - `GET /users/me/favorites` 登录
 *   - `POST /users/me/favorites` 登录
 *   - `DELETE /users/me/favorites/{foodId}` 登录
 *   - `DELETE /users/me/data` 登录（删除权）
 *
 * 安全约束：所有 `/users/me/**` 一律以令牌中的 userId 为准，
 *   **前端绝不传 userId**（既不放在路径也不放在请求体）。
 */

import { del, get, post, put } from './client'

/**
 * 登录。
 *
 * 接口：`POST /auth/login`（公开）
 * 请求体：`{ username: string, password: string }`
 * 响应：`{ token: string, user: { id, username, nickname, email, roles[], preferences } }`
 * 失败：用户名或密码错误由后端返回 `40001`；令牌失效返回 `40101`。
 *
 * @param {{username: string, password: string}} payload
 * @param {object} [options] 请求选项
 * @returns {Promise<object>} 含 token 与 user 的响应体（失败时抛出 ApiError）
 */
export function login(payload, options = {}) {
  return post('/auth/login', payload, options)
}

/**
 * 注册。
 *
 * 接口：`POST /auth/register`（公开）
 * 请求体：`{ username: string, password: string, nickname?: string, email?: string }`
 * 响应：`{ id, username, nickname, email, roles[] }`
 *
 * @param {{username: string, password: string, nickname?: string, email?: string}} payload
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>} 注册结果（失败时抛出 ApiError）
 */
export function register(payload, options = {}) {
  return post('/auth/register', payload, options)
}

/**
 * 退出登录。
 *
 * 接口：`POST /auth/logout`（登录）
 *
 * @param {object} [options] 请求选项（常传 `{ silent: true }` 避免退出时弹错）
 * @returns {Promise<unknown>}
 */
export function logout(options = {}) {
  return post('/auth/logout', undefined, options)
}

/**
 * 当前用户信息。
 *
 * 接口：`GET /users/me`（登录）
 * 响应：`{ id, username, nickname, email, avatarUrl, roles[], preferences, createdAt }`
 *
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>}
 */
export function getCurrentUser(options = {}) {
  return get('/users/me', undefined, options)
}

/**
 * 更新资料与偏好。
 *
 * 接口：`PUT /users/me/profile`（登录）
 * 请求体：`{ nickname?, email?, avatarUrl?, preferences?: { goal?, avoid?: string[] } }`
 * 响应：更新后的用户对象
 *
 * @param {object} payload 资料字段（不含 userId）
 * @param {object} [options] 请求选项
 * @returns {Promise<object|null>}
 */
export function updateProfile(payload, options = {}) {
  return put('/users/me/profile', payload, options)
}

/**
 * 收藏列表。
 *
 * 接口：`GET /users/me/favorites`（登录）
 * 响应：`FoodSummary[]` 或分页结构；本函数统一返回数组。
 *
 * @param {object} [options] 请求选项
 * @returns {Promise<Array<object>>}
 */
export async function getFavorites(options = {}) {
  const data = await get('/users/me/favorites', undefined, options)
  if (Array.isArray(data)) return data
  if (data && Array.isArray(data.list)) return data.list
  return []
}

/**
 * 添加收藏。
 *
 * 接口：`POST /users/me/favorites`（登录）
 * 请求体：`{ foodId: number }`
 * 重复收藏由后端返回 `40901`。
 *
 * @param {number|string} foodId 食品 id
 * @param {object} [options] 请求选项
 * @returns {Promise<unknown>}
 */
export function addFavorite(foodId, options = {}) {
  return post('/users/me/favorites', { foodId: Number(foodId) }, options)
}

/**
 * 取消收藏。
 *
 * 接口：`DELETE /users/me/favorites/{foodId}`（登录）
 *
 * @param {number|string} foodId 食品 id
 * @param {object} [options] 请求选项
 * @returns {Promise<unknown>}
 */
export function removeFavorite(foodId, options = {}) {
  return del(`/users/me/favorites/${encodeURIComponent(foodId)}`, options)
}

/**
 * 删除个人数据（PRD FR-007 删除权）。
 *
 * 接口：`DELETE /users/me/data`（登录）
 *
 * @param {object} [options] 请求选项
 * @returns {Promise<unknown>}
 */
export function deleteMyData(options = {}) {
  return del('/users/me/data', options)
}
