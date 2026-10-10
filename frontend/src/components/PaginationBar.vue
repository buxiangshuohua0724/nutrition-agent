<script setup>
/**
 * 分页条
 *
 * 与契约的分页结构一致：`{ page, pageSize, total, totalPages }`。
 * 组件不猜总数，`total` 为 0 时整条隐藏。
 */
import { computed } from 'vue'

const props = defineProps({
  /** 当前页（1 起） */
  page: {
    type: Number,
    default: 1,
  },
  /** 每页条数 */
  pageSize: {
    type: Number,
    default: 20,
  },
  /** 总条数 */
  total: {
    type: Number,
    default: 0,
  },
  /** 总页数；缺省时按 total/pageSize 推算（仅用于渲染页码按钮，不代表数据） */
  totalPages: {
    type: Number,
    default: 0,
  },
  /** 是否禁用（加载中） */
  disabled: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:page'])

/** 总页数：优先用接口返回值，其次按总数换算，至少 1 页 */
const pageCount = computed(() => {
  if (props.totalPages > 0) return props.totalPages
  if (props.total > 0 && props.pageSize > 0) return Math.ceil(props.total / props.pageSize)
  return 1
})

/** 需要展示的页码（当前页左右各 2 页，首尾常驻） */
const pageItems = computed(() => {
  const last = pageCount.value
  const current = props.page
  const windowSize = 2
  const items = []
  const start = Math.max(2, current - windowSize)
  const end = Math.min(last - 1, current + windowSize)

  items.push(1)
  if (start > 2) items.push('ellipsis-start')
  for (let page = start; page <= end; page += 1) items.push(page)
  if (end < last - 1) items.push('ellipsis-end')
  if (last > 1) items.push(last)

  return items
})

/**
 * 跳转到指定页。
 * @param {number} target 目标页码
 */
function go(target) {
  if (props.disabled) return
  if (target < 1 || target > pageCount.value || target === props.page) return
  emit('update:page', target)
}
</script>

<template>
  <nav v-if="total > 0" class="na-pagination" aria-label="分页">
    <span class="na-pagination__total">共 {{ total }} 条</span>

    <button class="na-pagination__btn" type="button" :disabled="disabled || page <= 1" @click="go(page - 1)">
      上一页
    </button>

    <ul class="na-pagination__list">
      <li v-for="item in pageItems" :key="String(item)">
        <span v-if="typeof item === 'string'" class="na-pagination__ellipsis">…</span>
        <button
          v-else
          class="na-pagination__page"
          :class="{ 'is-active': item === page }"
          type="button"
          :disabled="disabled"
          :aria-current="item === page ? 'page' : undefined"
          @click="go(item)"
        >
          {{ item }}
        </button>
      </li>
    </ul>

    <button
      class="na-pagination__btn"
      type="button"
      :disabled="disabled || page >= pageCount"
      @click="go(page + 1)"
    >
      下一页
    </button>
  </nav>
</template>

<style scoped>
.na-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: var(--na-space-2, 8px);
  padding: var(--na-space-4, 16px) 0;
  font-size: var(--na-font-size-sm, 14px);
  color: var(--na-color-text-muted, #6b7280);
}

.na-pagination__total {
  margin-right: var(--na-space-2, 8px);
}

.na-pagination__list {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.na-pagination__btn,
.na-pagination__page {
  height: 30px;
  min-width: 30px;
  padding: 0 10px;
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-sm, 6px);
  background: var(--na-color-surface, #ffffff);
  color: var(--na-color-text, #111827);
  font-size: var(--na-font-size-sm, 14px);
  cursor: pointer;
}

.na-pagination__btn:disabled,
.na-pagination__page:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.na-pagination__page.is-active {
  border-color: var(--na-color-primary-500, #3b82f6);
  background: var(--na-color-primary-500, #3b82f6);
  color: #ffffff;
  font-weight: 600;
}

.na-pagination__ellipsis {
  padding: 0 4px;
}
</style>
