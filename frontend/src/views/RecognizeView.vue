<script setup>
/**
 * 图片识别页（原型板块 5）
 *
 * 数据来源：
 *   - `POST /recognitions`              multipart 上传（字段名 `file`）
 *   - `POST /recognitions/{id}/confirm` 确认候选食品
 *
 * 契约硬约束：
 *   1. `candidates[]` **不含任何营养数值字段**，因此本页候选列表只展示
 *      名称 / 置信度 / 封面图，绝不展示或补算营养数据；
 *   2. `status === 'NEED_MANUAL_SEARCH'`（或候选都不合适）时，必须提供手动搜索入口；
 *   3. 图片格式与大小只做前端预校验（体验优化），真正的校验在后端（含魔数校验），
 *      前端不假装已经做过安全校验。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import UploadDropzone from '../components/UploadDropzone.vue'
import ConfidenceBadge from '../components/ConfidenceBadge.vue'
import FoodImage from '../components/FoodImage.vue'
import EmptyState from '../components/EmptyState.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import NavIcon from '../components/NavIcon.vue'
import { confirmRecognition, recognizeImage } from '../api/recognitions'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { useChatStore } from '../stores/chat'
import { formatFileSize } from '../utils/format'

const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()
const chatStore = useChatStore()

const file = ref(null)
const previewUrl = ref('')
const uploading = ref(false)
const confirming = ref(false)
const result = ref(null)
const isDemoData = ref(false)
const failed = ref(false)

/** 候选列表（契约保证不含营养字段） */
const candidates = computed(() => {
  if (!result.value || !Array.isArray(result.value.candidates)) return []
  return result.value.candidates
})

/** 是否需要手动搜索（无候选，或后端明确要求） */
const needManualSearch = computed(() => {
  if (!result.value) return false
  if (result.value.status === 'NEED_MANUAL_SEARCH') return true
  return candidates.value.length === 0
})

/** 最优候选（用于"识别结果"卡片） */
const bestCandidate = computed(() => {
  if (candidates.value.length === 0) return null
  return candidates.value[0]
})

/** 状态文案 */
const statusText = computed(() => {
  if (!result.value) return ''
  const map = {
    SUCCESS: '识别成功',
    LOW_CONFIDENCE: '识别置信度较低',
    NEED_MANUAL_SEARCH: '未能识别，请手动搜索',
    FAILED: '识别失败',
    PENDING: '识别处理中',
  }
  return map[result.value.status] || result.value.status || ''
})

/** 游客额度提示（数字来自后端 capabilities） */
const guestQuotaText = computed(() => {
  const capabilities = chatStore.capabilities
  if (!capabilities || authStore.isLoggedIn) return ''
  const quota = capabilities.guestRecognizeQuota
  if (quota === undefined || quota === null) return ''
  return `游客模式每天可试用 ${quota} 次图片识别，登录后额度更高。`
})

/**
 * 校验失败时的回调（由 UploadDropzone 触发）。
 * @param {{message: string, code: number}} payload
 */
function handleInvalid(payload) {
  toastStore.error(payload && payload.message ? payload.message : '图片不符合要求')
}

/** 上传并识别 */
async function startRecognize() {
  if (!file.value || uploading.value) return

  uploading.value = true
  failed.value = false
  result.value = null

  const { data, isDemoData: demo, error } = await recognizeImage(file.value)
  uploading.value = false

  if (error) {
    failed.value = true
    return
  }
  isDemoData.value = demo
  result.value = data
}

/**
 * 确认识别结果。
 * @param {number|string} foodId 用户选中的食品 id
 */
async function confirm(foodId) {
  if (!result.value || confirming.value) return
  confirming.value = true
  try {
    const updated = await confirmRecognition(result.value.recognitionId, foodId)
    result.value = updated || result.value
    toastStore.success('已确认，正在打开食品详情')
    router.push(`/food/${foodId}`)
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    confirming.value = false
  }
}

/** 重新上传：清空当前结果 */
function resetAll() {
  file.value = null
  previewUrl.value = ''
  result.value = null
  failed.value = false
  isDemoData.value = false
}

/** 跳手动搜索，关键词用文件名去掉扩展名作为初始建议 */
function goManualSearch() {
  const raw = file.value && file.value.name ? file.value.name.replace(/\.[^.]+$/, '') : ''
  router.push({ path: '/search', query: raw ? { q: raw } : {} })
}

// 选择文件后生成本地预览（仅用于展示，不读取内容做校验）
watch(file, (next) => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
  if (next) {
    previewUrl.value = URL.createObjectURL(next)
  }
})

