<script setup>
/**
 * 营养分析报告页（原型板块 6）
 *
 * 路由：`/report/:foodId`
 * 数据来源：
 *   - `GET /foods/{foodId}`                食品信息
 *   - `GET /foods/{foodId}/nutrition`      每 100g 营养表（口径来自接口字段）
 *   - `POST /nutrition/calculate`          按重量折算（可选，用户输入重量后触发）
 *
 * 关于"占比"的说明（重要，工程约束第 1 条）：
 *   页面上的占比只采用两种**后端已给出**的数据：
 *     1. 后端直接返回的比例字段（如 `percent` / `contributionPercent` / `dvPercent`）；
 *     2. 由契约 4.2 的计算接口返回的 `computedAmount` 与 `formula` 计算出的
 *        「该营养素对总能量的贡献」——公式为：营养素热量 ÷ 总热量，
 *        其中营养素热量 = 克数 × 该营养素的能量系数（蛋白质/碳水 4 kcal/g、脂肪 9 kcal/g）。
 *   任一环节数据缺失（`MISSING` 或接口未返回）时，占比显示「暂无数据」，
 *   **绝不以 0 代替缺失**，也不以缺失值参与求和。
 *   `ZERO` 是后端明确给出的 0，参与计算并显示 0%。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CalorieRing from '../components/CalorieRing.vue'
import NutrientBar from '../components/NutrientBar.vue'
import NutritionTable from '../components/NutritionTable.vue'
import RichText from '../components/RichText.vue'
import FoodImage from '../components/FoodImage.vue'
import SkeletonBlock from '../components/SkeletonBlock.vue'
import EmptyState from '../components/EmptyState.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import SourceList from '../components/SourceList.vue'
import { getFoodDetail, getFoodNutrition } from '../api/foods'
import { calculateNutrition } from '../api/nutrition'
import { MISSING_TEXT, formatDateTime, formatNutrition, nutritionAmount } from '../utils/format'
import { validateWeight } from '../utils/validators'

const route = useRoute()
const router = useRouter()

const foodId = computed(() => route.params.foodId)

const food = ref(null)
const table = ref(null)
const calculation = ref(null)
const loading = ref(true)
const calcLoading = ref(false)
const isDemoData = ref(false)
const loadFailed = ref(false)
const calcError = ref('')

const weight = ref(100)
const activeTab = ref('overview')

/** 营养素能量系数（kcal/g）——这是"占比"换算所需的标准系数，不是营养数据兜底 */
const ENERGY_FACTOR = { PROTEIN: 4, CARBOHYDRATE: 4, FAT: 9 }

const tabs = [
  { key: 'overview', label: '营养概览' },
  { key: 'detail', label: '详细营养成分' },
  { key: 'benefits', label: '功效与作用' },
  { key: 'suitable', label: '适宜人群' },
  { key: 'cautions', label: '注意事项' },
  { key: 'suggestion', label: '食用建议' },
]

/**
 * 按 nutrientCode 取营养行。
 * @param {string} code 营养素编码
 * @returns {object|null}
 */
function rowOf(code) {
  if (!table.value || !Array.isArray(table.value.rows)) return null
  return table.value.rows.find((row) => row.nutrientCode === code) || null
}

/** 参考口径文案（来自接口字段，不硬编码） */
const referenceLabel = computed(() => {
  if (!table.value) return '每 100g'
  const amount = table.value.referenceAmount !== undefined && table.value.referenceAmount !== null ? table.value.referenceAmount : 100
  const unit = table.value.referenceUnit || 'g'
  return table.value.referenceNote || `每 ${amount}${unit}`
})

/** 热量：只读接口返回，缺失即缺失 */
const energyRow = computed(() => rowOf('ENERGY'))
const energyText = computed(() => (energyRow.value ? formatNutrition(energyRow.value) : MISSING_TEXT))
const energyValue = computed(() => (energyRow.value ? nutritionAmount(energyRow.value) : null))

/**
 * 计算某一宏量营养素对总能量的贡献占比（%）。
 * @param {string} code 营养素编码（PROTEIN / FAT / CARBOHYDRATE / DIETARY_FIBER）
 * @returns {{percent: number|null, detail: string, missing: boolean, zero: boolean}}
 */
