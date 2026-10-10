/**
 * 收藏状态（Pinia）—— 本地 id 集合 + 乐观更新
 *
 * 依据契约 4.6：
 *   - `GET /users/me/favorites`、`POST /users/me/favorites`、`DELETE /users/me/favorites/{foodId}`
 *   - 所有接口均需登录，且以令牌中的 userId 为准
 *
 * 设计：
 *   1. 本地维护一份收藏 id 集合，持久化到 localStorage（key `na_favorites`），
 *      后端未就绪或游客浏览时页面依然可用。
 *   2. 用户点击收藏立即翻转本地状态（乐观更新），再后台请求接口；
 *      请求失败则**回滚**并提示，绝不留下与后端不一致的假状态。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as usersApi from '../api/users'
import { useAuthStore } from './auth'
import { useToastStore } from './toast'

/** localStorage 键 */
const FAVORITES_STORAGE_KEY = 'na_favorites'

/** 安全读取本地收藏 id 列表 */
function restoreIds() {
  try {
    const raw = localStorage.getItem(FAVORITES_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.map((id) => Number(id)).filter((id) => Number.isFinite(id))
  } catch {
    return []
  }
}

/** 安全写入本地收藏 id 列表 */
function persistIds(ids) {
  try {
    localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(ids))
  } catch {
    // 忽略写入失败（隐私模式）
  }
}

export const useFavoritesStore = defineStore('favorites', () => {
  /** @type {import('vue').Ref<number[]>} 收藏的食品 id 列表 */
  const ids = ref(restoreIds())

  /** 收藏列表的完整对象（来自接口或本地记录） */
  const items = ref([])

  /** 列表加载中 */
  const loading = ref(false)

  /** 是否曾经从接口同步过 */
  const synced = ref(false)

  /** 收藏数量 */
  const count = computed(() => ids.value.length)

  /** id 集合，便于 O(1) 判断 */
  const idSet = computed(() => new Set(ids.value))

  /**
   * 判断某食品是否已收藏。
   * @param {number|string|null|undefined} foodId 食品 id
   * @returns {boolean}
   */
  function isFavorite(foodId) {
    const id = Number(foodId)
    if (!Number.isFinite(id)) return false
    return idSet.value.has(id)
  }

  /** 持久化并同步内存集合 */
  function commit(nextIds) {
    const unique = Array.from(new Set(nextIds.map((id) => Number(id)).filter((id) => Number.isFinite(id))))
    ids.value = unique
    persistIds(unique)
  }

  /**
   * 取消收藏。
   * @param {number|string} foodId 食品 id
   * @returns {Promise<boolean>} 是否取消成功
   */
  async function removeFavorite(foodId) {
    const id = Number(foodId)
    if (!Number.isFinite(id) || !idSet.value.has(id)) return true

    const auth = useAuthStore()
    const toast = useToastStore()
    const previousIds = [...ids.value]

    // 乐观更新：先本地移除
    commit(ids.value.filter((item) => item !== id))
    items.value = items.value.filter((item) => Number(item.id) !== id)

    if (!auth.isLoggedIn) {
      // 未登录仅维护本地收藏，不发请求
      return true
    }

    try {
      await usersApi.removeFavorite(id)
      return true
    } catch (error) {
      // 回滚，避免状态与后端不一致
      commit(previousIds)
      toast.error('取消收藏失败，已恢复原状态')
      return false
    }
  }

  /**
   * 添加收藏。
   * @param {number|string} foodId 食品 id
   * @returns {Promise<boolean>} 是否添加成功
   */
  async function addFavorite(foodId) {
    const id = Number(foodId)
    if (!Number.isFinite(id) || idSet.value.has(id)) return true

    const auth = useAuthStore()
    const toast = useToastStore()
    const previousIds = [...ids.value]

    // 乐观更新：先本地加入
    commit([...ids.value, id])

    if (!auth.isLoggedIn) {
      return true
    }

    try {
      await usersApi.addFavorite(id)
      return true
    } catch (error) {
      // 40901（重复收藏）视为已收藏成功，不视为失败
      if (error && Number(error.code) === 40901) {
        return true
      }
      commit(previousIds)
      toast.error('收藏失败，已恢复原状态')
      return false
    }
  }

  /**
   * 切换收藏状态。
   * @param {number|string} foodId 食品 id
   * @returns {Promise<boolean>} 操作后是否处于"已收藏"状态
   */
  async function toggleFavorite(foodId) {
    const id = Number(foodId)
    if (!Number.isFinite(id)) return false
    if (idSet.value.has(id)) {
      await removeFavorite(id)
    } else {
      await addFavorite(id)
    }
    return idSet.value.has(id)
  }

  /**
   * 从接口同步收藏列表（登录用户）。
   * 失败时保留本地集合，不影响页面使用。
   * @param {object} [options]
   * @param {boolean} [options.silent=false] 失败时是否不提示
   * @returns {Promise<{isDemoData: boolean}>}
   */
  async function loadFavorites(options = {}) {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      // 游客：直接用本地收藏
      items.value = []
      return { isDemoData: false }
    }

    loading.value = true
    try {
      const result = await usersApi.getFavorites()
      const list = Array.isArray(result) ? result : []
      items.value = list
      commit(list.map((item) => item.id))
      synced.value = true
      return { isDemoData: false }
    } catch (error) {
      if (!options.silent) {
        // 错误提示已由 api 层统一处理，这里只记录状态
        console.info('[收藏] 同步失败，继续使用本地收藏集合')
      }
      return { isDemoData: false }
    } finally {
      loading.value = false
    }
  }

  /** 清空本地收藏（删除个人数据等场景） */
  function clear() {
    commit([])
    items.value = []
    synced.value = false
  }

  return {
    ids,
    items,
    loading,
    synced,
    count,
    isFavorite,
    addFavorite,
    removeFavorite,
    toggleFavorite,
    loadFavorites,
    clear,
  }
})
