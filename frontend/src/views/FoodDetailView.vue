<script setup>
/**
 * 食品详情页（原型板块 3）
 *
 * 路由：`/food/:id`
 * 数据来源：
 *   - `GET /foods/{id}`            详情（含 variants 形态列表）
 *   - `GET /foods/{id}/nutrition`  营养表（variantId 可选）
 *
 * 硬约束：营养数值一律原样渲染 `displayText`（由 `NutritionTable` 负责），
 * 本页不做任何营养学推断、不做数值兜底、不把缺失当零。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import FoodImage from '../components/FoodImage.vue'
import NutritionTable from '../components/NutritionTable.vue'
import SourceList from '../components/SourceList.vue'
import SkeletonBlock from '../components/SkeletonBlock.vue'
import EmptyState from '../components/EmptyState.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import RichText from '../components/RichText.vue'
import { getFoodDetail, getFoodNutrition, getRelatedFoods } from '../api/foods'
import { useFavoritesStore } from '../stores/favorites'
import { useHistoryStore } from '../stores/history'
import { useToastStore } from '../stores/toast'
import { textOrPlaceholder } from '../utils/format'

const route = useRoute()
const router = useRouter()
const favoritesStore = useFavoritesStore()
const historyStore = useHistoryStore()
const toastStore = useToastStore()

const foodId = computed(() => route.params.id)

const food = ref(null)
const nutritionTable = ref(null)
const related = ref([])
const variants = ref([])
const activeVariantId = ref(null)

const loading = ref(true)
const nutritionLoading = ref(false)
const isDemoData = ref(false)
const loadFailed = ref(false)
const nutritionFailed = ref(false)

const activeTab = ref('basic')

/** 标签页定义（与原型板块 3 的六个页签一致） */
const tabs = [
  { key: 'basic', label: '基本信息' },
  { key: 'nutrition', label: '营养成分' },
  { key: 'benefits', label: '功效与作用' },
  { key: 'suitable', label: '适宜人群' },
  { key: 'cautions', label: '注意事项' },
  { key: 'suggestion', label: '食用建议' },
]

/** 分类展示文案 */
const categoryLabel = computed(() => {
  if (!food.value) return ''
  if (food.value.category && food.value.category.name) return food.value.category.name
  return textOrPlaceholder(food.value.categoryName)
})

/**
 * 别名文案：接口返回 `aliases: [{ alias, aliasType }]`，
 * 也兼容直接返回字符串的情况；都没有时交给占位符，不编造。
 */
const aliasText = computed(() => {
  const item = food.value
  if (!item) return ''
  if (Array.isArray(item.aliases) && item.aliases.length > 0) {
    return item.aliases
      .map((entry) => (entry && typeof entry === 'object' ? entry.alias : entry))
      .filter((text) => text !== undefined && text !== null && text !== '')
      .join('、')
  }
  if (typeof item.alias === 'string' && item.alias) return item.alias
  return ''
})

/** 基本信息两列表格的数据（缺失值显示占位符，不编造） */
const basicRows = computed(() => {
  const item = food.value
  if (!item) return []
  return [
    { label: '中文名', value: textOrPlaceholder(item.name) },
    { label: '别名', value: textOrPlaceholder(aliasText.value) },
    { label: '分类', value: textOrPlaceholder(categoryLabel.value) },
    { label: '产地', value: textOrPlaceholder(item.origin) },
    { label: '储存方式', value: textOrPlaceholder(item.storageMethod || item.storage) },
    { label: '食品编码', value: textOrPlaceholder(item.code) },
  ]
})

/** 形态显示文案：优先 nameSuffix / stateLabel，其次 state，都没有才用 id */
function variantLabel(variant) {
  if (!variant) return ''
  return variant.nameSuffix || variant.stateLabel || variant.state || `形态 ${variant.id}`
}

/** 当前选中的形态对象 */
const activeVariant = computed(() =>
  variants.value.find((item) => String(item.id) === String(activeVariantId.value)),
)

/** 营养表口径文案由接口字段拼装，页面不硬编码 */
const nutritionVariantLabel = computed(() => {
  if (activeVariant.value) return variantLabel(activeVariant.value)
  return nutritionTable.value && nutritionTable.value.variantLabel ? nutritionTable.value.variantLabel : ''
})

