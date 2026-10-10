<script setup>
/**
 * 通用提示条
 *
 * 用于「演示数据」「接口降级」「不确定性提示」等场景。
 * 文案以纯文本渲染；需要强调的关键短语通过 `emphasis` 传入并单独加粗，避免使用 v-html。
 */
import NavIcon from './NavIcon.vue'

defineProps({
  /** 语义类型：`info` | `success` | `warning` | `danger` */
  variant: {
    type: String,
    default: 'info',
  },
  /** 标题（较短） */
  title: {
    type: String,
    default: '',
  },
  /** 正文文案 */
  message: {
    type: String,
    default: '',
  },
  /** 需要强调的短语（会加粗显示在正文前） */
  emphasis: {
    type: String,
    default: '',
  },
  /** 列表内容（如 AI 回答的 uncertainty[]） */
  items: {
    type: Array,
    default: () => [],
  },
  /** 图标名 */
  icon: {
    type: String,
    default: 'info',
  },
  /** 是否可关闭 */
  closable: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['close'])
</script>

<template>
  <div class="na-notice" :class="`na-notice--${variant}`" role="note">
    <NavIcon class="na-notice__icon" :name="icon" :size="18" />

    <div class="na-notice__body">
      <p v-if="title" class="na-notice__title">{{ title }}</p>
      <p v-if="message || emphasis" class="na-notice__message">
        <strong v-if="emphasis" class="na-notice__emphasis">{{ emphasis }}</strong>
        <span v-if="message">{{ message }}</span>
      </p>
      <ul v-if="items.length > 0" class="na-notice__list">
        <li v-for="(item, index) in items" :key="index">{{ item }}</li>
      </ul>
      <slot></slot>
    </div>

    <button v-if="closable" class="na-notice__close" type="button" aria-label="关闭提示" @click="emit('close')">
      ×
    </button>
  </div>
</template>

<style scoped>
.na-notice {
  display: flex;
  align-items: flex-start;
  gap: var(--na-space-2, 8px);
  padding: 10px 12px;
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-left-width: 3px;
  border-radius: var(--na-radius-md, 8px);
  background: var(--na-color-surface, #ffffff);
  font-size: 13px;
  line-height: 1.6;
  color: var(--na-color-text, #111827);
}

.na-notice--info {
  border-left-color: var(--na-color-primary-500, #3b82f6);
  background: var(--na-color-primary-50, #eff6ff);
}

.na-notice--success {
  border-left-color: var(--na-color-success, #10b981);
  background: #ecfdf5;
}

.na-notice--warning {
  border-left-color: var(--na-color-warning, #f59e0b);
  background: #fffbeb;
}

.na-notice--danger {
  border-left-color: var(--na-color-danger, #ef4444);
  background: #fef2f2;
}

.na-notice__icon {
  flex: 0 0 auto;
  margin-top: 2px;
}

.na-notice__body {
  flex: 1 1 auto;
  min-width: 0;
}

.na-notice__title {
  margin: 0 0 2px;
  font-weight: 600;
}

.na-notice__message {
  margin: 0;
}

.na-notice__emphasis {
  margin-right: 4px;
}

.na-notice__list {
  margin: 4px 0 0;
  padding-left: 18px;
}

.na-notice__close {
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: var(--na-radius-sm, 6px);
  background: transparent;
  color: var(--na-color-text-muted, #6b7280);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}
</style>
