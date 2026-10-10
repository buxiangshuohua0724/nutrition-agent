<script setup>
/**
 * AI 问答页（原型板块 4）
 *
 * 数据来源：
 *   - `POST /agent/chat`                 提问（游客有限次，超限返回 42901）
 *   - `GET  /agent/capabilities`         能力与限制（用于游客剩余次数提示）
 *
 * 契约硬规则（前端逐条落实）：
 *   1. `sources` 由后端工具层注入，前端**只渲染不编造**；
 *   2. `uncertainty` 非空时**必须可见展示**（回答上方的不确定性提示条）；
 *   3. `degraded === true` 时必须提示"结果已降级"，不能当正常结果。
 *
 * 正文渲染走 `RichText`（安全解析，不使用 v-html）。
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import RichText from '../components/RichText.vue'
import SourceList from '../components/SourceList.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import EmptyState from '../components/EmptyState.vue'
import NavIcon from '../components/NavIcon.vue'
import { chat } from '../api/agent'
import { useChatStore } from '../stores/chat'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { formatDateTime } from '../utils/format'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const authStore = useAuthStore()
const toastStore = useToastStore()

const question = ref('')
const isDemoData = ref(false)
const lastDegraded = ref(false)
const scrollAnchor = ref(null)

/** 是否处于思考中（禁用输入 + 展示"思考中"） */
const thinking = computed(() => chatStore.thinking)

/** 游客额度提示：数字来自 capabilities，前端不自行推断配额 */
const guestQuotaText = computed(() => {
  const capabilities = chatStore.capabilities
  if (!capabilities || authStore.isLoggedIn) return ''
  const quota = capabilities.guestChatQuota
  if (quota === undefined || quota === null) return '游客模式可试用，登录后额度更高。'
  return `游客模式每天可试用 ${quota} 次，登录后额度更高。`
})

/** 能力限制提示（后端声明的能力边界，原样展示） */
const limitationItems = computed(() => {
  const capabilities = chatStore.capabilities
  if (!capabilities || !Array.isArray(capabilities.limitations)) return []
  return capabilities.limitations
})

/** 后端未配置模型时的提示 */
const modelMissing = computed(() => {
  const capabilities = chatStore.capabilities
  return Boolean(capabilities) && capabilities.modelConfigured === false
})

/** 开场引导问题（只是便捷入口，不代表任何营养结论） */
const suggestions = [
  '西红柿每 100g 含多少热量？',
  '减脂期间怎么吃蔬菜更合适？',
  '鸡蛋和牛奶的蛋白质哪个更高？',
]

/** 滚动到底部 */
async function scrollToBottom() {
  await nextTick()
  if (scrollAnchor.value && scrollAnchor.value.scrollIntoView) {
    scrollAnchor.value.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }
}

/**
 * 追加一条"思考中"占位消息。
 * @returns {object} 占位消息
 */
function appendThinkingPlaceholder() {
  return chatStore.appendMessage({ role: 'ASSISTANT', content: '', thinking: true })
}

/**
 * 提交问题。
 * @param {string} [text] 直接指定问题（点击引导问题）
 */
async function submit(text) {
  const content = typeof text === 'string' ? text.trim() : question.value.trim()
  if (!content || thinking.value) return

  chatStore.appendMessage({ role: 'USER', content })
  question.value = ''
  chatStore.thinking = true
  const placeholder = appendThinkingPlaceholder()
  await scrollToBottom()

  const { data, isDemoData: demo, error } = await chat({
    sessionId: chatStore.sessionId,
    question: content,
    foodId: route.query.foodId || null,
    weight: route.query.weight || null,
  })

  chatStore.thinking = false

  if (error) {
    // 失败的占位消息移出列表（错误提示已由 api 层统一弹出）
    chatStore.messages = chatStore.messages.filter((item) => item.id !== placeholder.id)
    lastDegraded.value = false
    return
  }

  if (demo) isDemoData.value = true
  lastDegraded.value = Boolean(data && data.degraded)

  // 首次提问后由后端回传会话 id，后续轮次带上它实现多轮会话
  const returnedSessionId = data
    ? data.sessionId !== undefined
      ? data.sessionId
      : data.id
    : null
  if (returnedSessionId !== undefined && returnedSessionId !== null) {
    chatStore.sessionId = Number(returnedSessionId)
  }

  chatStore.updateMessage(placeholder.id, {
    content: data && data.answer ? data.answer : '（后端未返回回答正文）',
    structuredAnswer: data || null,
    thinking: false,
  })
  await scrollToBottom()
}