/** 当前营养表的数据来源列表（由接口返回，前端只负责渲染） */
const nutritionSources = computed(() => {
  if (!nutritionTable.value || !Array.isArray(nutritionTable.value.sources)) return []
  return nutritionTable.value.sources
})

/**
 * 文本类标签页对应的字段名候选。
 * 契约里字段命名可能是 `intro`/`suitableFor`/`commonUsage`，
 * 这里只做"字段取值"，不做任何内容生成；都取不到时交由占位符处理。
 */
const TEXT_FIELD_ALIASES = {
  benefits: ['benefits', 'effects', 'intro'],
  suitable: ['suitableFor', 'suitable', 'suitableCrowd'],
  cautions: ['cautions', 'warnings', 'notice'],
  suggestion: ['commonUsage', 'suggestion', 'eatingSuggestion'],
}

/** 当前标签页要展示的文本内容 */
const activeTextContent = computed(() => {
  if (!food.value) return ''
  const fields = TEXT_FIELD_ALIASES[activeTab.value] || [activeTab.value]
  for (const field of fields) {
    const value = food.value[field]
    if (typeof value === 'string' && value.trim() !== '') return value
  }
  return ''
})

/** 加载详情 */
async function loadDetail() {
  loading.value = true
  loadFailed.value = false
  const { data, isDemoData: demo, error } = await getFoodDetail(foodId.value)
  loading.value = false
  isDemoData.value = demo
  loadFailed.value = Boolean(error)

  if (error || !data) {
    food.value = null
    return
  }
  food.value = data
  variants.value = Array.isArray(data.variants) ? data.variants : []
  const defaultVariant = variants.value.find((item) => item.isDefault === true) || variants.value[0]
  activeVariantId.value = defaultVariant ? defaultVariant.id : null

  // 记录浏览历史（接口未就绪时也用详情里的信息记录）
  historyStore.recordView({
    id: data.id,
    name: data.name,
    code: data.code,
    categoryName: data.category ? data.category.name : data.categoryName,
    coverImageUrl: data.coverImageUrl,
  })

  await Promise.all([loadNutrition(), loadRelated()])
}

/** 加载营养表（跟随当前形态） */
async function loadNutrition() {
  if (!food.value) return
  nutritionLoading.value = true
  nutritionFailed.value = false
  const { data, isDemoData: demo, error } = await getFoodNutrition(foodId.value, {
    variantId: activeVariantId.value,
  })
  nutritionLoading.value = false
  if (demo) isDemoData.value = true
  nutritionFailed.value = Boolean(error)
  nutritionTable.value = error ? null : data
}

/** 加载相关推荐 */
async function loadRelated() {
  const { data, error } = await getRelatedFoods(foodId.value, { limit: 6 }, { silent: true })
  related.value = error || !Array.isArray(data) ? [] : data
}

/**
 * 切换形态：营养表随之变化（默认选中 isDefault 的形态）。
 * @param {number|string} variantId 形态 id
 */
async function selectVariant(variantId) {
  if (String(variantId) === String(activeVariantId.value)) return
  activeVariantId.value = variantId
  await loadNutrition()
}

/** 切换收藏 */
async function toggleFavorite() {
  if (!food.value) return
  const active = await favoritesStore.toggleFavorite(food.value.id)
  toastStore.success(active ? '已加入收藏' : '已取消收藏')
}

/** 分享：优先系统分享面板，其次复制链接 */
async function share() {
  const url = window.location.href
  const title = food.value ? food.value.name : 'AI 食品营养师'
  try {
    if (navigator.share) {
      await navigator.share({ title, url })
      return
    }
    await navigator.clipboard.writeText(url)
    toastStore.success('链接已复制到剪贴板')
  } catch (error) {
    // 用户取消系统分享面板不算失败，其余情况给出可操作的提示
    if (error && error.name === 'AbortError') return
    toastStore.warning('当前环境不支持自动分享，请手动复制地址栏链接')
  }
}

/** 进入营养分析报告页 */
function openReport() {
  router.push(`/report/${foodId.value}`)
}

watch(foodId, () => {
  activeTab.value = 'basic'
  loadDetail()
})

onMounted(loadDetail)
</script>

