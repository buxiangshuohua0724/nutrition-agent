<script setup>
/**
 * 空状态占位
 *
 * 用于列表无结果、接口无数据、以及"未登录/未选择"等场景。
 * 文案与操作按钮由调用方传入，本组件不做任何业务推断。
 */
import NavIcon from './NavIcon.vue'

defineProps({
  /** 主标题 */
  title: {
    type: String,
    default: '暂无内容',
  },
  /** 补充说明（可为空） */
  description: {
    type: String,
    default: '',
  },
  /** 图标名（assets/icons 下的文件名） */
  icon: {
    type: String,
    default: 'info',
  },
  /** 操作按钮文案；为空则不渲染按钮 */
  actionText: {
    type: String,
    default: '',
  },
  /** 紧凑模式（用于卡片内小面积占位） */
  compact: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['action'])
</script>

<template>
  <div class="na-empty" :class="{ 'na-empty--compact': compact }">
    <NavIcon class="na-empty__icon" :name="icon" :size="compact ? 28 : 40" />
    <p class="na-empty__title">{{ title }}</p>
    <p v-if="description" class="na-empty__description">{{ description }}</p>
    <button v-if="actionText" class="na-btn na-btn--ghost" type="button" @click="emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>

<style scoped>
.na-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--na-space-2, 8px);
  padding: var(--na-space-8, 32px) var(--na-space-4, 16px);
  text-align: center;
  color: var(--na-color-text-muted, #6b7280);
}

.na-empty--compact {
  padding: var(--na-space-4, 16px);
}

.na-empty__icon {
  color: var(--na-color-text-muted, #6b7280);
  opacity: 0.6;
}

.na-empty__title {
  margin: 0;
  font-size: var(--na-font-size-md, 15px);
  font-weight: 600;
  color: var(--na-color-text, #111827);
}

.na-empty__description {
  margin: 0;
  font-size: var(--na-font-size-sm, 14px);
  line-height: 1.6;
  max-width: 420px;
}
</style>