/**
 * 把 `uncertainty` 数组的每一项转成可读文本。
 * 契约里该项是对象 `{ code, text }`，直接插值会渲染成 `[object Object]`，
 * 因此这里只取后端给出的文本字段，取不到就退化为错误码，绝不自行编造说明。
 * @param {object|string} item 不确定性条目
 * @returns {string}
 */
function uncertaintyText(item) {
  if (item === null || item === undefined) return ''
  if (typeof item === 'string') return item
  if (typeof item === 'object') {
    return item.text || item.message || item.description || item.code || item.reason || ''
  }
  return String(item)
}

/** 结束当前会话，开始新的对话 */
async function startNewSession() {
  await chatStore.deleteSession()
  lastDegraded.value = false
  toastStore.info('已开始新的对话')
}

/** 重新加载当前页面（用于失败后重试） */
function reload() {
  router.go(0)
}

onMounted(async () => {
  await chatStore.loadCapabilities()
  await scrollToBottom()
})
</script>

<template>
  <div class="na-chat">
    <header class="na-chat__head">
      <div>
        <h2 class="na-chat__title">AI 问答</h2>
        <p class="na-chat__desc">基于已审核的营养知识库回答，回答中的参考来源由后端注入。</p>
      </div>
      <div class="na-chat__head-actions">
        <button
          v-if="chatStore.messages.length > 0"
          type="button"
          class="na-btn na-btn--ghost"
          :disabled="thinking"
          @click="startNewSession"
        >
          新建对话
        </button>
      </div>
    </header>

    <NoticeBanner
      v-if="isDemoData"
      variant="warning"
      title="演示数据"
      message="后端接口当前不可用，以下回答为开发模式下的内联演示数据，不是真实的 AI 回答，不能作为营养依据。"
    />

    <NoticeBanner
      v-if="modelMissing"
      variant="danger"
      title="AI 能力不可用"
      message="后端未配置模型服务，问答能力当前不可用。请配置模型后再试。"
    />

    <NoticeBanner
      v-if="lastDegraded"
      variant="warning"
      title="结果已降级"
      message="本次回答在后端发生了降级（例如知识库检索失败或模型超时），内容完整性与准确性可能下降，请谨慎参考。"
    />

    <NoticeBanner
      v-if="limitationItems.length > 0"
      variant="info"
      title="能力边界"
      :items="limitationItems"
    />

    <p v-if="!authStore.isLoggedIn && guestQuotaText" class="na-chat__quota">
      {{ guestQuotaText }}
      <RouterLink to="/login">去登录</RouterLink>
      ·
      <RouterLink to="/recognize">也可以先试试图片识别</RouterLink>
    </p>

    <!-- 消息列表 -->
    <section ref="scrollAnchor" class="na-chat__body">
      <EmptyState
        v-if="chatStore.messages.length === 0 && !thinking"
        icon="chat"
        title="问点什么吧"
        description="可以询问某食品的营养成分、不同食材的对比，或某类人群的饮食注意事项。"
      >
        <div class="na-chat__suggestions">
          <button
            v-for="item in suggestions"
            :key="item"
            type="button"
            class="na-chip"
            @click="submit(item)"
          >
            {{ item }}
          </button>
        </div>
      </EmptyState>

      <div v-for="message in chatStore.messages" :key="message.id" class="na-chat__row" :class="`na-chat__row--${message.role === 'USER' ? 'user' : 'ai'}`">
        <span v-if="message.role !== 'USER'" class="na-chat__avatar">
          <NavIcon name="sparkle" :size="18" />
        </span>

        <div class="na-chat__bubble" :class="message.role === 'USER' ? 'na-chat__bubble--user' : 'na-chat__bubble--ai'">
          <!-- 思考中 -->
          <p v-if="message.thinking" class="na-chat__thinking">思考中…</p>

          <template v-else>
            <!-- 不确定性提示：uncertainty 非空时必须可见 -->
            <div
              v-if="message.structuredAnswer && message.structuredAnswer.uncertainty && message.structuredAnswer.uncertainty.length > 0"
              class="na-chat__uncertainty"
              role="note"
            >
              <strong>不确定性提示</strong>
              <ul>
                <li v-for="(item, index) in message.structuredAnswer.uncertainty" :key="index">{{ uncertaintyText(item) }}</li>
              </ul>
            </div>

            <RichText :source="message.content" placeholder="（无内容）" />

            <!-- 警告 -->
            <ul
              v-if="message.structuredAnswer && message.structuredAnswer.warnings && message.structuredAnswer.warnings.length > 0"
              class="na-chat__warnings"
            >
              <li v-for="(item, index) in message.structuredAnswer.warnings" :key="index">{{ item }}</li>
            </ul>

            <!-- 推荐 -->
            <ul
              v-if="message.structuredAnswer && message.structuredAnswer.recommendations && message.structuredAnswer.recommendations.length > 0"
              class="na-chat__recommendations"
            >
              <li v-for="(item, index) in message.structuredAnswer.recommendations" :key="index">{{ item }}</li>
            </ul>

            <!-- 参考来源：原样渲染后端注入的 sources -->
            <SourceList
              v-if="message.structuredAnswer && message.structuredAnswer.sources && message.structuredAnswer.sources.length > 0"
              :sources="message.structuredAnswer.sources"
              compact
            />

            <p v-if="message.createdAt" class="na-chat__time">{{ formatDateTime(message.createdAt) }}</p>
          </template>
        </div>
      </div>
    </section>

    <!-- 输入区 -->
    <form class="na-chat__composer" @submit.prevent="submit()">
      <label class="na-sr-only" for="na-chat-input">输入你的问题</label>
      <textarea
        id="na-chat-input"
        v-model="question"
        class="na-input na-chat__input"
        rows="2"
        :disabled="thinking"
        placeholder="请输入你的问题，例如：西红柿每 100g 含多少热量？"
        @keydown.enter.exact.prevent="submit()"
      ></textarea>
      <button type="submit" class="na-btn na-btn--primary na-chat__send" :disabled="thinking || !question.trim()">
        {{ thinking ? '思考中…' : '发送' }}
      </button>
    </form>

    <p class="na-chat__footnote">
      回答仅基于已收录并审核的营养知识，不能替代医生或注册营养师的诊断与建议。
    </p>
  </div>
