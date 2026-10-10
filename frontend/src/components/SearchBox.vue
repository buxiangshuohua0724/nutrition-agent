<script setup>
/**
 * 搜索框
 *
 * 支持 `v-model` 与 `submit` 事件；右侧附带「上传图片识别」入口提示。
 * 组件本身不发起请求，也不做任何过滤/推断。
 */
import { ref, watch } from 'vue'

import NavIcon from './NavIcon.vue'

const props = defineProps({
  /** 搜索关键词 */
  modelValue: {
    type: String,
    default: '',
  },
  /** 占位提示 */
  placeholder: {
    type: String,
    default: '请输入食品名称，或上传图片识别',
  },
  /** 尺寸：`lg`（首页大搜索框）| `md` */
  size: {
    type: String,
    default: 'md',
  },
  /** 是否显示「上传图片识别」入口 */
  showImageHint: {
    type: Boolean,
    default: true,
  },
  /** 是否自动聚焦 */
  autofocus: {
    type: Boolean,
    default: false,
  },
  /** 按钮文案；为空则只显示图标按钮 */
  buttonText: {
    type: String,
    default: '搜索',
  },
})

const emit = defineEmits(['update:modelValue', 'submit', 'submit-image'])

const inputRef = ref(null)

watch(
  () => props.autofocus,
  (value) => {
    if (value && inputRef.value) {
      inputRef.value.focus()
    }
  },
  { immediate: true },
)

/**
 * 输入变化时同步给父组件。
 * @param {Event} event
 */
function handleInput(event) {
  emit('update:modelValue', event.target.value)
}

/**
 * 提交搜索：关键词去空格后回抛，空关键词也会抛出（由父组件决定是否忽略）。
 * @param {Event} event
 */
function handleSubmit(event) {
  event.preventDefault()
  emit('submit', props.modelValue.trim())
}

/** 点击「上传图片识别」跳转 */
function goRecognize() {
  emit('submit-image')
}
</script>

<template>
  <form class="na-search-box" :class="`na-search-box--${size}`" role="search" @submit="handleSubmit">
    <NavIcon class="na-search-box__icon" name="search" :size="size === 'lg' ? 20 : 16" />
    <input
      ref="inputRef"
      class="na-search-box__input"
      type="search"
      :value="modelValue"
      :placeholder="placeholder"
      :aria-label="placeholder"
      @input="handleInput"
    />
    <button v-if="buttonText" class="na-search-box__btn" type="submit">{{ buttonText }}</button>
    <button v-else class="na-search-box__icon-btn" type="submit" aria-label="搜索">
      <NavIcon name="search" :size="18" />
    </button>
  </form>

  <p v-if="showImageHint" class="na-search-box__hint">
    也可以
    <button class="na-search-box__hint-link" type="button" @click="goRecognize">上传图片识别</button>
    食品（支持 JPG / PNG / WebP，单张不超过 10MB）
  </p>
</template>

<style scoped>
.na-search-box {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
  height: var(--na-control-height-md, 36px);
  padding: 0 4px 0 12px;
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-md, 8px);
  background: var(--na-color-surface, #ffffff);
  color: var(--na-color-text-muted, #6b7280);
}

.na-search-box--lg {
  height: 48px;
  border-radius: var(--na-radius-lg, 12px);
  padding-left: 16px;
}

.na-search-box__icon {
  flex: 0 0 auto;
}

.na-search-box__input {
  flex: 1 1 auto;
  min-width: 0;
  height: 100%;
  border: none;
  background: transparent;
  outline: none;
  font-size: var(--na-font-size-sm, 14px);
  color: var(--na-color-text, #111827);
}

.na-search-box--lg .na-search-box__input {
  font-size: var(--na-font-size-md, 15px);
}

.na-search-box__btn {
  flex: 0 0 auto;
  height: calc(100% - 8px);
  padding: 0 18px;
  border: none;
  border-radius: var(--na-radius-sm, 6px);
  background: var(--na-color-primary-500, #3b82f6);
  color: #ffffff;
  font-size: var(--na-font-size-sm, 14px);
  cursor: pointer;
}

.na-search-box__icon-btn {
  flex: 0 0 auto;
  width: 32px;
  height: calc(100% - 8px);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--na-radius-sm, 6px);
  background: transparent;
  color: var(--na-color-text-muted, #6b7280);
  cursor: pointer;
}

.na-search-box__hint {
  margin: var(--na-space-2, 8px) 0 0;
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-search-box__hint-link {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--na-color-primary-600, #2563eb);
  font-size: 12px;
  text-decoration: underline;
  cursor: pointer;
}
</style>
