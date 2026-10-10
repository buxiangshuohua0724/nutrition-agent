/**
 * 会话状态（Pinia）—— AI 问答的多轮会话
 *
 * 依据契约 4.3：
 *   - `POST /agent/sessions` 新建会话（公开，游客可用）
 *   - `GET /agent/sessions/{id}/messages` 会话消息
 *   - `DELETE /agent/sessions/{id}` 删除会话（登录）
 *
 * 说明：`sessionId` 保存在本 store 中，页面刷新后由 `ChatView` 决定是否新建会话。
 * 游客的剩余试用次数以 `GET /agent/capabilities` 的返回为准，前端不自行推断配额。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as agentApi from '../api/agent'
import { useToastStore } from './toast'

export const useChatStore = defineStore('chat', () => {
  /** @type {import('vue').Ref<number|null>} 当前会话 id */
  const sessionId = ref(null)

  /** @type {import('vue').Ref<Array<object>>} 当前会话消息列表 */
  const messages = ref([])

  /** 是否正在等待 AI 回答 */
  const thinking = ref(false)

  /** 当前能力与限制（GET /agent/capabilities） */
  const capabilities = ref(null)

  /** 消息条数 */
  const messageCount = computed(() => messages.value.length)

  /** 是否已有会话 */
  const hasSession = computed(() => sessionId.value !== null)

  /**
   * 拉取当前能力与限制，用于游客剩余次数提示与降级展示。
   * @returns {Promise<object|null>}
   */
  async function loadCapabilities() {
    try {
      const data = await agentApi.getCapabilities({ silent: true })
      capabilities.value = data || null
      return capabilities.value
    } catch {
      // 能力接口失败不影响问答主流程
      return null
    }
  }

  /**
   * 新建会话。
   * @param {object} [payload] 会话创建参数（如标题）
   * @returns {Promise<number|null>} 会话 id
   */
  async function createSession(payload = {}) {
    const data = await agentApi.createSession(payload)
    const id = data && (data.id !== undefined ? data.id : data.sessionId)
    sessionId.value = id !== undefined && id !== null ? Number(id) : null
    messages.value = []
    return sessionId.value
  }

  /**
   * 载入指定会话的消息。
   * @param {number|string} id 会话 id
   * @returns {Promise<Array<object>>}
   */
  async function loadMessages(id) {
    const targetId = Number(id)
    if (!Number.isFinite(targetId)) return []
    const list = await agentApi.getSessionMessages(targetId)
    messages.value = Array.isArray(list) ? list : []
    sessionId.value = targetId
    return messages.value
  }

  /**
   * 追加一条本地消息（用于立即渲染用户提问）。
   * @param {{role: string, content: string, structuredAnswer?: object|null}} message
   * @returns {object} 附加了本地 id 的消息对象
   */
  function appendMessage(message) {
    const item = {
      id: message.id !== undefined ? message.id : `local-${Date.now()}-${messages.value.length}`,
      sessionId: sessionId.value,
      role: message.role,
      content: message.content,
      structuredAnswer: message.structuredAnswer || null,
      createdAt: message.createdAt || new Date().toISOString(),
    }
    messages.value = [...messages.value, item]
    return item
  }

  /**
   * 替换某条消息（用于"思考中"占位被真实回答替换）。
   * @param {string|number} id 消息 id
   * @param {object} patch 要合并的字段
   */
  function updateMessage(id, patch) {
    messages.value = messages.value.map((item) => (item.id === id ? { ...item, ...patch } : item))
  }

  /** 删除当前会话（本地 + 服务端） */
  async function deleteSession() {
    const toast = useToastStore()
    if (sessionId.value === null) {
      messages.value = []
      return
    }
    try {
      await agentApi.deleteSession(sessionId.value)
      toast.success('会话已删除')
    } catch (error) {
      // 错误提示已由 api 层统一处理；这里仍清空本地，避免界面残留
    } finally {
      sessionId.value = null
      messages.value = []
    }
  }

  /** 重置本地会话状态（不请求服务端） */
  function reset() {
    sessionId.value = null
    messages.value = []
    thinking.value = false
  }

  return {
    sessionId,
    messages,
    thinking,
    capabilities,
    messageCount,
    hasSession,
    loadCapabilities,
    createSession,
    loadMessages,
    appendMessage,
    updateMessage,
    deleteSession,
    reset,
  }
})
