<script setup>
/**
 * 分类浏览页（`/categories`）
 *
 * 数据来源：`GET /categories`
 * 展示 10 个分类徽章卡片，点击进入 `/category/:slug`。
 * 分类图标由 `utils/assets.js` 的 `categoryImage(slug)` 解析，
 * 素材缺失时回落到内联图标（不影响页面可用性）。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import SkeletonBlock from '../components/SkeletonBlock.vue'
import EmptyState from '../components/EmptyState.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import NavIcon from '../components/NavIcon.vue'
import { getCategories } from '../api/foods'
import { categoryImage } from '../utils/assets'

const router = useRouter()

const categories = ref([])
const loading = ref(true)
const isDemoData = ref(false)
const loadFailed = ref(false)

/** 拉取分类列表 */
async function loadCategories() {
  loading.value = true
  loadFailed.value = false
  // 有演示数据兜底，接口不可用时不弹红色错误提示，只由页面内黄色横幅统一说明
  const { data, isDemoData: demo, error } = await getCategories({ silent: true })
  loading.value = false
  isDemoData.value = demo
  loadFailed.value = Boolean(error)
  categories.value = Array.isArray(data) ? data : []
}

/**
 * 进入分类详情。
 * @param {object} category 分类
 */
function openCategory(category) {
  router.push(`/category/${encodeURIComponent(category.slug)}`)
}

onMounted(loadCategories)
</script>

<template>
  <div class="na-categories">
    <header class="na-categories__head">
      <h2 class="na-categories__title">分类浏览</h2>
      <p class="na-categories__desc">按食材分类浏览食品，快速找到蔬菜、水果、肉蛋奶等常见食材。</p>
    </header>

    <NoticeBanner
      v-if="isDemoData"
      variant="warning"
      title="演示数据"
      message="后端接口当前不可用，以下分类为开发模式下的内联演示数据。"
    />

    <div v-if="loading" class="na-categories__grid">
      <div v-for="index in 10" :key="index" class="na-card na-categories__skeleton">
        <SkeletonBlock :rows="1" height="56px" circle :widths="['56px']" />
        <SkeletonBlock :rows="1" height="14px" :widths="['60%']" />
      </div>
    </div>

    <div v-else-if="categories.length > 0" class="na-categories__grid">
      <button
        v-for="category in categories"
        :key="category.id || category.slug"
        type="button"
        class="na-card na-categories__item"
        @click="openCategory(category)"
      >
        <img
          v-if="categoryImage(category.slug)"
          class="na-categories__icon"
          :src="categoryImage(category.slug)"
          :alt="`${category.name}分类图标`"
          width="56"
          height="56"
        />
        <span v-else class="na-categories__icon na-categories__icon--fallback">
          <NavIcon name="category" :size="26" />
        </span>
        <span class="na-categories__name">{{ category.name }}</span>
        <span v-if="category.foodCount !== undefined && category.foodCount !== null" class="na-categories__count">
          {{ category.foodCount }} 种
        </span>
        <span v-else class="na-categories__count na-categories__count--muted">数量暂无数据</span>
      </button>
    </div>

    <EmptyState
      v-else
      icon="category"
      :title="loadFailed ? '暂时无法获取分类' : '还没有配置分类'"
      :description="
        loadFailed
          ? '请确认后端服务已启动（默认 8080 端口），或稍后重试。'
          : '数据库中暂无分类数据，可先到「食品搜索」按关键词查找。'
      "
      action-text="去食品搜索"
      @action="router.push('/search')"
    />
  </div>
</template>

<style scoped>
.na-categories {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-categories__title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.na-categories__desc {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-categories__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--na-space-4, 16px);
}

.na-categories__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--na-space-2, 8px);
  padding: var(--na-space-4, 16px);
  border: 1px solid var(--na-color-border, #e5e7eb);
  background: var(--na-color-surface, #ffffff);
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.na-categories__item:hover {
  border-color: var(--na-color-primary-500, #3b82f6);
  transform: translateY(-2px);
}

.na-categories__icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
}

.na-categories__icon--fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--na-color-primary-50, #eff6ff);
  color: var(--na-color-primary-600, #2563eb);
}

.na-categories__name {
  font-size: var(--na-font-size-sm, 14px);
  font-weight: 500;
  color: var(--na-color-text, #111827);
}

.na-categories__count {
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-categories__count--muted {
  font-style: italic;
}

.na-categories__skeleton {
  padding: var(--na-space-4, 16px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--na-space-2, 8px);
}
</style>
