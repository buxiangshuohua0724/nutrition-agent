<script setup>
/**
 * 图标组件 —— 渲染 `src/assets/icons/*.svg`
 *
 * 素材缺失时不报错：无法解析到图标时渲染一个内联的通用占位方块，
 * 保证菜单与按钮的布局不会塌陷。
 */
import { computed } from 'vue'
import { iconUrl } from '../utils/assets'

const props = defineProps({
  /** 图标名（对应 assets/icons 下的文件名，不含扩展名） */
  name: {
    type: String,
    required: true,
  },
  /** 尺寸（px） */
  size: {
    type: [Number, String],
    default: 20,
  },
})

/** 解析到的图标地址；为 null 时走占位符 */
const url = computed(() => iconUrl(props.name))

const sizeStyle = computed(() => ({
  width: typeof props.size === 'number' ? `${props.size}px` : props.size,
  height: typeof props.size === 'number' ? `${props.size}px` : props.size,
}))
</script>

<template>
  <img v-if="url" class="na-icon" :src="url" :style="sizeStyle" alt="" aria-hidden="true" />
  <svg
    v-else
    class="na-icon na-icon--placeholder"
    :style="sizeStyle"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <rect x="4" y="4" width="16" height="16" rx="3" />
    <path d="M8 12h8" />
  </svg>
</template>

<style scoped>
.na-icon {
  flex: 0 0 auto;
  display: block;
}

.na-icon--placeholder {
  color: var(--na-color-text-muted, #9ca3af);
  opacity: 0.7;
}
</style>
