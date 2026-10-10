<script setup>
/**
 * 食品卡片
 *
 * 展示插画、名称、分类徽章与可选的热量文字。
 * **热量文字直接使用接口给出的文案**（`energyText` 或 `energyKcal` + 单位），
 * 缺失时显示「暂无数据」，绝不用 0 代替（工程硬约束）。
 */
import { computed } from 'vue'

import FoodImage from './FoodImage.vue'
import NavIcon from './NavIcon.vue'
import { MISSING_TEXT, formatNumber } from '../utils/format'

const props = defineProps({
  /** 食品对象（契约 FoodSummary 结构） */
  food: {
    type: Object,
    required: true,
  },
  /** 分类中文名（接口附带或由父组件映射） */
  categoryName: {
    type: String,
    default: '',
  },
  /** 是否已收藏 */
  favorite: {
    type: Boolean,
    default: false,
  },
  /** 是否显示收藏按钮 */
  showFavorite: {
    type: Boolean,
    default: true,
  },
  /** 是否显示热量行 */
  showEnergy: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['toggle-favorite'])

/** 详情链接 */
const detailTo = computed(() => `/food/${props.food.id}`)

/** 分类展示名：优先接口/父组件传入，其次回落到 slug */
const categoryLabel = computed(() => props.categoryName || props.food.categoryName || props.food.categorySlug || '')

/**
 * 热量文案：优先使用接口提供的文本，否则用数值 + 单位。
 * 数值缺失时显示「暂无数据」，不做任何换算或补零。
 */
const energyText = computed(() => {
  if (props.food.energyText) return props.food.energyText
  if (props.food.energyKcal === null || props.food.energyKcal === undefined) return MISSING_TEXT
  const unit = props.food.energyUnit || 'kcal'
  return `${formatNumber(props.food.energyKcal)} ${unit}`
})

/** 点击收藏按钮时阻止跳转 */
function handleFavorite(event) {
  event.preventDefault()
  event.stopPropagation()
  emit('toggle-favorite', props.food)
}
</script>

<template>
  <article class="na-food-card na-card">
    <RouterLink class="na-food-card__link" :to="detailTo">
      <FoodImage
        class="na-food-card__image"
        :code="food.code"
        :name="food.name"
        :cover-image-url="food.coverImageUrl || ''"
        :category-slug="food.categorySlug || ''"
        height="120px"
      />

      <div class="na-food-card__body">
        <h3 class="na-food-card__name">{{ food.name }}</h3>

        <p v-if="categoryLabel" class="na-food-card__category">
          <span class="na-badge">{{ categoryLabel }}</span>
        </p>

        <p v-if="showEnergy" class="na-food-card__energy">
          <span v-if="food.energyAvailable === false" class="na-food-card__energy-missing">
            {{ MISSING_TEXT }}
          </span>
          <span v-else>{{ energyText }} / 100g</span>
        </p>
      </div>
    </RouterLink>

    <button
      v-if="showFavorite"
      class="na-food-card__favorite"
      type="button"
      :aria-pressed="favorite"
      :aria-label="favorite ? '取消收藏' : '加入收藏'"
      @click="handleFavorite"
    >
      <NavIcon :name="favorite ? 'heart-filled' : 'heart'" :size="18" />
    </button>
  </article>
</template>

<style scoped>
.na-food-card {
  position: relative;
  overflow: hidden;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.na-food-card:hover {
  box-shadow: var(--na-shadow-md, 0 8px 24px rgba(15, 23, 42, 0.12));
  transform: translateY(-2px);
}

.na-food-card__link {
  display: block;
  color: inherit;
  text-decoration: none;
}

.na-food-card__image {
  width: 100%;
}

.na-food-card__body {
  padding: var(--na-space-3, 12px);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.na-food-card__name {
  margin: 0;
  font-size: var(--na-font-size-md, 15px);
  font-weight: 600;
  color: var(--na-color-text, #111827);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.na-food-card__category {
  margin: 0;
}

.na-food-card__energy {
  margin: 0;
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-food-card__energy-missing {
  font-style: italic;
}

.na-food-card__favorite {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  color: var(--na-color-danger, #ef4444);
  cursor: pointer;
}

.na-food-card__favorite:hover {
  background: #ffffff;
}
</style>
