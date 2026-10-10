<script setup>
/**
 * 全局轻提示容器
 *
 * 固定在右下角，按类型着色；每条提示到点自动消失，也支持手动关闭。
 * 提示内容统一以纯文本渲染（不使用 v-html）。
 */
import { onBeforeUnmount, watch } from 'vue'

import { useToastStore } from '../stores/toast.js'

const toastStore = useToastStore()

/** 每条提示各自的定时器 */
const timers = new Map()

/**
 * 为一条提示安排自动关闭。
 * @param {{id:number, duration:number}} item 提示项
 */
function schedule(item) {
  if (!item.duration || item.duration <= 0) return
  if (timers.has(item.id)) return
  const timer = window.setTimeout(() => {
    toastStore.remove(item.id)
    timers.delete(item.id)
  }, item.duration)
  timers.set(item.id, timer)
}

/** 提示队列变化时补挂定时器并清理已消失项 */
watch(
  () => toastStore.items.map((item) => item.id).join(','),
  () => {
    toastStore.items.forEach(schedule)
    timers.forEach((timer, id) => {
      if (!toastStore.items.some((item) => item.id === id)) {
        window.clearTimeout(timer)
        timers.delete(id)
      }
    })
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  timers.forEach((timer) => window.clearTimeout(timer))
  timers.clear()
})
</script>

<template>
  <div class="na-toast-host" aria-live="polite" aria-atomic="false">
    <TransitionGroup name="na-toast">
      <div
        v-for="item in toastStore.items"
        :key="item.id"
        class="na-toast"
        :class="`na-toast--${item.type}`"
        role="status"
      >
        <span class="na-toast__message">{{ item.message }}</span>
        <button class="na-toast__close" type="button" aria-label="关闭提示" @click="toastStore.remove(item.id)">
          ×
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.na-toast-host {
  position: fixed;
  right: var(--na-space-6, 24px);
  bottom: var(--na-space-6, 24px);
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
  max-width: 360px;
  pointer-events: none;
}

.na-toast {
  pointer-events: auto;
  display: flex;
  align-items: flex-start;
  gap: var(--na-space-2, 8px);
  padding: 10px 12px;
  border-radius: var(--na-radius-md, 8px);
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-left-width: 3px;
  background: var(--na-color-surface, #ffffff);
  box-shadow: var(--na-shadow-lg, 0 16px 40px rgba(15, 23, 42, 0.16));
  font-size: var(--na-font-size-sm, 14px);
  color: var(--na-color-text, #111827);
}

.na-toast--success {
  border-left-color: var(--na-color-success, #10b981);
}

.na-toast--error {
  border-left-color: var(--na-color-danger, #ef4444);
}

.na-toast--warning {
  border-left-color: var(--na-color-warning, #f59e0b);
}

.na-toast--info {
  border-left-color: var(--na-color-primary-500, #3b82f6);
}

.na-toast__message {
  flex: 1 1 auto;
  line-height: 1.5;
  word-break: break-word;
}

.na-toast__close {
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

.na-toast__close:hover {
  background: var(--na-color-bg, #f8fafc);
}

.na-toast-enter-active,
.na-toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.na-toast-enter-from,
.na-toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
