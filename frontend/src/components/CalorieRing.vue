<script setup>
/**
 * 纯 SVG 环形图（热量 / 占比）
 *
 * 只负责"画"，不做任何营养学换算：
 *   - `value` 为 `null`（暂无数据）时，环内显示 MISSING_TEXT，环本身画成虚线轨道；
 *   - `value` 为 `0` 时，显示 `0` 并保留完整轨道（不隐藏），便于用户确认确实为 0。
 *
 * 进度按 `value / max` 计算，`max` 由调用方给定（例如热量环常用 2000 kcal 参考值）。
 */
import { computed } from 'vue'

import { MISSING_TEXT, formatNumber } from '../utils/format'

const props = defineProps({
  /** 当前值；`null` 表示暂无数据 */
  value: {
    type: Number,
    default: null,
  },
  /** 满环对应的值（>0） */
  max: {
    type: Number,
    default: 100,
  },
  /** 数值单位 */
  unit: {
    type: String,
    default: '',
  },
  /** 环下方说明文字 */
  label: {
    type: String,
    default: '',
  },
  /** 直径（px） */
  size: {
    type: Number,
    default: 160,
  },
  /** 环宽（px） */
  stroke: {
    type: Number,
    default: 12,
  },
  /** 进度色 */
  color: {
    type: String,
    default: 'var(--na-color-primary-500, #3b82f6)',
  },
})

/** 是否暂无数据 */
const isMissing = computed(() => props.value === null || props.value === undefined)

/** 半径与周长 */
const radius = computed(() => (props.size - props.stroke) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)

/** 进度比例 0~1；缺失时为 0（只用于描边长度，不影响文本） */
const ratio = computed(() => {
  if (isMissing.value) return 0
  const value = Number(props.value)
  const max = Number(props.max)
  if (!Number.isFinite(value) || !Number.isFinite(max) || max <= 0) return 0
  return Math.max(0, Math.min(1, value / max))
})

/** 描边虚线长度 */
const dashOffset = computed(() => circumference.value * (1 - ratio.value))

/** 中心文本 */
const valueText = computed(() => {
  if (isMissing.value) return MISSING_TEXT
  const value = Number(props.value)
  if (!Number.isFinite(value)) return MISSING_TEXT
  return formatNumber(value)
})

/** 视图中心坐标 */
const center = computed(() => props.size / 2)
</script>

<template>
  <figure class="na-calorie-ring" :style="{ width: `${size}px` }">
    <svg class="na-calorie-ring__svg" :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" role="img"
      :aria-label="label ? `${label} ${valueText}` : valueText">
      <circle
        class="na-calorie-ring__track"
        :cx="center"
        :cy="center"
        :r="radius"
        fill="none"
        :stroke-width="stroke"
      />
      <circle
        v-if="!isMissing"
        class="na-calorie-ring__progress"
        :cx="center"
        :cy="center"
        :r="radius"
        fill="none"
        :stroke="color"
        :stroke-width="stroke"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
      />
      <text class="na-calorie-ring__value" :x="center" :y="center - 2" text-anchor="middle" dominant-baseline="middle">
        {{ valueText }}
      </text>
      <text
        v-if="!isMissing && unit"
        class="na-calorie-ring__unit"
        :x="center"
        :y="center + 20"
        text-anchor="middle"
        dominant-baseline="middle"
      >
        {{ unit }}
      </text>
    </svg>
    <figcaption v-if="label" class="na-calorie-ring__label">{{ label }}</figcaption>
  </figure>
</template>

<style scoped>
.na-calorie-ring {
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--na-space-2, 8px);
}

.na-calorie-ring__svg {
  transform: rotate(-90deg);
}

.na-calorie-ring__track {
  stroke: var(--na-color-neutral-100, #f3f4f6);
}

.na-calorie-ring__progress {
  transition: stroke-dashoffset 0.4s ease;
}

.na-calorie-ring__value {
  transform: rotate(90deg);
  transform-origin: center;
  font-size: 20px;
  font-weight: 700;
  fill: var(--na-color-text, #111827);
}

.na-calorie-ring__unit {
  transform: rotate(90deg);
  transform-origin: center;
  font-size: 11px;
  fill: var(--na-color-text-muted, #6b7280);
}

.na-calorie-ring__label {
  font-size: var(--na-font-size-sm, 14px);
  color: var(--na-color-text-muted, #6b7280);
  text-align: center;
}
</style>
