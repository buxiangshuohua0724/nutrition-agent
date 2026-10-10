<script setup>
/**
 * 后台 · 营养数据管理
 *
 * 接口（契约 4.7）：
 *   - `GET /admin/foods`                     选择食品
 *   - `GET /foods/{id}`                      取该食品的形态（variant）列表
 *   - `GET /admin/variants/{id}/nutrition`   读取某形态已录入的营养项
 *   - `PUT /admin/variants/{id}/nutrition`   保存
 *
 * **重要**：契约要求每一项营养数据都必须带 `sourceId`（来源可追溯率 100%），
 * 缺 `sourceId` 后端会返回 40001。前端在提交前先做同样的预校验，
 * 并在界面上逐行标出"缺来源"的行，避免用户提交后才发现被拒。
 *
 * 本页面**不计算、不推断**任何营养数值：`amount` 与 `displayText`
 * 都来自录入者填写的内容，前端只做原样提交与展示。
 */
import { computed, onMounted, reactive, ref } from 'vue'

import EmptyState from '../../components/EmptyState.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import NoticeBanner from '../../components/NoticeBanner.vue'
import { getAdminFoods, getVariantNutrition, updateVariantNutrition } from '../../api/admin'
import { getFoodDetail } from '../../api/foods'
import { useToastStore } from '../../stores/toast'
import { MISSING_TEXT, textOrPlaceholder } from '../../utils/format'

const toastStore = useToastStore()

/** 可选的取值状态（与契约 valueStatus 三态一致） */
const VALUE_STATUS_OPTIONS = [
  { value: 'PRESENT', label: 'PRESENT（有数值）' },
  { value: 'ZERO', label: 'ZERO（确认为 0）' },
  { value: 'MISSING', label: 'MISSING（暂无数据）' },
]

const foods = ref([])
const variants = ref([])
const loadingFoods = ref(false)
const loadingVariants = ref(false)
const loadingNutrition = ref(false)
const saving = ref(false)
const loadFailed = ref(false)

const selectedFoodId = ref('')
const selectedVariantId = ref('')

/** 已加载的营养项（每行含 sourceId，必填） */
const rows = ref([])
/** 后端返回的参考口径（原样展示，不硬编码） */
const reference = ref({ amount: null, unit: '', note: '', variantLabel: '' })

/** 缺来源的行号（1 起） */
const missingSourceIndexes = computed(() =>
  rows.value.reduce((acc, row, index) => {
    if (!String(row.sourceId === null || row.sourceId === undefined ? '' : row.sourceId).trim()) acc.push(index + 1)
    return acc
  }, []),
)

const canSave = computed(() => selectedVariantId.value !== '' && rows.value.length > 0 && missingSourceIndexes.value.length === 0)

/** 参考口径文案 */
const referenceText = computed(() => {
  if (reference.value.note) return reference.value.note
  if (reference.value.amount === null || reference.value.amount === undefined) return MISSING_TEXT
  return `每 ${reference.value.amount}${reference.value.unit || ''}`
})

/** 加载食品列表 */
async function loadFoods() {
  loadingFoods.value = true
  loadFailed.value = false
  const { data, error } = await getAdminFoods({ page: 1, pageSize: 50 })
  loadingFoods.value = false
  loadFailed.value = Boolean(error)
  foods.value = error || !data || !Array.isArray(data.list) ? [] : data.list
  if (foods.value.length > 0 && !selectedFoodId.value) {
    selectedFoodId.value = String(foods.value[0].id)
    await loadVariants()
  }
}

/** 加载所选食品的形态列表 */
async function loadVariants() {
  variants.value = []
  selectedVariantId.value = ''
  rows.value = []
  if (!selectedFoodId.value) return

  loadingVariants.value = true
  const { data } = await getFoodDetail(selectedFoodId.value, { silent: true })
  loadingVariants.value = false

  const list = data && Array.isArray(data.variants) ? data.variants : []
  variants.value = list
  if (list.length === 0) return

  const preferred = list.find((variant) => variant.isDefault) || list[0]
  selectedVariantId.value = String(preferred.id)
  await loadNutrition()
}

