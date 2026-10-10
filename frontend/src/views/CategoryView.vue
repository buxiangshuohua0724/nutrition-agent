<script setup>
/**
 * 分类详情页（原型板块 2 下半）
 *
 * 路由：`/category/:slug`
 * 数据来源：`GET /categories/{slug}/foods`（page / pageSize）
 *
 * 分类名称优先取接口返回（`getCategories` 里匹配 slug），
 * 匹配不到时用 slug 原文兜底显示，绝不编造分类名称。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import FoodCard from '../components/FoodCard.vue'
import SkeletonBlock from '../components/SkeletonBlock.vue'
import EmptyState from '../components/EmptyState.vue'
import PaginationBar from '../components/PaginationBar.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import NavIcon from '../components/NavIcon.vue'
import { getCategories, getCategoryFoods } from '../api/foods'
import { useFavoritesStore } from '../stores/favorites'
import { categoryImage } from '../utils/assets'
import { pageMetaOf } from '../utils/dev'

const route = useRoute()
const router = useRouter()
const favoritesStore = useFavoritesStore()

const slug = computed(() => String(route.params.slug || ''))
const categoryName = ref('')
const categories = ref([])
const list = ref([])
const pagination = ref({ page: 1, pageSize: 20, total: 0, totalPages: 0 })
const page = ref(1)
const pageSize = 24
const loading = ref(true)
const isDemoData = ref(false)
const loadFailed = ref(false)

/** 标题：优先接口给的分类名，否则用 slug 原文（不臆造中文名） */
const displayName = computed(() => categoryName.value || slug.value || '分类')
const categoryIcon = computed(() => categoryImage(slug.value))

/** 拉分类清单，只为拿到当前分类的中文名与图标 */
async function loadCategoryMeta() {
  const { data, error } = await getCategories({ silent: true })
  if (error) return
  categories.value = Array.isArray(data) ? data : []
  const matched = categories.value.find((item) => String(item.slug) === slug.value)
  categoryName.value = matched ? matched.name : ''
}

/** 拉分类下的食品 */
async function loadFoods() {
  if (!slug.value) {
    loading.value = false
    return
  }
  loading.value = true
  loadFailed.value = false
  // 有演示数据兜底，接口不可用时不弹红色错误提示，只由页面内黄色横幅统一说明
  const { data, isDemoData: demo, error } = await getCategoryFoods(
    slug.value,
    { page: page.value, pageSize },
    { silent: true },
  )
  loading.value = false
  isDemoData.value = demo
  loadFailed.value = Boolean(error)

  if (error) {
    list.value = []
    pagination.value = { page: page.value, pageSize, total: 0, totalPages: 0 }
    return
  }
  list.value = data && Array.isArray(data.list) ? data.list : []
  // 分页元信息统一走 pageMetaOf（只处理分页计数，营养数值严禁任何兜底）
  pagination.value = pageMetaOf(data, { page: page.value, pageSize })
}

/** 翻页 */
function handlePageChange(next) {
  page.value = next
  loadFoods()
}

/** 切换收藏 */
async function handleToggleFavorite(food) {
  await favoritesStore.toggleFavorite(food.id)
}

// slug 变化时（分类之间跳转）重置分页并重新加载
watch(slug, async () => {
  page.value = 1
  categoryName.value = ''
  await loadCategoryMeta()
  await loadFoods()
})

onMounted(async () => {
  await loadCategoryMeta()
  await loadFoods()
})
</script>

<template>
  <div class="na-category">
    <header class="na-category__head">
      <span v-if="categoryIcon" class="na-category__badge">
        <img :src="categoryIcon" :alt="`${displayName}分类图标`" width="44" height="44" />
      </span>
      <span v-else class="na-category__badge na-category__badge--fallback">
        <NavIcon name="category" :size="22" />
      </span>

      <div class="na-category__meta">
        <h2 class="na-category__title">{{ displayName }}</h2>
        <p class="na-category__desc">
          <template v-if="!loading && pagination.total > 0">该分类下共 {{ pagination.total }} 种食品</template>
          <template v-else-if="loading">正在加载该分类的食品…</template>
          <template v-else>该分类下暂无可展示的食品</template>
        </p>
      </div>

      <RouterLink class="na-btn na-btn--ghost" to="/categories">返回分类浏览</RouterLink>
    </header>

    <NoticeBanner
      v-if="isDemoData"
      variant="warning"
      title="演示数据"
      message="后端接口当前不可用，以下内容为开发模式下的内联演示数据。"
    />

    <div v-if="loading" class="na-category__grid">
      <div v-for="index in 8" :key="index" class="na-card na-category__skeleton">
        <SkeletonBlock :rows="1" height="110px" :widths="['100%']" />
        <SkeletonBlock :rows="2" height="14px" />
      </div>
    </div>

    <div v-else-if="list.length > 0" class="na-category__grid">
      <FoodCard
        v-for="food in list"
        :key="food.id"
        :food="food"
        :favorite="favoritesStore.isFavorite(food.id)"
        @toggle-favorite="handleToggleFavorite"
      />
    </div>

    <EmptyState
      v-else
      icon="category"
      :title="loadFailed ? '暂时无法获取该分类的食品' : '该分类下还没有食品'"
      :description="
        loadFailed
          ? '请确认后端服务已启动（默认 8080 端口），或稍后重试。'
          : '可以换个分类浏览，或直接搜索食品名称。'
      "
      action-text="去食品搜索"
      @action="router.push('/search')"
    />

    <PaginationBar
      v-if="!loading && list.length > 0"
      :page="pagination.page"
      :page-size="pagination.pageSize"
      :total="pagination.total"
      :total-pages="pagination.totalPages"
      @update:page="handlePageChange"
    />
  </div>
</template>

<style scoped>
.na-category {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-category__head {
  display: flex;
  align-items: center;
  gap: var(--na-space-3, 12px);
}

.na-category__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--na-color-primary-50, #eff6ff);
  color: var(--na-color-primary-600, #2563eb);
  flex: none;
}

.na-category__meta {
  flex: 1;
  min-width: 0;
}

.na-category__title {
  margin: 0 0 2px;
  font-size: 20px;
  font-weight: 600;
}

.na-category__desc {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-category__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--na-space-4, 16px);
}

.na-category__skeleton {
  padding: var(--na-space-3, 12px);
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
}

@media (max-width: 1080px) {
  .na-category__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .na-category__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