<template>
  <div class="na-detail">
    <!-- 加载骨架 -->
    <div v-if="loading" class="na-detail__skeleton">
      <SkeletonBlock :rows="1" height="260px" :widths="['100%']" />
      <SkeletonBlock :rows="4" height="16px" />
    </div>

    <EmptyState
      v-else-if="!food"
      icon="search"
      title="没有找到这个食品"
      :description="
        loadFailed
          ? '请确认后端服务已启动（默认 8080 端口），或稍后重试。'
          : '该食品可能已下架或不存在，可以换个关键词重新搜索。'
      "
      action-text="返回搜索"
      @action="router.push('/search')"
    />

    <template v-else>
      <NoticeBanner
        v-if="isDemoData"
        variant="warning"
        title="演示数据"
        message="后端接口当前不可用，以下食品信息与营养数据为开发模式下的内联演示数据，不能作为营养依据。"
      />

      <!-- 板块 3-1：大图 + 基本信息 -->
      <section class="na-detail__hero">
        <div class="na-detail__media">
          <FoodImage
            class="na-detail__cover"
            :code="food.code"
            :name="food.name"
            :cover-image-url="food.coverImageUrl"
            :category-slug="food.category ? food.category.slug : ''"
            height="260px"
          />
          <div v-if="food.images && food.images.length > 0" class="na-detail__thumbs">
            <img
              v-for="(image, index) in food.images"
              :key="image.id || index"
              class="na-detail__thumb"
              :src="image.thumbUrl || image.url"
              :alt="image.caption || `${food.name} 图片 ${index + 1}`"
              width="64"
              height="48"
            />
          </div>
        </div>

        <div class="na-detail__intro">
          <div class="na-detail__title-row">
            <h2 class="na-detail__title">{{ food.name }}</h2>
            <span v-if="categoryLabel" class="na-badge">{{ categoryLabel }}</span>
            <span v-if="activeVariant" class="na-badge">{{ variantLabel(activeVariant) }}</span>
          </div>

          <p v-if="aliasText" class="na-detail__alias">别名：{{ aliasText }}</p>

          <p class="na-detail__desc">
            {{ food.intro ? food.intro : '暂无简介。' }}
          </p>

          <div class="na-detail__actions">
            <button type="button" class="na-btn na-btn--primary" @click="toggleFavorite">
              {{ favoritesStore.isFavorite(food.id) ? '已收藏' : '收藏' }}
            </button>
            <button type="button" class="na-btn na-btn--ghost" @click="share">分享</button>
            <button type="button" class="na-btn na-btn--ghost" @click="openReport">营养分析报告</button>
            <RouterLink class="na-btn na-btn--ghost" to="/chat">问问 AI</RouterLink>
          </div>

          <!-- 形态切换器：只有存在多个形态时才显示 -->
          <div v-if="variants.length > 1" class="na-detail__variants">
            <span class="na-detail__variants-label">食用形态</span>
            <button
              v-for="variant in variants"
              :key="variant.id"
              type="button"
              class="na-chip"
              :class="{ 'is-active': String(variant.id) === String(activeVariantId) }"
              @click="selectVariant(variant.id)"
            >
              {{ variantLabel(variant) }}
            </button>
          </div>
        </div>
      </section>

      <!-- 板块 3-2：六个标签页 -->
      <section class="na-detail__tabs">
        <div class="na-tabs" role="tablist">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            role="tab"
            class="na-tab"
            :class="{ 'is-active': activeTab === tab.key }"
            :aria-selected="activeTab === tab.key"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>

        <!-- 基本信息：两列信息表 -->
        <div v-if="activeTab === 'basic'" class="na-detail__panel" role="tabpanel">
          <dl class="na-detail__info">
            <div v-for="row in basicRows" :key="row.label" class="na-detail__info-row">
              <dt>{{ row.label }}</dt>
              <dd>{{ row.value }}</dd>
            </div>
          </dl>
          <p v-if="food.pinyin" class="na-detail__hint">拼音：{{ food.pinyin }}</p>
        </div>

        <!-- 营养成分：核心组件 NutritionTable -->
        <div v-else-if="activeTab === 'nutrition'" class="na-detail__panel" role="tabpanel">
          <div class="na-detail__panel-head">
            <h3 class="na-detail__panel-title">
              营养成分<template v-if="nutritionVariantLabel">（{{ nutritionVariantLabel }}）</template>
            </h3>
            <button type="button" class="na-btn na-btn--ghost" :disabled="nutritionLoading" @click="loadNutrition">
              {{ nutritionLoading ? '加载中…' : '刷新' }}
            </button>
          </div>

          <SkeletonBlock v-if="nutritionLoading" :rows="6" height="18px" />

          <NutritionTable v-else-if="nutritionTable" :table="nutritionTable" density="full" />

          <EmptyState
            v-else
            icon="chart"
            :title="nutritionFailed ? '暂时无法获取营养数据' : '该食品暂无营养数据'"
            :description="
              nutritionFailed
                ? '请确认后端服务已启动（默认 8080 端口），或稍后重试。'
                : '后端未收录该形态的营养数据，页面不会用 0 代替缺失值。'
            "
            compact
          />

          <SourceList v-if="nutritionSources.length > 0" :sources="nutritionSources" title="营养数据来源" />
        </div>

        <!-- 文本类标签页：用安全富文本渲染，不使用 v-html -->
        <div v-else class="na-detail__panel" role="tabpanel">
          <RichText :source="activeTextContent" />
        </div>
      </section>

      <!-- 相关推荐 -->
      <section v-if="related.length > 0" class="na-detail__related">
        <h3 class="na-detail__panel-title">相关食品</h3>
        <div class="na-detail__related-grid">
          <RouterLink
            v-for="item in related"
            :key="item.id"
            class="na-card na-detail__related-card"
            :to="`/food/${item.id}`"
          >
            <FoodImage
              :code="item.code"
              :name="item.name"
              :cover-image-url="item.coverImageUrl"
              :category-slug="item.category ? item.category.slug : ''"
              height="96px"
            />
            <span class="na-detail__related-name">{{ item.name }}</span>
          </RouterLink>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.na-detail {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-5, 20px);
}

