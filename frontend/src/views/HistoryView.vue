<script setup>
/**
 * 浏览历史
 *
 * 数据来源：本地 localStorage（`stores/history.js`）。
 * 契约中没有"浏览历史"接口（FR-008 的历史指 AI 会话历史，见聊天页），
 * 因此这里只展示本机记录，不伪造服务端数据。
 *
 * 历史记录里保存的是打开详情页时的快照字段（名称 / 分类 / 封面），
 * 不包含也不展示任何营养数值。
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import FoodImage from '../components/FoodImage.vue'
import EmptyState from '../components/EmptyState.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { useHistoryStore } from '../stores/history'
import { formatRelativeTime, textOrPlaceholder } from '../utils/format'

const router = useRouter()
const historyStore = useHistoryStore()

const clearDialogOpen = ref(false)

/** 历史记录（已经是新的在前） */
const records = computed(() => historyStore.records)

/**
 * 删除单条历史。
 * @param {number} foodId 食品 id
 */
function removeRecord(foodId) {
  historyStore.removeRecord(foodId)
}

/** 清空全部历史 */
function clearAll() {
  historyStore.clear()
  clearDialogOpen.value = false
}

/** 直接进入对应食品详情 */
function openFood(foodId) {
  router.push(`/food/${foodId}`)
}
</script>

<template>
  <div class="na-history">
    <header class="na-history__head">
      <div>
        <h2 class="na-history__title">浏览历史</h2>
        <p class="na-history__desc">共 {{ historyStore.count }} 条记录，仅保存在本机浏览器中。</p>
      </div>
      <div class="na-history__actions">
        <button type="button" class="na-btn na-btn--ghost" :disabled="records.length === 0" @click="clearDialogOpen = true">
          清空历史
        </button>
        <RouterLink class="na-btn na-btn--ghost" to="/search">去搜索食品</RouterLink>
      </div>
    </header>

    <ul v-if="records.length > 0" class="na-history__list">
      <li v-for="record in records" :key="record.foodId" class="na-card na-history__item">
        <FoodImage
          :code="record.code"
          :name="record.name"
          :cover-image-url="record.coverImageUrl"
          width="64px"
          height="64px"
        />
        <div class="na-history__meta">
          <button type="button" class="na-history__name" @click="openFood(record.foodId)">
            {{ textOrPlaceholder(record.name) }}
          </button>
          <span class="na-history__sub">
            {{ textOrPlaceholder(record.categoryName) }} · {{ formatRelativeTime(record.viewedAt) }}
          </span>
          <span class="na-history__sub">食品编号：{{ record.foodId }}</span>
        </div>
        <div class="na-history__item-actions">
          <button type="button" class="na-btn na-btn--ghost" @click="openFood(record.foodId)">查看详情</button>
          <button type="button" class="na-history__link" @click="removeRecord(record.foodId)">删除</button>
        </div>
      </li>
    </ul>

    <EmptyState
      v-else
      icon="clock"
      title="还没有浏览记录"
      description="打开任意食品详情页后，这里会记录你最近查看过的食品。"
      action-text="去搜索食品"
      @action="router.push('/search')"
    />

    <ConfirmDialog
      :open="clearDialogOpen"
      title="清空浏览历史"
      message="确认清空本机保存的全部浏览历史吗？该操作不可撤销。"
      confirm-text="确认清空"
      danger
      @confirm="clearAll"
      @cancel="clearDialogOpen = false"
    />
  </div>
</template>

<style scoped>
.na-history {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-history__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--na-space-3, 12px);
  flex-wrap: wrap;
}

.na-history__title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.na-history__desc {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-history__actions {
  display: flex;
  gap: var(--na-space-2, 8px);
}

.na-history__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--na-space-3, 12px);
}

.na-history__item {
  display: flex;
  align-items: center;
  gap: var(--na-space-3, 12px);
  padding: var(--na-space-3, 12px);
}

.na-history__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.na-history__name {
  border: none;
  background: none;
  padding: 0;
  text-align: left;
  font-size: 15px;
  font-weight: 500;
  color: var(--na-color-text, #111827);
  cursor: pointer;
}

.na-history__name:hover {
  color: var(--na-color-primary-600, #2563eb);
}

.na-history__sub {
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-history__item-actions {
  display: flex;
  align-items: center;
  gap: var(--na-space-3, 12px);
}

.na-history__link {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  color: var(--na-color-danger, #ef4444);
  cursor: pointer;
}
</style>