function macroContribution(code) {
  const row = rowOf(code)
  const factor = ENERGY_FACTOR[code]
  const base = {
    percent: null,
    detail: '',
    missing: !row,
    zero: false,
  }
  if (!row) return base

  const text = formatNutrition(row)
  if (row.valueStatus === 'MISSING') {
    return { percent: null, detail: `${text}，无法计算占比`, missing: true, zero: false }
  }

  // 后端直接给出比例时优先使用后端比例
  const provided =
    row.percent !== undefined && row.percent !== null
      ? row.percent
      : row.contributionPercent !== undefined && row.contributionPercent !== null
        ? row.contributionPercent
        : row.dvPercent !== undefined && row.dvPercent !== null
          ? row.dvPercent
          : null
  if (provided !== null) {
    return { percent: Number(provided), detail: text, missing: false, zero: Number(provided) === 0 }
  }

  // 膳食纤维没有统一的能量系数，没有后端比例时不做换算
  if (!factor) {
    return { percent: null, detail: `${text}，缺少后端比例`, missing: false, zero: false }
  }

  const amount = nutritionAmount(row)
  if (amount === null) {
    return { percent: null, detail: `${text}，无法计算占比`, missing: true, zero: false }
  }
  const energy = energyValue.value
  if (energy === null || energy <= 0) {
    // 总热量缺失或非正数时无法折算占比，不猜、不兜底
    return { percent: null, detail: `${text}，缺少总热量无法折算`, missing: false, zero: false }
  }

  const percent = ((amount * factor) / energy) * 100
  const rounded = Math.round(percent * 10) / 10
  return {
    percent: rounded,
    detail: `${text} × ${factor} kcal/g ÷ ${energyText.value}`,
    missing: false,
    zero: rounded === 0,
  }
}

/** 三条主营养占比条（蛋白质 / 脂肪 / 碳水化合物）+ 膳食纤维 */
const macroBars = computed(() => [
  { key: 'PROTEIN', label: '蛋白质', color: 'var(--na-color-primary-500, #3b82f6)', ...macroContribution('PROTEIN') },
  { key: 'FAT', label: '脂肪', color: 'var(--na-color-warning, #f59e0b)', ...macroContribution('FAT') },
  { key: 'CARBOHYDRATE', label: '碳水化合物', color: 'var(--na-color-success, #10b981)', ...macroContribution('CARBOHYDRATE') },
  { key: 'DIETARY_FIBER', label: '膳食纤维', color: 'var(--na-color-text-muted, #6b7280)', ...macroContribution('DIETARY_FIBER') },
])

/** 底部四个主要营养成分卡片 */
const primaryRows = computed(() =>
  ['ENERGY', 'PROTEIN', 'FAT', 'CARBOHYDRATE'].map((code) => {
    const row = rowOf(code)
    return {
      code,
      name: row ? row.nutrientName : code,
      text: row ? formatNutrition(row) : MISSING_TEXT,
      missing: !row || row.valueStatus === 'MISSING',
    }
  }),
)

/** 按重量折算的表格行 */
const calculationItems = computed(() => (calculation.value && Array.isArray(calculation.value.items) ? calculation.value.items : []))

/** 当前营养表来源 */
const sources = computed(() => {
  const list = []
  if (table.value && Array.isArray(table.value.sources)) list.push(...table.value.sources)
  if (calculation.value && Array.isArray(calculation.value.sources)) list.push(...calculation.value.sources)
  return list
})

/** 文本类标签页字段候选 */
const TEXT_FIELD_ALIASES = {
  benefits: ['benefits', 'effects', 'intro'],
  suitable: ['suitableFor', 'suitable', 'suitableCrowd'],
  cautions: ['cautions', 'warnings', 'notice'],
  suggestion: ['commonUsage', 'suggestion', 'eatingSuggestion'],
}

/** 当前文本标签页内容 */
const activeTextContent = computed(() => {
  if (!food.value) return ''
  const fields = TEXT_FIELD_ALIASES[activeTab.value] || [activeTab.value]
  for (const field of fields) {
    const value = food.value[field]
    if (typeof value === 'string' && value.trim() !== '') return value
  }
  return ''
})

/** 报告生成时间（页面展示用） */
const reportTime = ref(new Date().toISOString())

/** 加载报告基础数据 */
async function loadReport() {
  loading.value = true
  loadFailed.value = false
  const [detailResult, nutritionResult] = await Promise.all([
    getFoodDetail(foodId.value),
    getFoodNutrition(foodId.value, {}),
  ])
  loading.value = false
  loadFailed.value = Boolean(detailResult.error || nutritionResult.error)
  if (detailResult.isDemoData || nutritionResult.isDemoData) isDemoData.value = true

  food.value = detailResult.error ? null : detailResult.data
  table.value = nutritionResult.error ? null : nutritionResult.data
  reportTime.value = new Date().toISOString()

  if (food.value) {
    await runCalculation()
  }
}