</template>

<style scoped>
.na-chat {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-3, 12px);
}

.na-chat__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--na-space-3, 12px);
}

.na-chat__title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.na-chat__desc {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-chat__quota {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-chat__quota a {
  color: var(--na-color-primary-600, #2563eb);
}

.na-chat__body {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
  min-height: 320px;
  max-height: 56vh;
  overflow-y: auto;
  padding: var(--na-space-4, 16px);
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-lg, 12px);
  background: var(--na-color-surface-muted, #f9fafb);
}

.na-chat__suggestions {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
  margin-top: var(--na-space-3, 12px);
}

.na-chat__row {
  display: flex;
  gap: var(--na-space-2, 8px);
  align-items: flex-start;
}

.na-chat__row--user {
  justify-content: flex-end;
}

.na-chat__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--na-color-primary-50, #eff6ff);
  color: var(--na-color-primary-600, #2563eb);
  flex: none;
}

.na-chat__bubble {
  max-width: min(680px, 82%);
  padding: var(--na-space-3, 12px) var(--na-space-4, 16px);
  border-radius: var(--na-radius-lg, 12px);
  font-size: 14px;
  line-height: 1.7;
}

.na-chat__bubble--user {
  background: var(--na-color-primary-500, #3b82f6);
  color: #ffffff;
  white-space: pre-wrap;
  word-break: break-word;
}

.na-chat__bubble--ai {
  background: var(--na-color-surface, #ffffff);
  border: 1px solid var(--na-color-border, #e5e7eb);
  color: var(--na-color-text, #111827);
}

.na-chat__thinking {
  margin: 0;
  color: var(--na-color-text-muted, #6b7280);
}

.na-chat__uncertainty {
  margin-bottom: var(--na-space-3, 12px);
  padding: var(--na-space-2, 8px) var(--na-space-3, 12px);
  border-left: 3px solid var(--na-color-warning, #f59e0b);
  border-radius: var(--na-radius-sm, 6px);
  background: #fffbeb;
  font-size: 13px;
}

.na-chat__uncertainty ul {
  margin: 4px 0 0;
  padding-left: 20px;
}

.na-chat__warnings,
.na-chat__recommendations {
  margin: var(--na-space-3, 12px) 0 0;
  padding-left: 20px;
  font-size: 13px;
}

.na-chat__warnings li {
  color: var(--na-color-danger, #ef4444);
}

.na-chat__time {
  margin: var(--na-space-2, 8px) 0 0;
  font-size: 12px;
  color: var(--na-color-text-muted, #9ca3af);
}

.na-chat__composer {
  display: flex;
  gap: var(--na-space-2, 8px);
  align-items: flex-end;
}

.na-chat__input {
  flex: 1;
  resize: vertical;
  min-height: 58px;
}

.na-chat__send {
  flex: none;
  height: var(--na-control-height-md, 36px);
  padding: 0 var(--na-space-4, 16px);
}

.na-chat__footnote {
  margin: 0;
  font-size: 12px;
  color: var(--na-color-text-muted, #9ca3af);
}
</style>
