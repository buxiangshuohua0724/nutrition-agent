<script setup>
/**
 * 食品插画
 *
 * 素材（`assets/foods/*.svg`）按食品 `code` 命名，可能缺失或被后端 `coverImageUrl` 覆盖。
 * 加载失败或素材缺失时渲染一个内联占位块（不显示破图），占位块只展示首字，不展示任何营养信息。
 */
import { computed, ref, watch } from 'vue'

import { foodCodeToSlug, foodImage, categoryBgVar } from '../utils/assets'

const props = defineProps({
  /** 食品 code（如 `VEG_TOMATO`），用于解析本地素材 */
  code: {
    type: String,
    default: '',
  },
  /** 食品名称，用于占位块首字与 alt */
  name: {
    type: String,
    default: '',
  },
  /** 后端返回的封面图地址，优先于本地素材 */
  coverImageUrl: {
    type: String,
    default: '',
  },
  /** 分类 slug，用于占位块底色 */
  categorySlug: {
    type: String,
    default: '',
  },
  /** 容器宽高比宽度 */
  width: {
    type: String,
    default: '100%',
  },
  /** 容器宽高比高度 */
  height: {
    type: String,
    default: 'auto',
  },
  /** 圆角 */
  radius: {
    type: String,
    default: 'var(--na-radius-md, 8px)',
  },
})

/** 图片加载失败标记 */
const failed = ref(false)

/** 解析得到的图片地址 */
const src = computed(() => {
  if (props.coverImageUrl) return props.coverImageUrl
  return foodImage(props.code)
})

/** 是否展示占位块 */
const showPlaceholder = computed(() => !src.value || failed.value)

/** 占位块首字 */
const initial = computed(() => {
  const text = props.name ? String(props.name).trim() : props.code ? String(props.code) : ''
  return text ? text.slice(0, 1) : '食'
})

watch(
  () => [props.code, props.coverImageUrl],
  () => {
    failed.value = false
  },
)

/** 图片加载失败 */
function handleError() {
  failed.value = true
}
</script>

<template>
  <div
    class="na-food-image"
    :style="{ width, height, borderRadius: radius, background: categoryBgVar(categorySlug) }"
  >
    <img
      v-if="!showPlaceholder"
      class="na-food-image__img"
      :src="src"
      :alt="name ? `${name} 配图` : '食品配图'"
      loading="lazy"
      @error="handleError"
    />
    <span v-else class="na-food-image__placeholder" aria-hidden="true">{{ initial }}</span>
  </div>
</template>

<style scoped>
.na-food-image {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--na-color-neutral-100, #f3f4f6);
}

.na-food-image__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.na-food-image__placeholder {
  font-size: 24px;
  font-weight: 600;
  color: var(--na-color-text-muted, #6b7280);
  opacity: 0.7;
}
</style>
