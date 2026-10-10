<script setup>
/**
 * 食品搜索页（原型板块 2 上半）
 *
 * 数据来源：`GET /foods/search`（keyword / categoryId / page / pageSize / sort）
 * 分类 chips 数据来源：`GET /categories`
 *
 * 说明：搜索结果里若某食品没有能量数据，卡片显示「暂无数据」，
 * 绝不用 0 顶替（能量字段只读接口返回值）。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import SearchBox from '../components/SearchBox.vue'
import CategoryChips from '../components/CategoryChips.vue'
import FoodCard from '../components/FoodCard.vue'
import SkeletonBlock from '../components/SkeletonBlock.vue'
import EmptyState from '../components/EmptyState.vue'
import PaginationBar from '../components/PaginationBar.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import { getCategories, searchFoods } from '../api/foods'
import { useFavoritesStore } from '../stores/favorites'
import { pageMetaOf } from '../utils/dev'

const route = useRoute()
const router = useRouter()
const favoritesStore = useFavoritesStore()

const keyword = ref(String(route.query.q || ''))
const categorySlug = ref(String(route.query.category || ''))
const sort = ref(String(route.query.sort || 'relevance'))
const page = ref(1)
const pageSize = 20

const categories = ref([])
const list = ref([])
const pagination = ref({ page: 1, pageSize, total: 0, totalPages: 0 })
const loading = ref(false)
const isDemoData = ref(false)
const loadFailed = ref(false)

/** 排序选项：排序逻辑在后端，前端只负责传参 */
const sortOptions = [
  { value: 'relevance', label: '相关度' },
  { value: 'name', label: '名称' },
  { value: 'energy', label: '能量' },
]

/** 当前选中的分类对象（用于把 slug 换算成 categoryId 传给接口） */
const currentCategory = computed(() =>
  categories.value.find((item) => String(item.slug) === String(categorySlug.value)),
)

/** 结果统计文案 */
const resultSummary = computed(() => {
  if (loading.value) return '正在搜索…'
  if (pagination.value.total > 0) return `共找到 ${pagination.value.total} 条结果`
  return keyword.value ? `没有找到与「${keyword.value}」匹配的食品` : '请输入关键词开始搜索'
})

/** 分类 chips 数据 */
async function loadCategories() {
  const { data, error } = await getCategories({ silent: true })
  if (error) return
  categories.value = Array.isArray(data) ? data : []
}

