<script setup>
/**
 * 后台 · 内容审核
 *
 * 接口（契约 4.7）：
 *   - `GET  /admin/foods?reviewStatus=PENDING`  待审核列表（复用食品列表接口的筛选能力）
 *   - `POST /admin/foods/{id}/review`           审核通过 / 驳回
 *   - `POST /admin/foods/{id}/publish`          发布
 *
 * 说明：契约中**没有**独立的 `/admin/review` 接口，审核状态是食品资源上的字段，
 * 因此本页按 `reviewStatus` 过滤 `/admin/foods` 实现，不虚构接口路径。
 */
import { onMounted, ref } from 'vue'

import EmptyState from '../../components/EmptyState.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import NoticeBanner from '../../components/NoticeBanner.vue'
import FoodImage from '../../components/FoodImage.vue'
import { getAdminFoods, publishFood, reviewFood } from '../../api/admin'
import { useToastStore } from '../../stores/toast'
import { formatDateTime, textOrPlaceholder } from '../../utils/format'

const toastStore = useToastStore()

/** 审核状态筛选：默认只看待审核 */
const reviewStatus = ref('PENDING')
const list = ref([])
const loading = ref(false)
const loadFailed = ref(false)
const actingId = ref(null)

/** 驳回理由（按食品 id 暂存） */
const rejectReasons = ref({})

const REVIEW_TABS = [
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: '', label: '全部' },
]

/** 加载待审核列表 */
async function loadList() {
  loading.value = true
  loadFailed.value = false
  const { data, error } = await getAdminFoods({
    reviewStatus: reviewStatus.value,
    page: 1,
    pageSize: 50,
  })
  loading.value = false
  loadFailed.value = Boolean(error)
  list.value = error || !data || !Array.isArray(data.list) ? [] : data.list
}

/**
 * 切换筛选标签。
 * @param {string} value 审核状态
 */
function switchTab(value) {
  reviewStatus.value = value
  loadList()
}

/**
 * 审核通过。
 * @param {object} row 列表行
 */
async function approve(row) {
  actingId.value = row.id
  try {
    await reviewFood(row.id, { approved: true, comment: '内容审核通过' })
    toastStore.success('已审核通过')
    await loadList()
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    actingId.value = null
  }
}

/**
 * 驳回。
 * @param {object} row 列表行
 */
async function reject(row) {
  const reason = (rejectReasons.value[row.id] || '').trim()
  if (!reason) {
    toastStore.warning('请先填写驳回理由')
    return
  }
  actingId.value = row.id
  try {
    await reviewFood(row.id, { approved: false, comment: reason })
    toastStore.success('已驳回')
    rejectReasons.value[row.id] = ''
    await loadList()
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    actingId.value = null
  }
}

/**
 * 发布。
 * @param {object} row 列表行
 */
async function publish(row) {
  actingId.value = row.id
  try {
    await publishFood(row.id)
    toastStore.success('已发布')
    await loadList()
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    actingId.value = null
  }
}

onMounted(loadList)
</script>

<template>
  <div class="na-admin-review">
    <header class="na-admin-page__head">
      <div>
        <h2 class="na-admin-page__title">内容审核</h2>
        <p class="na-admin-page__desc">
          {{ loading ? '加载中…' : `当前筛选下共 ${list.length} 条` }}
        </p>
      </div>
    </header>

    <NoticeBanner
      v-if="loadFailed"
      variant="warning"
      title="接口不可用"
      message="无法获取审核列表，请确认后端服务已启动（默认 8080 端口）且当前账号具备管理权限。"
    />

    <div class="na-admin-review__tabs na-tabs" role="tablist">
      <button
        v-for="tab in REVIEW_TABS"
        :key="tab.value"
        type="button"
        role="tab"
        class="na-tab"
        :class="{ 'is-active': reviewStatus === tab.value }"
        :aria-selected="reviewStatus === tab.value"
        @click="switchTab(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <SkeletonBlock v-if="loading" :rows="5" height="20px" />

    <div v-else-if="list.length > 0" class="na-admin-review__list">
      <article v-for="row in list" :key="row.id" class="na-card na-admin-review__item">
        <FoodImage
          :code="row.code"
          :name="row.name"
          :cover-image-url="row.coverImageUrl"
          width="72px"
          height="54px"
        />

        <div class="na-admin-review__meta">
          <h3 class="na-admin-review__name">{{ textOrPlaceholder(row.name) }}</h3>
          <p class="na-admin-review__sub">
            {{ textOrPlaceholder(row.categoryName) }} · 编码 {{ textOrPlaceholder(row.code) }} · 更新于
            {{ formatDateTime(row.updatedAt) }}
          </p>
          <p class="na-admin-review__sub">
            营养成分：已填 {{ textOrPlaceholder(row.nutrientFilledCount) }} 项 / 缺失
            {{ textOrPlaceholder(row.nutrientMissingCount) }} 项
          </p>
          <p class="na-admin-review__sub">
            状态：{{ textOrPlaceholder(row.reviewStatus) }} ·
            {{ textOrPlaceholder(row.publishStatus) }}
          </p>
        </div>

        <div class="na-admin-review__actions">
          <button
            v-if="row.reviewStatus !== 'APPROVED'"
            type="button"
            class="na-btn na-btn--primary"
            :disabled="actingId === row.id"
            @click="approve(row)"
          >
            审核通过
          </button>

          <div v-if="row.reviewStatus !== 'REJECTED'" class="na-admin-review__reject">
            <label class="na-sr-only" :for="`na-reject-reason-${row.id}`">驳回理由</label>
            <input
              :id="`na-reject-reason-${row.id}`"
              v-model="rejectReasons[row.id]"
              class="na-input"
              type="text"
              placeholder="驳回理由（必填）"
            />
            <button type="button" class="na-btn na-btn--ghost" :disabled="actingId === row.id" @click="reject(row)">
              驳回
            </button>
          </div>

          <button
            v-if="row.publishStatus !== 'PUBLISHED'"
            type="button"
            class="na-btn na-btn--ghost"
            :disabled="actingId === row.id"
            @click="publish(row)"
          >
            发布
          </button>
        </div>
      </article>
    </div>

    <EmptyState
      v-else
      icon="check"
      :title="loadFailed ? '暂时无法获取审核列表' : '这里没有需要处理的条目'"
      :description="
        loadFailed
          ? '请确认后端服务已启动（默认 8080 端口），且当前账号具备管理权限。'
          : '可以切换上方标签查看其他审核状态的条目。'
      "
    />
  </div>
</template>

<style scoped>
.na-admin-review {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-admin-review__list {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-3, 12px);
}

.na-admin-review__item {
  display: flex;
  align-items: flex-start;
  gap: var(--na-space-3, 12px);
}

.na-admin-review__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.na-admin-review__name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.na-admin-review__sub {
  margin: 0;
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-admin-review__actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--na-space-2, 8px);
  min-width: 220px;
}

.na-admin-review__reject {
  display: flex;
  gap: var(--na-space-2, 8px);
  width: 100%;
}
</style>
