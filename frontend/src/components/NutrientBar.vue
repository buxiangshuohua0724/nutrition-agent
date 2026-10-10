<script setup>
/**
 * 营养素占比条
 *
 * 用于营养分析报告的「营养概览」。
 * 占比由**页面层**计算后以 `percent` 传入（本组件不做任何换算与推断）：
 *   - `percent` 为 `null` → 该项营养「暂无数据」，显示 MISSING_TEXT 且不画进度条；
 *   - `percent` 为 `0`   → 显示 0% 且**保留一条零宽进度条的轨道**（不隐藏，便于用户确认"确实为 0"）；
 *   - 其余 → 按比例填充。
 *
 * 注意：`0` 与 `null` 语义完全不同，组件内部一律用 `=== null` / `=== undefined` 判断，禁止真值判断。
 */
import { computed } from 'vue'

import { MISSING_TEXT, formatNumber } from '../utils/format'

const props = defineProps({
  /** 营养素名（如「蛋白质」） */
  label: {
    type: String,
    required: true,
  },
  /** 占比百分数（0~100）；`null` 表示暂无数据 */
  percent: {
    type: Number,
    default: null,
  },
  /** 右侧补充说明（如「12.4 g / 100g」），可为空 */
  detail: {
    type: String,
    default: '',
  },
  /** 进度条颜色（CSS 颜色值） */
  color: {
    type: String,
    default: 'var(--na-color-primary-500, #3b82f6)',
  },
  /** 是否显示百分比数字 */
  showValue: {
    type: Boolean,
    default: true,
  },
})

/** 是否为「暂无数据」（只认 null/undefined，绝不用真值判断） */
const isMissing = computed(() => props.percent === null || props.percent === undefined)

/** 夹取到 0~100 的宽度百分比文本 */
const widthText = computed(() => {
  if (isMissing.value) return '0%'
  const value = Number(props.percent)
  if (!Number.isFinite(value)) return '0%'
  return `${Math.max(0, Math.min(100, value))}%`
})

/** 右侧数值文本 */
const valueText = computed(() => {
  if (isMissing.value) return MISSING_TEXT
  const value = Number(props.percent)
  if (!Number.isFinite(value)) return MISSING_TEXT
  return `${formatNumber(Math.max(0, Math.min(100, value)), 1)}%`
})
</script>

<template>
  <div class="na-nutrient-bar" :class="{ 'na-nutrient-bar--missing': isMissing }">
    <div class="na-nutrient-bar__head">
      <span class="na-nutrient-bar__label">{{ label }}</span>
      <span class="na-nutrient-bar__meta">
        <span v-if="detail" class="na-nutrient-bar__detail">{{ detail }}</span>
        <span v-if="showValue" class="na-nutrient-bar__value">{{ valueText }}</span>
      </span>
    </div>

    <div
      class="na-nutrient-bar__track"
      role="progressbar"
      :aria-label="`${label}占比`"
      :aria-valuenow="isMissing ? undefined : percent"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <span
        v-if="!isMissing"
        class="na-nutrient-bar__fill"
        :style="{ width: widthText, background: color }"
      ></span>
      <span v-else class="na-nutrient-bar__fill na-nutrient-bar__fill--missing"></span>
    </div>
  </div>
</template>

<style scoped>
.na-nutrient-bar {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.na-nutrient-bar__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--na-space-2, 8px);
  font-size: var(--na-font-size-sm, 14px);
}

.na-nutrient-bar__label {
  font-weight: 500;
  color: var(--na-color-text, #111827);
}

.na-nutrient-bar__meta {
  display: inline-flex;
  align-items: baseline;
  gap: var(--na-space-2, 8px);
}

.na-nutrient-bar__detail {
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-nutrient-bar__value {
  font-weight: 600;
  color: var(--na-color-text, #111827);
}

.na-nutrient-bar--missing .na-nutrient-bar__value {
  color: var(--na-color-text-muted, #9ca3af);
  font-style: italic;
  font-weight: 400;
}

.na-nutrient-bar__track {
  position: relative;
  height: 8px;
  border-radius: 4px;
  background: var(--na-color-neutral-100, #f3f4f6);
  overflow: hidden;
}

.na-nutrient-bar__fill {
  display: block;
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease;
}

.na-nutrient-bar__fill--missing {
  width: 100%;
  background: repeating-linear-gradient(
    45deg,
    var(--na-color-neutral-200, #e5e7eb),
    var(--na-color-neutral-200, #e5e7eb) 6px,
    transparent 6px,
    transparent 12px
  );
}
</style>
