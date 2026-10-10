<script setup>
/**
 * 分类 chips（横排圆形分类徽章）
 *
 * 支持"全部"选项（`allowAll`）。分类图来自 `assets/categories/*.svg`，
 * 命名与契约 slug 可能不一致，由 `utils/assets.js` 统一做别名兼容；取不到图时渲染首字占位。
 */
import { computed } from 'vue'

import NavIcon from './NavIcon.vue'
import { categoryImage } from '../utils/assets'

const props = defineProps({
  /** 分类数组，元素形如 `{id, slug, name, foodCount}` */
  categories: {
    type: Array,
    default: () => [],
  },
  /** 当前选中的 slug；'' 或 null 表示未选中 */
  modelValue: {
    type: [String, Number, null],
    default: '',
  },
  /** 是否显示「全部」 */
  allowAll: {
    type: Boolean,
    default: true,
  },
  /** 「全部」的取值 */
  allValue: {
    type: [String, Number],
    default: '',
  },
  /** 每个 chip 是否显示食品数量 */
  showCount: {
    type: Boolean,
    default: false,
  },
  /** 尺寸：`md` | `sm` */
  size: {
    type: String,
    default: 'md',
  },
})

const emit = defineEmits(['update:modelValue', 'change'])

/**
 * 把分类对象归一化为展示结构。
 * @param {object} category
 * @returns {{key: string|number, slug: string, name: string, count: number|null}}
 */
function normalize(category) {
  return {
    key: category.id !== undefined && category.id !== null ? category.id : category.slug,
    slug: category.slug || '',
    name: category.name || category.slug || '未命名分类',
    count: typeof category.foodCount === 'number' ? category.foodCount : null,
  }
}

/** 归一化后的分类列表 */
const items = computed(() => {
  const list = (Array.isArray(props.categories) ? props.categories : []).map(normalize)
  if (!props.allowAll) return list
  return [{ key: '__all__', slug: props.allValue, name: '全部', count: null }, ...list]
})

/**
 * 判断某一项是否选中（用 slug 比较，数字/字符串宽松处理）。
 * @param {string} slug
 * @returns {boolean}
 */
function isActive(slug) {
  return String(props.modelValue ?? '') === String(slug ?? '')
}

/**
 * 点击某一项。
 * @param {string} slug
 */
function select(slug) {
  if (isActive(slug)) return
  emit('update:modelValue', slug)
  emit('change', slug)
}

/**
 * 取分类徽章图。
 * @param {string} slug
 * @returns {string|null}
 */
function imageOf(slug) {
  if (!slug) return null
  return categoryImage(slug)
}
</script>

<template>
  <div class="na-category-chips" :class="`na-category-chips--${size}`" role="list">
    <button
      v-for="item in items"
      :key="String(item.key)"
      class="na-category-chips__item"
      :class="{ 'is-active': isActive(item.slug) }"
      type="button"
      role="listitem"
      :aria-pressed="isActive(item.slug)"
      @click="select(item.slug)"
    >
      <span class="na-category-chips__badge">
        <img v-if="imageOf(item.slug)" :src="imageOf(item.slug)" :alt="`${item.name} 分类图标`" loading="lazy" />
        <NavIcon v-else :name="item.slug ? 'category' : 'filter'" :size="20" />
      </span>
      <span class="na-category-chips__name">{{ item.name }}</span>
      <span v-if="showCount && item.count !== null" class="na-category-chips__count">{{ item.count }}</span>
    </button>
  </div>
</template>

<style scoped>
.na-category-chips {
  display: flex;
  align-items: flex-start;
  gap: var(--na-space-3, 12px);
  overflow-x: auto;
  padding: var(--na-space-1, 4px) 0 var(--na-space-2, 8px);
}

.na-category-chips__item {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--na-color-text-muted, #6b7280);
  font-size: 12px;
}

.na-category-chips__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: 2px solid transparent;
  background: var(--na-color-neutral-100, #f3f4f6);
  overflow: hidden;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.na-category-chips--sm .na-category-chips__badge {
  width: 42px;
  height: 42px;
}

.na-category-chips__badge img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.na-category-chips__item:hover .na-category-chips__badge {
  border-color: var(--na-color-primary-200, #bfdbfe);
}

.na-category-chips__item.is-active {
  color: var(--na-color-primary-600, #2563eb);
  font-weight: 600;
}

.na-category-chips__item.is-active .na-category-chips__badge {
  border-color: var(--na-color-primary-500, #3b82f6);
  background: var(--na-color-primary-50, #eff6ff);
}

.na-category-chips__name {
  max-width: 64px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.na-category-chips__count {
  font-size: 11px;
  opacity: 0.7;
}
</style>
