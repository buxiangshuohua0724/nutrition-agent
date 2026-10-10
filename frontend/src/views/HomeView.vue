<script setup>
/**
 * 首页（原型板块 1）
 *
 * 结构：大搜索框 → 四个圆形入口 → 热门食品横向卡片区。
 * 数据来自 `GET /foods/featured`（最多 5 个）；接口失败时在开发模式下回退演示数据，
 * 页面顶部会明确显示「演示数据」标识。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import SearchBox from '../components/SearchBox.vue'
import FoodCard from '../components/FoodCard.vue'
import SkeletonBlock from '../components/SkeletonBlock.vue'
import EmptyState from '../components/EmptyState.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import NavIcon from '../components/NavIcon.vue'
import { getFeaturedFoods } from '../api/foods'
import { useFavoritesStore } from '../stores/favorites'
import { useHistoryStore } from '../stores/history'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const favoritesStore = useFavoritesStore()
const historyStore = useHistoryStore()
const authStore = useAuthStore()

const keyword = ref('')
const featured = ref([])
const loading = ref(true)
const isDemoData = ref(false)
const loadFailed = ref(false)

/** 四个圆形功能入口 */
const entries = [
  { label: '食品搜索', to: '/search', icon: 'search' },
  { label: '图片识别', to: '/recognize', icon: 'image' },
  { label: 'AI问答', to: '/chat', icon: 'chat' },
  { label: '我的健康', to: '/me', icon: 'health' },
]

/** 拉取热门食品 */
async function loadFeatured() {
  loading.value = true
  loadFailed.value = false
  // 首页有演示数据兜底，接口不可用时不弹红色错误提示（否则会和下方的
  // 黄色「演示数据」横幅同时出现，自相矛盾）。
  const { data, isDemoData: demo, error } = await getFeaturedFoods({ type: 'hot', limit: 5 }, { silent: true })
  isDemoData.value = demo
  loadFailed.value = Boolean(error)
  featured.value = Array.isArray(data) ? data : []
  loading.value = false
}

/**
 * 提交搜索。
 * @param {string} text 关键词
 */
function handleSearch(text) {
  router.push({ path: '/search', query: text ? { q: text } : {} })
}

/** 跳转图片识别 */
function goRecognize() {
  router.push('/recognize')
}

/**
 * 切换收藏。
 * @param {object} food 食品
 */
async function handleToggleFavorite(food) {
  const active = await favoritesStore.toggleFavorite(food.id)
  if (active) {
    historyStore.recordView(food)
  }
}

onMounted(loadFeatured)
</script>

<template>
  <div class="na-home">
    <!-- 板块 1-1：大搜索区 -->
    <section class="na-home__hero">
      <h2 class="na-home__headline">想知道你吃的食品有多少营养？</h2>
      <p class="na-home__subhead">
        查询食品营养成分、按重量折算摄入量，或直接上传一张图片让 AI 帮你识别。
      </p>

      <div class="na-home__search">
        <SearchBox v-model="keyword" size="lg" autofocus @submit="handleSearch" @submit-image="goRecognize" />
      </div>

      <p v-if="!authStore.isLoggedIn" class="na-home__guest-hint">
        当前为游客模式：AI 问答与图片识别每天有试用次数限制，登录后额度更高。
      </p>
    </section>

    <!-- 板块 1-2：四个圆形入口 -->
    <section class="na-home__entries" aria-label="功能入口">
      <RouterLink v-for="entry in entries" :key="entry.to" class="na-home__entry" :to="entry.to">
        <span class="na-home__entry-icon">
          <NavIcon :name="entry.icon" :size="26" />
        </span>
        <span class="na-home__entry-label">{{ entry.label }}</span>
      </RouterLink>
    </section>

    <!-- 演示数据标识（仅在开发模式回退时出现） -->
    <NoticeBanner
      v-if="isDemoData"
      variant="warning"
      icon="warning"
      title="演示数据"
      message="后端接口当前不可用，以下内容为开发模式下的内联演示数据，不代表真实营养数据。"
    />

    <!-- 板块 1-3：热门食品 -->
    <section class="na-home__featured">
      <header class="na-home__section-head">
        <h3 class="na-home__section-title">热门食品</h3>
        <RouterLink class="na-home__more" to="/search">查看全部</RouterLink>
      </header>

      <div v-if="loading" class="na-home__grid">
        <div v-for="index in 5" :key="index" class="na-card na-home__skeleton">
          <SkeletonBlock :rows="1" height="120px" :widths="['100%']" />
          <SkeletonBlock :rows="2" height="14px" />
        </div>
      </div>

      <div v-else-if="featured.length > 0" class="na-home__grid">
        <FoodCard
          v-for="food in featured"
          :key="food.id"
          :food="food"
          :favorite="favoritesStore.isFavorite(food.id)"
          @toggle-favorite="handleToggleFavorite"
        />
      </div>

      <EmptyState
        v-else
        icon="search"
        :title="loadFailed ? '暂时无法获取热门食品' : '还没有可展示的食品'"
        :description="
          loadFailed
            ? '请确认后端服务已启动（默认 8080 端口），或稍后重试。'
            : '数据库中暂无可展示的食品，可先到「食品搜索」按关键词查找。'
        "
        action-text="去食品搜索"
        @action="router.push('/search')"
      />
    </section>
  </div>
</template>

<style scoped>
.na-home {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-6, 24px);
}

.na-home__hero {
  padding: var(--na-space-8, 32px) var(--na-space-6, 24px);
  border-radius: var(--na-radius-lg, 12px);
  background: linear-gradient(135deg, #eff6ff 0%, #ecfdf5 100%);
  text-align: center;
}

.na-home__headline {
  margin: 0 0 var(--na-space-2, 8px);
  font-size: 26px;
  font-weight: 700;
  color: var(--na-color-text, #111827);
}

.na-home__subhead {
  margin: 0 0 var(--na-space-5, 20px);
  font-size: var(--na-font-size-sm, 14px);
  color: var(--na-color-text-muted, #6b7280);
}

.na-home__search {
  max-width: 560px;
  margin: 0 auto;
  text-align: left;
}

.na-home__guest-hint {
  margin: var(--na-space-4, 16px) 0 0;
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-home__entries {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--na-space-4, 16px);
}

.na-home__entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--na-space-2, 8px);
  padding: var(--na-space-4, 16px);
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-lg, 12px);
  background: var(--na-color-surface, #ffffff);
  color: var(--na-color-text, #111827);
  text-decoration: none;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.na-home__entry:hover {
  border-color: var(--na-color-primary-500, #3b82f6);
  transform: translateY(-2px);
}

.na-home__entry-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--na-color-primary-50, #eff6ff);
  color: var(--na-color-primary-600, #2563eb);
}

.na-home__entry-label {
  font-size: var(--na-font-size-sm, 14px);
  font-weight: 500;
}

.na-home__section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--na-space-3, 12px);
}

.na-home__section-title {
  margin: 0;
  font-size: var(--na-font-size-lg, 17px);
  font-weight: 600;
}

.na-home__more {
  font-size: 13px;
  color: var(--na-color-primary-600, #2563eb);
  text-decoration: none;
}

.na-home__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--na-space-4, 16px);
}

.na-home__skeleton {
  padding: var(--na-space-3, 12px);
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
}

@media (max-width: 720px) {
  .na-home__entries {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