/** 按重量折算 */
async function runCalculation() {
  const validation = validateWeight(weight.value)
  if (!validation.valid) {
    calcError.value = validation.message
    calculation.value = null
    return
  }
  calcError.value = ''
  calcLoading.value = true
  const { data, isDemoData: demo, error } = await calculateNutrition({
    foodId: foodId.value,
    weight: Number(weight.value),
    weightUnit: 'g',
  })
  calcLoading.value = false
  if (demo) isDemoData.value = true
  if (error) {
    calcError.value = '折算失败，请稍后重试。'
    calculation.value = null
    return
  }
  calculation.value = data
}

watch(foodId, () => {
  activeTab.value = 'overview'
  loadReport()
})

onMounted(loadReport)
</script>

<template>
  <div class="na-report">
    <div v-if="loading" class="na-report__skeleton">
      <SkeletonBlock :rows="1" height="120px" :widths="['100%']" />
      <SkeletonBlock :rows="5" height="18px" />
    </div>

    <EmptyState
      v-else-if="!food"
      icon="chart"
      title="暂时无法生成分析报告"
      :description="
        loadFailed
          ? '请确认后端服务已启动（默认 8080 端口），或稍后重试。'
          : '没有找到该食品，可能已下架或不存在。'
      "
      action-text="返回搜索"
      @action="router.push('/search')"
    />

    <template v-else>
      <NoticeBanner
        v-if="isDemoData"
        variant="warning"
        title="演示数据"
        message="后端接口当前不可用，以下营养数据为开发模式下的内联演示数据（固定为「西红柿 / 每 100g」那一套），可能与当前食品不一致，绝不能作为营养依据。"
      />

      <!-- 报告头 -->
      <section class="na-card na-report__head">
        <FoodImage
          :code="food.code"
          :name="food.name"
          :cover-image-url="food.coverImageUrl"
          :category-slug="food.category ? food.category.slug : ''"
          width="96px"
          height="96px"
        />
        <div class="na-report__head-meta">
          <div class="na-report__title-row">
            <h2 class="na-report__title">{{ food.name }}</h2>
            <span v-if="food.category && food.category.name" class="na-badge">{{ food.category.name }}</span>
          </div>
          <p class="na-report__sub">分析时间：{{ formatDateTime(reportTime) }} · 数据口径：{{ referenceLabel }}</p>
          <p class="na-report__sub">数据更新时间：{{ formatDateTime(table ? table.dataUpdatedAt : food.dataUpdatedAt) }}</p>
        </div>
        <div class="na-report__head-actions">
          <RouterLink class="na-btn na-btn--ghost" :to="`/food/${food.id}`">查看食品详情</RouterLink>
          <RouterLink class="na-btn na-btn--ghost" :to="`/chat?foodId=${food.id}`">问 AI</RouterLink>
        </div>
      </section>

      <!-- 标签页 -->
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

      <!-- 营养概览 -->
      <section v-if="activeTab === 'overview'" class="na-report__panel" role="tabpanel">
        <div class="na-report__overview">
          <CalorieRing
            :value="energyValue"
            :max="100"
            :unit="energyRow && energyRow.unit ? energyRow.unit : 'kcal'"
            :label="referenceLabel"
          />

          <div class="na-report__bars">
            <NutrientBar
              v-for="bar in macroBars"
              :key="bar.key"
              :label="bar.label"
              :percent="bar.percent"
              :detail="bar.detail"
              :color="bar.color"
            />
          </div>
        </div>

        <p class="na-report__formula-note">
          占比说明：优先使用后端返回的比例字段；没有时按「该营养素热量 ÷ 总热量」换算，
          其中蛋白质与碳水化合物按 4 kcal/g、脂肪按 9 kcal/g 计。任一项为「暂无数据」时占比显示
          「暂无数据」，不做 0 值兜底；后端给出的 0（ZERO）才显示 0%。
        </p>

        <!-- 主要营养成分卡片 -->
        <div class="na-report__cards">
          <div v-for="item in primaryRows" :key="item.code" class="na-card na-report__card">
            <span class="na-report__card-name">{{ item.name }}</span>
            <span class="na-report__card-value" :class="{ 'is-missing': item.missing }">{{ item.text }}</span>
          </div>
        </div>

        <!-- 按重量折算 -->
        <div class="na-card na-report__calculator">
          <h3 class="na-report__section-title">按食用重量折算</h3>
          <form class="na-report__calc-form" @submit.prevent="runCalculation">
            <label class="na-report__calc-label" for="na-report-weight">食用重量（g）</label>
            <input
              id="na-report-weight"
              v-model="weight"
              class="na-input"
              type="number"
              min="1"
              max="100000"
              step="1"
            />
            <button type="submit" class="na-btn na-btn--primary" :disabled="calcLoading">
              {{ calcLoading ? '计算中…' : '计算' }}
            </button>
          </form>
          <p v-if="calcError" class="na-report__calc-error">{{ calcError }}</p>

          <table v-if="calculationItems.length > 0" class="na-table na-report__calc-table">
            <thead>
              <tr>
                <th scope="col">营养素</th>
                <th scope="col">折算结果</th>
                <th scope="col">计算公式（后端返回）</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in calculationItems" :key="item.nutrientCode">
                <td>{{ item.nutrientName }}</td>
                <td>{{ formatNutrition(item) }}</td>
                <td class="na-report__formula">{{ item.formula || MISSING_TEXT }}</td>
              </tr>
            </tbody>
          </table>

          <div
            v-if="calculation && calculation.missingNutrients && calculation.missingNutrients.length > 0"
            class="na-report__missing"
          >
            以下营养素因缺少数据未参与折算：{{ calculation.missingNutrients.join('、') }}
          </div>

          <p class="na-report__calc-note">
            折算结果由后端计算接口返回，页面只做展示；缺失的营养素不参与求和，也不会显示为 0。
          </p>
        </div>
      </section>

      <!-- 详细营养成分 -->
      <section v-else-if="activeTab === 'detail'" class="na-report__panel" role="tabpanel">
        <NutritionTable v-if="table" :table="table" density="full" />
        <EmptyState
          v-else
          icon="chart"
          title="暂无营养数据"
          description="后端未收录该食品的营养数据，页面不会用 0 代替缺失值。"
          compact
        />
        <SourceList v-if="sources.length > 0" :sources="sources" title="数据来源" />
      </section>

      <!-- 文本标签页 -->
      <section v-else class="na-report__panel" role="tabpanel">
        <RichText :source="activeTextContent" />
      </section>

      <p class="na-report__disclaimer">
        本报告仅基于已收录的营养数据做客观呈现，不构成医学建议；如有疾病或特殊生理状况，请咨询医生或注册营养师。
      </p>
    </template>
  </div>
