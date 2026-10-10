<script setup>
/**
 * 营养成分表（核心组件）
 *
 * 严格按契约 3.3 的三态渲染，不做任何营养学推断、不做数值兜底：
 *   - `PRESENT` → 原样展示后端 `displayText`（值 + 单位）
 *   - `ZERO`    → 原样展示 `displayText`（形如 "0 g"），**不隐藏**
 *   - `MISSING` → 显示「暂无数据」，**绝不以 0 代替**，且视觉上可区分（灰化 + 斜体 + 虚线底边）
 *
 * `incomplete` 为 true 时在表格上方显示一行提示，说明有多少项暂无数据、未以 0 代替。
 * 参考量（每 100g）来自接口的 `referenceAmount` / `referenceUnit` / `referenceNote`，不硬编码。
 */
import { computed } from 'vue'

import { MISSING_TEXT, VALUE_STATUS, formatNutrition, isMissingCell } from '../utils/format'

const props = defineProps({
  /** 契约 NutritionTable 结构 */
  table: {
    type: Object,
    default: null,
  },
  /** 密度：`compact` | `full` */
  density: {
    type: String,
    default: 'full',
  },
  /** 是否显示来源列 */
  showSource: {
    type: Boolean,
    default: true,
  },
  /** 是否显示分组标题列（如「宏量营养素」） */
  showGroup: {
    type: Boolean,
    default: false,
  },
})

/** 行集合；结构异常时回退空数组，不抛错 */
const rows = computed(() => (Array.isArray(props.table?.rows) ? props.table.rows : []))

/** 表头参考量文案（如「每 100 g」） */
const referenceLabel = computed(() => {
  const amount = props.table?.referenceAmount
  const unit = props.table?.referenceUnit
  if (amount === null || amount === undefined || amount === '') return '每 100g'
  return `每 ${amount}${unit || ''}`
})

/** 表头口径补充说明（接口给出才显示） */
const referenceNote = computed(() => props.table?.referenceNote || '')

/** 缺失项数量：优先用接口给的 missingCount，否则按行统计 */
const missingCount = computed(() => {
  if (typeof props.table?.missingCount === 'number') return props.table.missingCount
  return rows.value.filter((row) => isMissingCell(row)).length
})

/** 是否存在缺失（接口未给 incomplete 时按行统计兜底判断，仅影响提示显示） */
const isIncomplete = computed(() => {
  if (typeof props.table?.incomplete === 'boolean') return props.table.incomplete
  return missingCount.value > 0
})

/** 提示文案 */
const incompleteText = computed(
  () => `本表有 ${missingCount.value} 项暂无数据，未以 0 代替。`,
)

/**
 * 取行的展示文本（原样使用后端 displayText，缺失显示「暂无数据」）。
 * @param {object} row 营养行
 * @returns {string}
 */
function valueText(row) {
  return formatNutrition(row)
}

/**
 * 取行名称。
 * 契约 3.3 的行结构用 `nutrientName`；这里兼容 `name`/`label`，
 * 都取不到时才显示占位文案（不编造营养素名称）。
 * @param {object} row 营养行
 * @returns {string}
 */
function nutrientName(row) {
  if (!row) return '未命名营养素'
  return row.nutrientName || row.name || row.label || row.nutrientCode || row.code || '未命名营养素'
}

/**
 * 取行的来源文本。
 * 契约 3.3 的 `source` 是一个对象（`{ name, sourceType, ... }`），
 * 这里统一取名称展示，避免出现 `[object Object]`。
 * @param {object} row 营养行
 * @returns {string}
 */
function sourceText(row) {
  const source = row && (row.source || row.dataSource)
  if (!source) return ''
  if (typeof source === 'string') return source
  if (typeof source === 'object') {
    return source.name || source.title || source.code || ''
  }
  return String(source)
}

/**
 * 行样式类：三态视觉区分。
 * @param {object} row 营养行
 * @returns {Record<string, boolean>}
 */
function rowClass(row) {
  return {
    'na-nutrition-table__row--missing': row?.valueStatus === VALUE_STATUS.MISSING,
    'na-nutrition-table__row--zero': row?.valueStatus === VALUE_STATUS.ZERO,
  }
}

const isCompact = computed(() => props.density === 'compact')

/**
 * 营养素分组的展示文案。
 * 这是纯展示用的分组标签映射（不是营养数据本身）；
 * 未收录的分组编码原样显示，绝不猜测含义。
 */
const GROUP_LABELS = {
  MACRO: '宏量营养素',
  MINERAL: '矿物质',
  VITAMIN: '维生素',
  AMINO_ACID: '氨基酸',
  FATTY_ACID: '脂肪酸',
  OTHER: '其他',
}

