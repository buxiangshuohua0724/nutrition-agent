<script setup>
/**
 * 置信度徽章
 *
 * 用于图片识别的候选列表。置信度为 `null` 时显示「暂无数据」，
 * 不猜数值、不默认 0%。颜色按低/中/高三档区分，仅作视觉提示。
 */
import { computed } from 'vue'

import { MISSING_TEXT, formatConfidence } from '../utils/format'

const props = defineProps({
  /** 置信度：0~1 的小数；也兼容 0~100 的百分数（`isPercent` 为 true） */
  confidence: {
    type: Number,
    default: null,
  },
  /** 传入值是否已经是百分数 */
  isPercent: {
    type: Boolean,
    default: false,
  },
  /** 前缀文案（如「相似度」） */
  prefix: {
    type: String,
    default: '相似度',
  },
})

/** 归一化到 0~1；缺失为 null */
const normalized = computed(() => {
  if (props.confidence === null || props.confidence === undefined) return null
  const value = Number(props.confidence)
  if (!Number.isFinite(value)) return null
  const ratio = props.isPercent ? value / 100 : value
  return Math.max(0, Math.min(1, ratio))
})

/** 展示文本 */
const text = computed(() => {
  if (normalized.value === null) return MISSING_TEXT
  return formatConfidence(normalized.value)
})

/**
 * 档位样式类（仅用于配色）。
 * @returns {Record<string, boolean>}
 */
const levelClass = computed(() => {
  if (normalized.value === null) return { 'na-confidence--unknown': true }
  if (normalized.value >= 0.8) return { 'na-confidence--high': true }
  if (normalized.value >= 0.5) return { 'na-confidence--mid': true }
  return { 'na-confidence--low': true }
})
</script>

<template>
  <span class="na-confidence na-badge" :class="levelClass" :title="`${prefix} ${text}`">
    <span class="na-confidence__prefix">{{ prefix }}</span>
    <span class="na-confidence__value">{{ text }}</span>
  </span>
</template>

<style scoped>
.na-confidence {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.na-confidence__prefix {
  opacity: 0.75;
}

.na-confidence__value {
  font-weight: 600;
}

.na-confidence--high {
  color: var(--na-color-success, #10b981);
}

.na-confidence--mid {
  color: var(--na-color-warning, #f59e0b);
}

.na-confidence--low {
  color: var(--na-color-danger, #ef4444);
}

.na-confidence--unknown {
  color: var(--na-color-text-muted, #6b7280);
}
</style>