onMounted(async () => {
  // 游客额度提示需要能力信息
  if (!chatStore.capabilities) {
    await chatStore.loadCapabilities()
  }
})
</script>

<template>
  <div class="na-recognize">
    <header class="na-recognize__head">
      <h2 class="na-recognize__title">图片识别</h2>
      <p class="na-recognize__desc">
        上传一张食品图片，AI 会给出候选食品与相似度；确认后再查看营养数据。
      </p>
    </header>

    <p v-if="guestQuotaText" class="na-recognize__quota">
      {{ guestQuotaText }}
      <RouterLink to="/login">去登录</RouterLink>
    </p>

    <NoticeBanner
      v-if="isDemoData"
      variant="warning"
      title="演示数据"
      message="后端接口当前不可用，以下识别结果为开发模式下的内联演示数据，不是真实识别结果，也不包含任何营养数据。"
    />

    <div class="na-recognize__layout">
      <!-- 左：上传区 -->
      <section class="na-card na-recognize__upload">
        <UploadDropzone v-model:file="file" :disabled="uploading" @invalid="handleInvalid" />

        <div v-if="file" class="na-recognize__file">
          <img v-if="previewUrl" class="na-recognize__preview" :src="previewUrl" :alt="file.name" />
          <div class="na-recognize__file-meta">
            <span class="na-recognize__file-name">{{ file.name }}</span>
            <span class="na-recognize__file-size">{{ formatFileSize(file.size) }}</span>
          </div>
        </div>

        <div class="na-recognize__upload-actions">
          <button
            type="button"
            class="na-btn na-btn--primary"
            :disabled="!file || uploading"
            @click="startRecognize"
          >
            {{ uploading ? '识别中…' : '开始识别' }}
          </button>
          <button v-if="file || result" type="button" class="na-btn na-btn--ghost" :disabled="uploading" @click="resetAll">
            重新上传
          </button>
        </div>

        <p class="na-recognize__tip">
          图片格式与大小会在上传前做一次预校验，后端仍会做完整校验（含文件内容校验），
          前端不代替后端做安全判定。
        </p>
      </section>

      <!-- 右：识别结果 -->
      <section class="na-card na-recognize__result">
        <h3 class="na-recognize__section-title">识别结果</h3>

        <div v-if="uploading" class="na-recognize__loading">
          <span class="na-skeleton na-recognize__loading-bar"></span>
          <p>正在识别图片，请稍候…</p>
        </div>

        <EmptyState
          v-else-if="!result"
          icon="image"
          :title="failed ? '识别失败' : '还没有识别结果'"
          :description="
            failed
              ? '可能是图片不清晰、服务不可用或已超出试用次数，可以重新上传或改用手动搜索。'
              : '上传一张清晰的食品图片，然后点击「开始识别」。'
          "
          compact
        >
          <button type="button" class="na-chip" @click="goManualSearch">直接手动搜索食品</button>
        </EmptyState>

        <template v-else>
          <!-- 识别结果卡片：仅展示名称 / 相似度 / 分类，不含营养数值 -->
          <div class="na-recognize__card">
            <FoodImage
              :code="bestCandidate ? bestCandidate.foodCode : ''"
              :name="bestCandidate ? bestCandidate.foodName : ''"
              :cover-image-url="bestCandidate ? bestCandidate.coverImageUrl : result.imageUrl"
              height="120px"
              width="120px"
            />
            <div class="na-recognize__card-meta">
              <p class="na-recognize__card-name">
                {{ bestCandidate ? bestCandidate.foodName : '未识别出食品' }}
              </p>
              <ConfidenceBadge
                v-if="bestCandidate"
                :confidence="bestCandidate.confidence"
                prefix="相似度"
              />
              <p class="na-recognize__status">{{ statusText }}</p>
              <p v-if="result.notice" class="na-recognize__notice">{{ result.notice }}</p>
              <p v-if="result.failureReason" class="na-recognize__notice">{{ result.failureReason }}</p>
            </div>
          </div>

          <div class="na-recognize__card-actions">
            <button
              v-if="bestCandidate"
              type="button"
              class="na-btn na-btn--primary"
              :disabled="confirming"
              @click="confirm(bestCandidate.foodId)"
            >
              {{ confirming ? '确认中…' : '确认识别结果' }}
            </button>
            <button type="button" class="na-recognize__link" @click="resetAll">重新上传</button>
          </div>

          <!-- 候选列表：不得展示任何营养数值 -->
          <h4 class="na-recognize__sub-title">候选食品（{{ candidates.length }}）</h4>
          <ul v-if="candidates.length > 0" class="na-recognize__candidates">
            <li v-for="candidate in candidates" :key="candidate.foodId" class="na-recognize__candidate">
              <FoodImage
                :code="candidate.foodCode"
                :name="candidate.foodName"
                :cover-image-url="candidate.coverImageUrl"
                height="56px"
                width="56px"
              />
              <div class="na-recognize__candidate-meta">
                <span class="na-recognize__candidate-name">{{ candidate.foodName }}</span>
                <span v-if="candidate.reason" class="na-recognize__candidate-reason">{{ candidate.reason }}</span>
              </div>
              <ConfidenceBadge :confidence="candidate.confidence" />
              <button
                type="button"
                class="na-btn na-btn--ghost"
                :disabled="confirming"
                @click="confirm(candidate.foodId)"
              >
                选择
              </button>
            </li>
          </ul>
          <p v-else class="na-recognize__candidate-empty">暂无候选食品。</p>

          <p class="na-recognize__constraint">
            按接口约定，候选结果不包含任何营养数值，营养数据需在确认食品后另行查询。
          </p>

          <!-- 手动搜索入口：NEED_MANUAL_SEARCH 或候选不合适时必须有 -->
          <div v-if="needManualSearch" class="na-recognize__manual">
            <NavIcon name="search" :size="18" />
            <span>没有合适的候选？</span>
            <button type="button" class="na-btn na-btn--primary" @click="goManualSearch">手动搜索食品</button>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.na-recognize {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-recognize__title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.na-recognize__desc,
.na-recognize__quota {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-recognize__quota a {
  color: var(--na-color-primary-600, #2563eb);
}

.na-recognize__layout {
  display: grid;
  grid-template-columns: minmax(0, 380px) minmax(0, 1fr);
  gap: var(--na-space-4, 16px);
  align-items: start;
}

.na-recognize__upload,
.na-recognize__result {
  padding: var(--na-space-4, 16px);
  display: flex;
  flex-direction: column;
  gap: var(--na-space-3, 12px);
}

.na-recognize__file {
  display: flex;
  gap: var(--na-space-3, 12px);
  align-items: center;
}

.na-recognize__preview {
  width: 96px;
  height: 96px;
  object-fit: cover;
  border-radius: var(--na-radius-md, 8px);
  border: 1px solid var(--na-color-border, #e5e7eb);
}

.na-recognize__file-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.na-recognize__file-name {
  font-size: 13px;
  word-break: break-all;
}

.na-recognize__file-size {
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-recognize__upload-actions {
  display: flex;
  gap: var(--na-space-2, 8px);
}

.na-recognize__tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--na-color-text-muted, #9ca3af);
}

.na-recognize__section-title {
  margin: 0;
  font-size: var(--na-font-size-lg, 17px);
  font-weight: 600;
}

.na-recognize__loading {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
}

.na-recognize__loading-bar {
  display: block;
  height: 96px;
}

.na-recognize__card {
  display: flex;
  gap: var(--na-space-4, 16px);
  align-items: center;
  padding: var(--na-space-3, 12px);
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-md, 8px);
}

.na-recognize__card-meta {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
  min-width: 0;
}

.na-recognize__card-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.na-recognize__status {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-recognize__notice {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-warning, #f59e0b);
}

.na-recognize__card-actions {
  display: flex;
  align-items: center;
  gap: var(--na-space-3, 12px);
}

.na-recognize__link {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  color: var(--na-color-primary-600, #2563eb);
  cursor: pointer;
}

.na-recognize__sub-title {
  margin: var(--na-space-3, 12px) 0 0;
  font-size: 14px;
  font-weight: 600;
}

.na-recognize__candidates {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
}

.na-recognize__candidate {
  display: flex;
  align-items: center;
  gap: var(--na-space-3, 12px);
  padding: var(--na-space-2, 8px) var(--na-space-3, 12px);
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-md, 8px);
}

.na-recognize__candidate-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.na-recognize__candidate-name {
  font-size: 14px;
  font-weight: 500;
}

.na-recognize__candidate-reason {
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-recognize__candidate-empty,
.na-recognize__constraint {
  margin: 0;
  font-size: 12px;
  color: var(--na-color-text-muted, #9ca3af);
}

.na-recognize__manual {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
  padding: var(--na-space-3, 12px);
  border: 1px dashed var(--na-color-border, #d1d5db);
  border-radius: var(--na-radius-md, 8px);
  font-size: 13px;
}

@media (max-width: 900px) {
  .na-recognize__layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