</template>

<style scoped>
.na-report {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-report__skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-report__head {
  display: flex;
  align-items: center;
  gap: var(--na-space-4, 16px);
  padding: var(--na-space-4, 16px);
  flex-wrap: wrap;
}

.na-report__head-meta {
  flex: 1;
  min-width: 200px;
}

.na-report__title-row {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
}

.na-report__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.na-report__sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-report__head-actions {
  display: flex;
  gap: var(--na-space-2, 8px);
}

.na-report__overview {
  display: grid;
  grid-template-columns: minmax(0, 200px) minmax(0, 1fr);
  gap: var(--na-space-5, 20px);
  align-items: center;
  padding: var(--na-space-4, 16px) 0;
}

.na-report__bars {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-3, 12px);
}

.na-report__formula-note {
  margin: 0 0 var(--na-space-4, 16px);
  padding: var(--na-space-2, 8px) var(--na-space-3, 12px);
  border-left: 3px solid var(--na-color-primary-500, #3b82f6);
  background: var(--na-color-surface-muted, #f9fafb);
  font-size: 12px;
  line-height: 1.7;
  color: var(--na-color-text-muted, #6b7280);
}

.na-report__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--na-space-3, 12px);
  margin-bottom: var(--na-space-4, 16px);
}

.na-report__card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--na-space-3, 12px);
}

.na-report__card-name {
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-report__card-value {
  font-size: 18px;
  font-weight: 600;
}

.na-report__card-value.is-missing {
  font-size: 14px;
  font-weight: 400;
  font-style: italic;
  color: var(--na-color-text-muted, #9ca3af);
}

.na-report__calculator {
  padding: var(--na-space-4, 16px);
}

.na-report__section-title {
  margin: 0 0 var(--na-space-3, 12px);
  font-size: var(--na-font-size-lg, 17px);
  font-weight: 600;
}

.na-report__calc-form {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
  flex-wrap: wrap;
}

.na-report__calc-label {
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-report__calc-error {
  margin: var(--na-space-2, 8px) 0 0;
  font-size: 13px;
  color: var(--na-color-danger, #ef4444);
}

.na-report__calc-table {
  margin-top: var(--na-space-3, 12px);
}

.na-report__formula {
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-report__missing {
  margin-top: var(--na-space-2, 8px);
  padding: var(--na-space-2, 8px) var(--na-space-3, 12px);
  border-radius: var(--na-radius-sm, 6px);
  background: #fffbeb;
  font-size: 13px;
  color: #92400e;
}

.na-report__calc-note,
.na-report__disclaimer {
  margin: var(--na-space-3, 12px) 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--na-color-text-muted, #9ca3af);
}

@media (max-width: 760px) {
  .na-report__overview {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