/** 加载所选形态的营养项 */
async function loadNutrition() {
  rows.value = []
  reference.value = { amount: null, unit: '', note: '', variantLabel: '' }
  if (!selectedVariantId.value) return

  loadingNutrition.value = true
  try {
    const data = await getVariantNutrition(selectedVariantId.value)
    const list = data && Array.isArray(data.rows) ? data.rows : []
    rows.value = list.map((row) => ({
      nutrientId: row.nutrientId === undefined ? null : row.nutrientId,
      nutrientCode: row.nutrientCode || '',
      nutrientName: row.nutrientName || '',
      unit: row.unit || '',
      valueStatus: row.valueStatus || 'MISSING',
      amount: row.amount === undefined || row.amount === null ? '' : String(row.amount),
      displayText: row.displayText || '',
      sourceId: row.sourceId === undefined || row.sourceId === null ? '' : String(row.sourceId),
      sourceNote: row.sourceNote || '',
    }))
    reference.value = {
      amount: data && data.referenceAmount !== undefined ? data.referenceAmount : null,
      unit: (data && data.referenceUnit) || '',
      note: (data && data.referenceNote) || '',
      variantLabel: (data && data.variantLabel) || '',
    }
  } catch (error) {
    rows.value = []
  } finally {
    loadingNutrition.value = false
  }
}

/** 新增一行（空行，全部字段由录入者填写） */
function addRow() {
  rows.value.push({
    nutrientId: null,
    nutrientCode: '',
    nutrientName: '',
    unit: '',
    valueStatus: 'MISSING',
    amount: '',
    displayText: '',
    sourceId: '',
    sourceNote: '',
  })
}

/**
 * 删除一行。
 * @param {number} index 行下标
 */
function removeRow(index) {
  rows.value.splice(index, 1)
}

/** 保存 */
async function save() {
  if (saving.value || !selectedVariantId.value) return
  if (rows.value.length === 0) {
    toastStore.warning('没有可保存的营养项')
    return
  }
  if (missingSourceIndexes.value.length > 0) {
    // 与契约保持一致：缺 sourceId 必须被拒绝（后端返回 40001）
    toastStore.error(`第 ${missingSourceIndexes.value.join('、')} 行缺少来源（sourceId），无法保存`)
    return
  }

  saving.value = true
  try {
    const items = rows.value.map((row) => ({
      nutrientId: row.nutrientId,
      nutrientCode: row.nutrientCode,
      valueStatus: row.valueStatus,
      unit: row.unit,
      // MISSING 时 amount 必须为 null（契约：MISSING 绝不参与求和）
      amount: row.valueStatus === 'MISSING' || row.amount === '' ? null : Number(row.amount),
      displayText: row.displayText,
      sourceId: row.sourceId === '' ? null : Number(row.sourceId),
      sourceNote: row.sourceNote,
    }))
    await updateVariantNutrition(selectedVariantId.value, { items })
    toastStore.success('营养数据已保存')
    await loadNutrition()
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    saving.value = false
  }
}

onMounted(loadFoods)
</script>