/** 执行搜索 */
async function runSearch() {
  loading.value = true
  loadFailed.value = false
  // 搜索有演示数据兜底，接口不可用时不弹红色错误提示，
  // 只由页面内的黄色「演示数据」横幅统一说明（避免自相矛盾的两条提示）。
  const { data, isDemoData: demo, error } = await searchFoods(
    {
      keyword: keyword.value.trim(),
      categoryId: currentCategory.value ? currentCategory.value.id : undefined,
      page: page.value,
      pageSize,
      sort: sort.value,
    },
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

/** 把当前筛选条件同步到地址栏（便于分享与前进后退） */
function syncQuery() {
  router.replace({
    path: '/search',
    query: {
      ...(keyword.value.trim() ? { q: keyword.value.trim() } : {}),
      ...(categorySlug.value ? { category: categorySlug.value } : {}),
      ...(sort.value !== 'relevance' ? { sort: sort.value } : {}),
    },
  })
}

/**
 * 提交关键词搜索。
 * @param {string} text 关键词
 */
function handleSubmit(text) {
  keyword.value = text
  page.value = 1
  syncQuery()
  runSearch()
}

/** 切换分类 */
function handleCategoryChange() {
  page.value = 1
  syncQuery()
  runSearch()
}

/** 切换排序 */
function handleSortChange() {
  page.value = 1
  syncQuery()
  runSearch()
}

/** 翻页 */
function handlePageChange(next) {
  page.value = next
  runSearch()
}

/** 清空筛选条件 */
function resetFilters() {
  keyword.value = ''
  categorySlug.value = ''
  sort.value = 'relevance'
  page.value = 1
  syncQuery()
  runSearch()
}

/** 切换收藏 */
async function handleToggleFavorite(food) {
  await favoritesStore.toggleFavorite(food.id)
}

// 地址栏参数变化（浏览器前进/后退、外链跳入）时重新搜索
watch(
  () => [route.query.q, route.query.category, route.query.sort],
  () => {
    const nextKeyword = String(route.query.q || '')
    const nextCategory = String(route.query.category || '')
    const nextSort = String(route.query.sort || 'relevance')
    if (nextKeyword === keyword.value && nextCategory === categorySlug.value && nextSort === sort.value) {
      return
    }
    keyword.value = nextKeyword
    categorySlug.value = nextCategory
    sort.value = nextSort
    page.value = 1
    runSearch()
  },
)

onMounted(async () => {
  await loadCategories()
  await runSearch()
})
</script>

<template>
  <div class="na-search">
    <header class="na-search__head">
      <h2 class="na-search__title">食品搜索</h2>
      <p class="na-search__desc">按名称、别名或拼音搜索食品，查看每 100g 的营养成分。</p>
    </header>

    <SearchBox v-model="keyword" size="lg" @submit="handleSubmit" @submit-image="router.push('/recognize')" />

    <NoticeBanner
      v-if="isDemoData"
      variant="warning"
      title="演示数据"
      message="后端接口当前不可用，以下搜索结果为开发模式下的内联演示数据，不代表真实营养数据。"
    />

    <!-- 分类筛选 -->
    <section class="na-search__filters">
      <CategoryChips
        v-model="categorySlug"
        :categories="categories"
        allow-all
        all-value=""
        @change="handleCategoryChange"
      />

      <div class="na-search__sort">
        <label class="na-sr-only" for="na-search-sort">排序方式</label>
        <select id="na-search-sort" class="na-input na-search__select" :value="sort" @change="sort = $event.target.value; handleSortChange()">
          <option v-for="option in sortOptions" :key="option.value" :value="option.value">
            排序：{{ option.label }}
          </option>
        </select>
      </div>
    </section>

    <!-- 结果统计 -->
    <div class="na-search__summary">
      <span>{{ resultSummary }}</span>
      <button
        v-if="keyword || categorySlug || sort !== 'relevance'"
        type="button"
        class="na-search__reset"
        @click="resetFilters"
      >
        清空筛选
      </button>
    </div>

    <!-- 加载骨架 -->
    <div v-if="loading" class="na-search__grid">
      <div v-for="index in 8" :key="index" class="na-card na-search__skeleton">
        <SkeletonBlock :rows="1" height="110px" :widths="['100%']" />
        <SkeletonBlock :rows="2" height="14px" />
      </div>
    </div>

    <!-- 结果网格 -->
    <div v-else-if="list.length > 0" class="na-search__grid">
      <FoodCard
        v-for="food in list"
        :key="food.id"
        :food="food"
        :favorite="favoritesStore.isFavorite(food.id)"
        @toggle-favorite="handleToggleFavorite"
      />
    </div>

    <!-- 空态 -->
    <EmptyState
      v-else
      icon="search"
      :title="loadFailed ? '搜索失败' : '没有找到匹配的食品'"
      :description="
        loadFailed
          ? '请确认后端服务已启动（默认 8080 端口），或稍后重试。'
          : '可以试试更短的关键词、换个分类，或者上传一张图片让 AI 帮你识别。'
      "
      action-text="上传图片识别"
      @action="router.push('/recognize')"
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
.na-search {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-search__title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.na-search__desc {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-search__filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--na-space-4, 16px);
  flex-wrap: wrap;
}

.na-search__select {
  min-width: 132px;
}

.na-search__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-search__reset {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  color: var(--na-color-primary-600, #2563eb);
  cursor: pointer;
}

.na-search__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: var(--na-space-4, 16px);
}

.na-search__skeleton {
  padding: var(--na-space-3, 12px);
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
}
</style>
