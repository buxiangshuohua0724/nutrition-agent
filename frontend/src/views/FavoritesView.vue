<script setup>
/**
 * 我的收藏
 *
 * 数据来源：
 *   - `GET /users/me/favorites`        登录用户的收藏列表（含食品摘要）
 *   - `DELETE /users/me/favorites/{id}` 取消收藏（由 store 乐观更新调用）
 *
 * 未登录时使用本地 localStorage 中的收藏 id；由于本地只有 id，
 * 页面会提示"登录后可查看收藏详情"，不伪造食品信息。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import FoodCard from '../components/FoodCard.vue'
import EmptyState from '../components/EmptyState.vue'
import SkeletonBlock from '../components/SkeletonBlock.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import { useAuthStore } from '../stores/auth'
import { useFavoritesStore } from '../stores/favorites'
import { getFoodDetail } from '../api/foods'

const router = useRouter()
const authStore = useAuthStore()
const favoritesStore = useFavoritesStore()

const loading = ref(false)
const isDemoData = ref(false)
const loadFailed = ref(false)
const details = ref([])

/** 收藏条目（登录用户优先后端数据，其次本地条目） */
const items = computed(() => details.value)

/** 本地只有 id 的收藏（未登录或接口未返回摘要时） */
const localOnlyIds = computed(() => {
  if (items.value.length > 0) return []
  return favoritesStore.ids
})

/** 加载收藏 */
async function loadFavorites() {
  loading.value = true
  loadFailed.value = false
  const { isDemoData: demo } = await favoritesStore.loadFavorites()
  isDemoData.value = demo

  // store 的 items 是接口返回的条目；没有摘要时再按 id 逐个补拉详情
  const fromStore = Array.isArray(favoritesStore.items) ? favoritesStore.items : []
  if (fromStore.length > 0) {
    details.value = fromStore
    loading.value = false
    return
  }

  const ids = favoritesStore.ids
  if (ids.length === 0) {
    details.value = []
    loading.value = false
    return
  }

  const results = await Promise.all(
    ids.slice(0, 30).map(async (id) => {
      const { data, isDemoData: demoData, error } = await getFoodDetail(id, { silent: true })
      if (demoData) isDemoData.value = true
      if (error || !data) return null
      return data
    }),
  )
  details.value = results.filter(Boolean)
  // 全部补拉失败（例如未登录且后端不可用）时保留 id 列表用于提示
  loadFailed.value = details.value.length === 0
  loading.value = false
}

/** 取消收藏 */
async function handleToggleFavorite(food) {
  await favoritesStore.toggleFavorite(food.id)
  details.value = details.value.filter((item) => String(item.id) !== String(food.id))
}

onMounted(loadFavorites)
</script>

<template>
  <div class="na-favorites">
    <header class="na-favorites__head">
      <div>
        <h2 class="na-favorites__title">我的收藏</h2>
        <p class="na-favorites__desc">
          已收藏 {{ favoritesStore.count }} 个食品<template v-if="!authStore.isLoggedIn">（当前保存在本机浏览器中）</template>
        </p>
      </div>
      <RouterLink class="na-btn na-btn--ghost" to="/search">去搜索食品</RouterLink>
    </header>

    <NoticeBanner
      v-if="isDemoData"
      variant="warning"
      title="演示数据"
      message="后端接口当前不可用，以下内容为开发模式下的内联演示数据，不能作为营养依据。"
    />

    <!-- 未登录提示 -->
    <NoticeBanner
      v-if="!authStore.isLoggedIn"
      variant="info"
      title="未登录"
      message="未登录时收藏只保存在本机浏览器。登录后收藏会同步到账号，换设备也能看到。"
    />

    <div v-if="loading" class="na-favorites__grid">
      <div v-for="index in 4" :key="index" class="na-card na-favorites__skeleton">
        <SkeletonBlock :rows="1" height="110px" :widths="['100%']" />
        <SkeletonBlock :rows="2" height="14px" />
      </div>
    </div>

    <div v-else-if="items.length > 0" class="na-favorites__grid">
      <FoodCard
        v-for="food in items"
        :key="food.id"
        :food="food"
        favorite
        @toggle-favorite="handleToggleFavorite"
      />
    </div>

    <!-- 只有本地 id 时：不伪造食品信息，给出去搜索的入口 -->
    <EmptyState
      v-else-if="localOnlyIds.length > 0"
      icon="heart-filled"
      title="收藏的详情暂时取不到"
      :description="`本机记录了 ${localOnlyIds.length} 个收藏，但暂时无法从接口获取食品详情。可以稍后重试，或到搜索页重新查找。`"
      action-text="去搜索食品"
      @action="router.push('/search')"
    />

    <EmptyState
      v-else
      icon="heart"
      :title="loadFailed ? '暂时无法获取收藏' : '还没有收藏任何食品'"
      :description="
        loadFailed
          ? '请确认后端服务已启动（默认 8080 端口），或稍后重试。'
          : '在食品详情页点击「收藏」，就能在这里快速找到它。'
      "
      action-text="去搜索食品"
      @action="router.push('/search')"
    />
  </div>
</template>

<style scoped>
.na-favorites {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-favorites__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--na-space-3, 12px);
}

.na-favorites__title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.na-favorites__desc {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-favorites__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: var(--na-space-4, 16px);
}

.na-favorites__skeleton {
  padding: var(--na-space-3, 12px);
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
}
</style>
