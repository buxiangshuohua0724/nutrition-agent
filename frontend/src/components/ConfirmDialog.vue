<script setup>
/**
 * 二次确认对话框
 *
 * 用 `v-if` 控制显隐（由父组件通过 `open` 传入）。确认/取消都只发事件，
 * 具体动作由父组件执行，避免组件内隐藏副作用。
 */
import { onBeforeUnmount, watch } from 'vue'

const props = defineProps({
  /** 是否显示 */
  open: {
    type: Boolean,
    default: false,
  },
  /** 标题 */
  title: {
    type: String,
    default: '请确认操作',
  },
  /** 正文说明 */
  message: {
    type: String,
    default: '',
  },
  /** 确认按钮文案 */
  confirmText: {
    type: String,
    default: '确认',
  },
  /** 取消按钮文案 */
  cancelText: {
    type: String,
    default: '取消',
  },
  /** 危险操作（确认按钮用红色） */
  danger: {
    type: Boolean,
    default: false,
  },
  /** 确认按钮加载态 */
  loading: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['confirm', 'cancel'])

/** Esc 关闭 */
function handleKeydown(event) {
  if (event.key === 'Escape') emit('cancel')
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      window.addEventListener('keydown', handleKeydown)
    } else {
      window.removeEventListener('keydown', handleKeydown)
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div v-if="open" class="na-dialog-mask" role="presentation" @click.self="emit('cancel')">
    <div class="na-dialog" role="dialog" aria-modal="true" :aria-label="title">
      <h2 class="na-dialog__title">{{ title }}</h2>
      <p v-if="message" class="na-dialog__message">{{ message }}</p>
      <div class="na-dialog__actions">
        <button class="na-btn na-btn--ghost" type="button" :disabled="loading" @click="emit('cancel')">
          {{ cancelText }}
        </button>
        <button
          class="na-btn"
          :class="danger ? 'na-btn--danger' : 'na-btn--primary'"
          type="button"
          :disabled="loading"
          @click="emit('confirm')"
        >
          {{ loading ? '处理中…' : confirmText }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.na-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--na-space-4, 16px);
  background: rgba(15, 23, 42, 0.45);
}

.na-dialog {
  width: 100%;
  max-width: 400px;
  padding: var(--na-space-5, 20px);
  border-radius: var(--na-radius-lg, 12px);
  background: var(--na-color-surface, #ffffff);
  box-shadow: var(--na-shadow-lg, 0 16px 40px rgba(15, 23, 42, 0.16));
}

.na-dialog__title {
  margin: 0 0 var(--na-space-2, 8px);
  font-size: var(--na-font-size-lg, 17px);
  font-weight: 600;
  color: var(--na-color-text, #111827);
}

.na-dialog__message {
  margin: 0 0 var(--na-space-4, 16px);
  font-size: var(--na-font-size-sm, 14px);
  line-height: 1.6;
  color: var(--na-color-text-muted, #6b7280);
  white-space: pre-line;
}

.na-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--na-space-2, 8px);
}
</style>