.na-detail__skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-detail__hero {
  display: grid;
  grid-template-columns: minmax(0, 320px) minmax(0, 1fr);
  gap: var(--na-space-5, 20px);
  align-items: start;
}

.na-detail__cover {
  width: 100%;
}

.na-detail__thumbs {
  display: flex;
  gap: var(--na-space-2, 8px);
  margin-top: var(--na-space-2, 8px);
}

.na-detail__thumb {
  width: 64px;
  height: 48px;
  object-fit: cover;
  border-radius: var(--na-radius-sm, 6px);
  border: 1px solid var(--na-color-border, #e5e7eb);
}

.na-detail__title-row {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
  flex-wrap: wrap;
}

.na-detail__title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
}

.na-detail__alias {
  margin: var(--na-space-2, 8px) 0 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-detail__desc {
  margin: var(--na-space-3, 12px) 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--na-color-text, #111827);
}

.na-detail__actions {
  display: flex;
  gap: var(--na-space-2, 8px);
  flex-wrap: wrap;
  margin-top: var(--na-space-4, 16px);
}

.na-detail__variants {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
  flex-wrap: wrap;
  margin-top: var(--na-space-4, 16px);
}

.na-detail__variants-label {
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-detail__panel {
  padding: var(--na-space-4, 16px) 0 0;
}

.na-detail__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--na-space-3, 12px);
}

.na-detail__panel-title {
  margin: 0;
  font-size: var(--na-font-size-lg, 17px);
  font-weight: 600;
}

.na-detail__info {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  margin: 0;
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-md, 8px);
  overflow: hidden;
}

.na-detail__info-row {
  display: flex;
  border-bottom: 1px solid var(--na-color-border, #e5e7eb);
}

.na-detail__info-row:nth-last-child(-n + 2) {
  border-bottom: none;
}

.na-detail__info-row dt {
  flex: none;
  width: 96px;
  padding: 10px 12px;
  background: var(--na-color-surface-muted, #f9fafb);
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-detail__info-row dd {
  flex: 1;
  min-width: 0;
  margin: 0;
  padding: 10px 12px;
  font-size: 13px;
  color: var(--na-color-text, #111827);
  word-break: break-word;
}

.na-detail__hint {
  margin: var(--na-space-2, 8px) 0 0;
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-detail__related-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--na-space-3, 12px);
  margin-top: var(--na-space-3, 12px);
}

.na-detail__related-card {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
  padding: var(--na-space-2, 8px);
  text-decoration: none;
  color: var(--na-color-text, #111827);
}

.na-detail__related-name {
  font-size: 13px;
}

@media (max-width: 860px) {
  .na-detail__hero {
    grid-template-columns: minmax(0, 1fr);
  }

  .na-detail__info {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
