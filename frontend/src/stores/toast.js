/**
 * 全局轻提示队列（成功 / 错误 / 信息 / 警告）
 *
 * api 层出错时会调用本 store 入队提示；
 * `components/ToastHost.vue` 负责把它渲染成右下角浮层。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

/** 提示类型（与 ToastHost 的配色一一对应） */
export const TOAST_TYPES = ['success', 'error', 'warning', 'info']

/** 单条提示的存活时长（毫秒） */
const TOAST_DURATION = 4000

/** 同类提示去重用的自增 id */
let seed = 0

export const useToastStore = defineStore('toast', () => {
  /** @type {import('vue').Ref<Array<{id:number, type:string, message:string, duration:number}>>} */
  const items = ref([])

  /** 当前提示条数（用于宿主组件判断是否显示区域） */
  const count = computed(() => items.value.length)

  /**
   * 入队一条提示。
   * @param {string} message 提示文案
   * @param {object} [options]
   * @param {'success'|'error'|'warning'|'info'} [options.type='info'] 类型
   * @param {number} [options.duration] 存活时长（毫秒），传 0 表示不自动关闭
   * @returns {number} 提示 id
   */
  function push(message, options = {}) {
    const type = TOAST_TYPES.includes(options.type) ? options.type : 'info'
    const duration = typeof options.duration === 'number' ? options.duration : TOAST_DURATION
    seed += 1
    const id = seed
    const text = message === null || message === undefined || message === '' ? '操作未成功' : String(message)

    // 同类型同文案只保留最新一条，避免接口连续失败时刷屏
    const duplicated = items.value.find((item) => item.type === type && item.message === text)
    if (duplicated) {
      return duplicated.id
    }

    items.value.push({ id, type, message: text, duration })
    return id
  }

  /** 便捷方法：成功提示 */
  function success(message, options = {}) {
    return push(message, { ...options, type: 'success' })
  }

  /** 便捷方法：错误提示 */
  function error(message, options = {}) {
    return push(message, { ...options, type: 'error' })
  }

  /** 便捷方法：警告提示 */
  function warning(message, options = {}) {
    return push(message, { ...options, type: 'warning' })
  }

  /** 便捷方法：信息提示 */
  function info(message, options = {}) {
    return push(message, { ...options, type: 'info' })
  }

  /**
   * api 层的统一入口（结构为 `{type, message}`），便于 client.js 注入。
   * @param {{type?: string, message?: string}} payload
   */
  function notify(payload = {}) {
    return push(payload.message, { type: payload.type })
  }

  /**
   * 移除指定提示。
   * @param {number} id 提示 id
   */
  function remove(id) {
    items.value = items.value.filter((item) => item.id !== id)
  }

  /** 清空全部提示 */
  function clear() {
    items.value = []
  }

  return { items, count, push, success, error, warning, info, notify, remove, clear }
})