/**
 * 取行的分组文案。
 * @param {object} row 营养行
 * @returns {string}
 */
function groupText(row) {
  if (!row) return '—'
  if (row.group) return String(row.group)
  const code = row.groupCode
  if (!code) return '—'
  return GROUP_LABELS[code] || String(code)
}
</script>

<template>
  <div class="na-nutrition-table" :class="{ 'na-nutrition-table--compact': isCompact }">
    <p v-if="isIncomplete" class="na-nutrition-table__notice" role="note">
      <span class="na-nutrition-table__notice-icon" aria-hidden="true">!</span>
      {{ incompleteText }}
    </p>

    <table class="na-table na-nutrition-table__table">
      <caption class="na-nutrition-table__caption">
        {{ referenceLabel }}含量
        <span v-if="referenceNote" class="na-nutrition-table__note">（{{ referenceNote }}）</span>
      </caption>
      <thead>
        <tr>
          <th v-if="showGroup" scope="col">分类</th>
          <th scope="col">营养素</th>
          <th scope="col" class="na-nutrition-table__col-value">含量</th>
          <th v-if="showSource" scope="col">数据来源</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="row.nutrientId || row.id || row.nutrientCode || row.code || index" :class="rowClass(row)">
          <td v-if="showGroup">{{ groupText(row) }}</td>
          <th scope="row" class="na-nutrition-table__name">
            {{ nutrientName(row) }}
            <span v-if="row.unit && !isCompact" class="na-nutrition-table__unit">（{{ row.unit }}）</span>
          </th>
          <td
            class="na-nutrition-table__col-value na-nutrition-table__value"
            :class="{ 'is-missing': row.valueStatus === VALUE_STATUS.MISSING }"
          >
            {{ valueText(row) }}
          </td>
          <td v-if="showSource" class="na-nutrition-table__source">
            <span v-if="sourceText(row)">{{ sourceText(row) }}</span>
            <span v-else class="na-nutrition-table__source-empty">—</span>
          </td>
        </tr>

        <tr v-if="rows.length === 0">
          <td :colspan="showSource ? (showGroup ? 4 : 3) : showGroup ? 3 : 2" class="na-nutrition-table__empty">
            {{ MISSING_TEXT }}
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="rows.length === 0" class="na-nutrition-table__hint">
      接口未返回该食品的营养数据行，请稍后重试或查看其他形态。
    </p>
  </div>
</template>

<style scoped>
.na-nutrition-table {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
}

.na-nutrition-table__notice {
  display: flex;
  align-items: flex-start;
  gap: var(--na-space-2, 8px);
  margin: 0;
  padding: 8px 12px;
  border: 1px solid var(--na-color-warning, #f59e0b);
  border-radius: var(--na-radius-md, 8px);
  background: var(--na-color-warning-50, #fffbeb);
  color: #92400e;
  font-size: 13px;
  line-height: 1.5;
}

.na-nutrition-table__notice-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  margin-top: 1px;
  border-radius: 50%;
  background: var(--na-color-warning, #f59e0b);
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
}

.na-nutrition-table__table {
  width: 100%;
}

.na-nutrition-table__caption {
  caption-side: top;
  text-align: left;
  padding: 0 0 6px;
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-nutrition-table__note {
  color: var(--na-color-text-muted, #6b7280);
}

.na-nutrition-table__name {
  font-weight: 500;
  text-align: left;
}

.na-nutrition-table__unit {
  font-weight: 400;
  color: var(--na-color-text-muted, #6b7280);
  font-size: 12px;
}

.na-nutrition-table__col-value {
  width: 140px;
}

.na-nutrition-table__value.is-missing {
  color: var(--na-color-text-muted, #9ca3af);
  font-style: italic;
}

.na-nutrition-table__row--missing {
  border-bottom-style: dashed;
}

.na-nutrition-table__row--missing td,
.na-nutrition-table__row--missing th {
  opacity: 0.75;
  background: var(--na-color-neutral-50, #f9fafb);
}

.na-nutrition-table__row--zero td,
.na-nutrition-table__row--zero th {
  color: var(--na-color-text, #111827);
}

.na-nutrition-table__source {
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-nutrition-table__source-empty {
  opacity: 0.6;
}

.na-nutrition-table__empty {
  text-align: center;
  color: var(--na-color-text-muted, #6b7280);
  font-style: italic;
}

.na-nutrition-table__hint {
  margin: 0;
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

/* 紧凑模式：更小的行高与字号 */
.na-nutrition-table--compact .na-nutrition-table__table th,
.na-nutrition-table--compact .na-nutrition-table__table td {
  padding: 6px 8px;
  font-size: 13px;
}
</style>
