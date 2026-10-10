/**
 * 浏览历史（Pinia）—— 本地持久化，登录用户可从接口补齐
 *
 * 契约中没有"浏览历史"接口（FR-008 的历史指对话历史，见 stores/chat.js），
 * 因此这里的历史记录以 localStorage 为准：
 *   - 打开食品详情页时记录一条；
 *   - 保留最近 100 条，同一食品去重并把新记录置顶。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

/** localStorage 键 */
const HISTORY_STORAGE_KEY = 'na_history'

/** 最多保留的条数 */
const MAX_HISTORY = 100

/** 安全读取历史 */
function restoreHistory() {
  try {
    const raw = localStorage.getItem(HISTORY_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((item) => item && item.foodId !== undefined && item.foodId !== null)
  } catch {
    return []
  }
}

/** 安全写入历史 */
function persistHistory(list) {
  try {
    localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(list))
  } catch {
    // 忽略写入失败
  }
}

export const useHistoryStore = defineStore('history', () => {
  /** @type {import('vue').Ref<Array<{foodId:number, name:string, code:string|null, categoryName:string|null, coverImageUrl:string|null, viewedAt:string}>>} */
  const records = ref(restoreHistory())

  /** 历史条数 */
  const count = computed(() => records.value.length)

  /** 最近 N 条 */
  const recent = computed(() => records.value.slice(0, 10))

  /**
   * 记录一次浏览。
   * @param {{id?: number|string, foodId?: number|string, name?: string, code?: string|null, categoryName?: string|null, coverImageUrl?: string|null}} food
   */
  function recordView(food) {
    if (!food) return
    const foodId = Number(food.foodId !== undefined ? food.foodId : food.id)
    if (!Number.isFinite(foodId)) return

    const entry = {
      foodId,
      name: food.name || '未命名食品',
      code: food.code || null,
      categoryName: food.categoryName || (food.category && food.category.name) || null,
      coverImageUrl: food.coverImageUrl || null,
      viewedAt: new Date().toISOString(),
    }

    const rest = records.value.filter((item) => Number(item.foodId) !== foodId)
    const next = [entry, ...rest].slice(0, MAX_HISTORY)
    records.value = next
    persistHistory(next)
  }

  /**
   * 删除一条历史。
   * @param {number|string} foodId
   */
  function removeRecord(foodId) {
    const id = Number(foodId)
    const next = records.value.filter((item) => Number(item.foodId) !== id)
    records.value = next
    persistHistory(next)
  }

  /** 清空历史 */
  function clear() {
    records.value = []
    persistHistory([])
  }

  return { records, count, recent, recordView, removeRecord, clear }
})
