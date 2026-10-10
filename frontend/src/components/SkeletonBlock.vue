<script setup>
/**
 * 骨架屏占位
 *
 * 高度/宽度/行数/形状均可配置，用于列表与卡片的加载态。
 */
import { computed } from 'vue'

const props = defineProps({
  /** 行数（每行一个骨架条） */
  rows: {
    type: Number,
    default: 1,
  },
  /** 单行高度（CSS 值） */
  height: {
    type: String,
    default: '16px',
  },
  /** 圆形（用于头像/缩略图位） */
  circle: {
    type: Boolean,
    default: false,
  },
  /** 每行宽度，按数组循环取用；少于行数时循环复用 */
  widths: {
    type: Array,
    default: () => ['100%', '88%', '72%'],
  },
})

/** 展开后的行配置 */
const lines = computed(() =>
  Array.from({ length: Math.max(1, props.rows) }, (_, index) => ({
    key: index,
    width: props.widths[index % props.widths.length],
    height: props.circle ? props.height : props.height,
  })),
)
</script>

<template>
  <div class="na-skeleton-group" aria-hidden="true">
    <span
      v-for="line in lines"
      :key="line.key"
      class="na-skeleton"
      :class="{ 'na-skeleton--circle': circle }"
      :style="{
        width: circle ? height : line.width,
        height: line.height,
        borderRadius: circle ? '50%' : undefined,
      }"
    ></span>
  </div>
</template>

<style scoped>
.na-skeleton-group {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
  width: 100%;
}
</style>