<template>
  <div class="na-admin-nutrition">
    <header class="na-admin-page__head">
      <div>
        <h2 class="na-admin-page__title">营养数据管理</h2>
        <p class="na-admin-page__desc">
          按「食品 → 形态」逐项维护营养数据。每一项都必须填写来源（sourceId），否则后端会以 40001 拒绝保存。
        </p>
      </div>
      <button type="button" class="na-btn na-btn--primary" :disabled="!canSave || saving" @click="save">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </header>

    <NoticeBanner
      v-if="loadFailed"
      variant="warning"
      title="接口不可用"
      message="无法获取食品列表，请确认后端服务已启动（默认 8080 端口）且当前账号具备管理权限。"
    />

    <NoticeBanner
      v-if="missingSourceIndexes.length > 0"
      variant="warning"
      title="有项目缺少来源"
      :emphasis="`第 ${missingSourceIndexes.join('、')} 行`"
      message="缺少来源（sourceId）的营养数据无法保存，请补全后再提交。"
    />

    <!-- 选择区 -->
    <form class="na-card na-admin-nutrition__selector" @submit.prevent="loadVariants">
      <div class="na-admin-nutrition__field">
        <label for="na-admin-nutrition-food">食品</label>
        <select id="na-admin-nutrition-food" v-model="selectedFoodId" class="na-input" @change="loadVariants">
          <option value="">请选择食品</option>
          <option v-for="food in foods" :key="food.id" :value="String(food.id)">
            {{ food.name }}（{{ textOrPlaceholder(food.code) }}）
          </option>
        </select>
      </div>

      <div class="na-admin-nutrition__field">
        <label for="na-admin-nutrition-variant">形态</label>
        <select
          id="na-admin-nutrition-variant"
          v-model="selectedVariantId"
          class="na-input"
          :disabled="loadingVariants || variants.length === 0"
          @change="loadNutrition"
        >
          <option value="">请选择形态</option>
          <option v-for="variant in variants" :key="variant.id" :value="String(variant.id)">
            {{ variant.nameSuffix || variant.stateLabel || variant.state || `形态 ${variant.id}` }}
          </option>
        </select>
      </div>

      <div class="na-admin-nutrition__field">
        <span class="na-admin-nutrition__label">参考口径</span>
        <span class="na-admin-nutrition__reference">{{ referenceText }}</span>
      </div>
    </form>

    <SkeletonBlock v-if="loadingFoods || loadingVariants || loadingNutrition" :rows="6" height="20px" />

    <template v-else>
      <div v-if="rows.length > 0" class="na-card na-admin-nutrition__table-wrap">
        <table class="na-table">
          <thead>
            <tr>
              <th scope="col">营养素编码</th>
              <th scope="col">营养素名称</th>
              <th scope="col">取值状态</th>
              <th scope="col">单位</th>
              <th scope="col">数值</th>
              <th scope="col">展示文本</th>
              <th scope="col">来源标识（必填）</th>
              <th scope="col">来源说明</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in rows" :key="index" :class="{ 'is-missing-source': !String(row.sourceId).trim() }">
              <td><input v-model="row.nutrientCode" class="na-input" type="text" /></td>
              <td><input v-model="row.nutrientName" class="na-input" type="text" /></td>
              <td>
                <select v-model="row.valueStatus" class="na-input">
                  <option v-for="option in VALUE_STATUS_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </td>
              <td><input v-model="row.unit" class="na-input na-admin-nutrition__narrow" type="text" /></td>
              <td>
                <input
                  v-model="row.amount"
                  class="na-input na-admin-nutrition__narrow"
                  type="number"
                  step="any"
                  :disabled="row.valueStatus === 'MISSING'"
                  :placeholder="row.valueStatus === 'MISSING' ? '不参与计算' : ''"
                />
              </td>
              <td><input v-model="row.displayText" class="na-input" type="text" placeholder="例如 15 kcal" /></td>
              <td>
                <input v-model="row.sourceId" class="na-input na-admin-nutrition__narrow" type="number" placeholder="必填" />
              </td>
              <td><input v-model="row.sourceNote" class="na-input" type="text" /></td>
              <td>
                <button type="button" class="na-admin-nutrition__link" @click="removeRow(index)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!--
        未选择形态 ≠ 该形态没有数据。前者是"尚未发起查询"，后者才是「暂无数据」；
        两者若共用同一句文案，会与三态语义（MISSING 表示数据库确实缺这一项）冲突。
      -->
      <EmptyState
        v-else-if="!selectedVariantId"
        icon="chart"
        title="请先选择食品与形态"
        description="选择之后这里会列出该形态的营养项，可逐项修改或新增。"
      />

      <EmptyState
        v-else
        icon="chart"
        title="该形态还没有营养数据"
        description="数据库里这个形态没有营养记录。可以新增一行手工录入，或前往食品库确认形态是否正确。"
      />

      <div class="na-admin-nutrition__actions">
        <button type="button" class="na-btn na-btn--ghost" :disabled="!selectedVariantId" @click="addRow">新增一行</button>
        <button type="button" class="na-btn na-btn--primary" :disabled="!canSave || saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>

      <p class="na-admin-nutrition__note">
        提示：`MISSING` 表示数据暂缺，保存时 `amount` 会提交为 `null`，绝不写成 0；
        `ZERO` 表示经过确认的 0 值，两者在页面上必须区分展示。
      </p>
    </template>
  </div>
</template>

<style scoped>
.na-admin-nutrition {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-admin-nutrition__selector {
  display: flex;
  gap: var(--na-space-3, 12px);
  flex-wrap: wrap;
  padding: var(--na-space-3, 12px);
}

.na-admin-nutrition__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 180px;
  flex: 1;
}

.na-admin-nutrition__field label,
.na-admin-nutrition__label {
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-admin-nutrition__reference {
  font-size: 14px;
  padding-top: 6px;
}

.na-admin-nutrition__table-wrap {
  overflow-x: auto;
  padding: var(--na-space-2, 8px);
}

.na-admin-nutrition__narrow {
  width: 92px;
}

.na-admin-nutrition__table-wrap :deep(.is-missing-source) {
  background-color: var(--na-color-danger-50, #fef2f2);
}

.na-admin-nutrition__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--na-space-2, 8px);
}

.na-admin-nutrition__link {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  color: var(--na-color-danger, #ef4444);
  cursor: pointer;
}

.na-admin-nutrition__note {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--na-color-text-muted, #9ca3af);
}
</style>
