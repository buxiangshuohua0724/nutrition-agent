<script setup>
/**
 * 图片上传区（虚线框 + 拖拽 + 点击选择）
 *
 * 只做**前端预校验**（MIME 类型与体积），不读取文件内容、不做魔数校验
 * （契约要求魔数校验由后端完成，前端不假装做了）。
 */
import { computed, ref } from 'vue'

import NavIcon from './NavIcon.vue'
import { ALLOWED_IMAGE_TYPES, MAX_IMAGE_SIZE, validateImageFile } from '../utils/validators'
import { formatFileSize } from '../utils/format'

const props = defineProps({
  /** 已选文件（受控） */
  file: {
    type: Object,
    default: null,
  },
  /** 是否禁用 */
  disabled: {
    type: Boolean,
    default: false,
  },
  /** 主提示文案 */
  hint: {
    type: String,
    default: '点击选择图片，或把图片拖到这里',
  },
  /** 是否显示预览 */
  preview: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['update:file', 'invalid', 'clear'])

const inputRef = ref(null)
const dragging = ref(false)

/** 允许的格式说明 */
const acceptAttr = computed(() => ALLOWED_IMAGE_TYPES.join(','))

/** 说明文案（格式 + 体积上限） */
const tips = computed(
  () => `支持 JPG / PNG / WebP，单张不超过 ${formatFileSize(MAX_IMAGE_SIZE)}`,
)

/** 预览地址 */
const previewUrl = computed(() => {
  if (!props.file || typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') return ''
  return URL.createObjectURL(props.file)
})

/** 打开系统文件选择框 */
function openPicker() {
  if (props.disabled) return
  if (inputRef.value) inputRef.value.click()
}

/**
 * 处理选中的文件。
 * @param {File|null} selected
 */
function handleFile(selected) {
  if (!selected) return
  const result = validateImageFile(selected)
  if (!result.valid) {
    emit('invalid', { code: result.code, message: result.message, file: selected })
    return
  }
  emit('update:file', selected)
}

/**
 * input change。
 * @param {Event} event
 */
function handleInputChange(event) {
  const files = event.target.files
  handleFile(files && files.length > 0 ? files[0] : null)
  // 允许重复选择同一文件
  event.target.value = ''
}

/**
 * 拖拽进入。
 * @param {DragEvent} event
 */
function handleDragEnter(event) {
  event.preventDefault()
  if (props.disabled) return
  dragging.value = true
}

/**
 * 拖拽离开。
 * @param {DragEvent} event
 */
function handleDragLeave(event) {
  event.preventDefault()
  dragging.value = false
}

/**
 * 放下文件。
 * @param {DragEvent} event
 */
function handleDrop(event) {
  event.preventDefault()
  dragging.value = false
  if (props.disabled) return
  const files = event.dataTransfer && event.dataTransfer.files
  handleFile(files && files.length > 0 ? files[0] : null)
}

/** 清除已选文件 */
function clearFile() {
  emit('update:file', null)
  emit('clear')
}
</script>

<template>
  <div class="na-dropzone">
    <div
      class="na-dropzone__box"
      :class="{ 'is-dragging': dragging, 'is-disabled': disabled, 'has-file': Boolean(file) }"
      role="button"
      tabindex="0"
      :aria-disabled="disabled"
      @click="openPicker"
      @keydown.enter.prevent="openPicker"
      @keydown.space.prevent="openPicker"
      @dragenter="handleDragEnter"
      @dragover.prevent
      @dragleave="handleDragLeave"
      @drop="handleDrop"
    >
      <template v-if="file && preview">
        <img class="na-dropzone__preview" :src="previewUrl" :alt="file.name" />
      </template>
      <template v-else>
        <NavIcon class="na-dropzone__icon" name="upload" :size="32" />
      </template>

      <p class="na-dropzone__hint">{{ hint }}</p>
      <p class="na-dropzone__tips">{{ tips }}</p>

      <input
        ref="inputRef"
        class="na-dropzone__input na-sr-only"
        type="file"
        :accept="acceptAttr"
        :disabled="disabled"
        @change="handleInputChange"
      />
    </div>

    <div v-if="file" class="na-dropzone__meta">
      <span class="na-dropzone__file">{{ file.name }}（{{ formatFileSize(file.size) }}）</span>
      <button class="na-btn na-btn--ghost na-btn--sm" type="button" :disabled="disabled" @click.stop="clearFile">
        重新选择
      </button>
    </div>
  </div>
</template>

<style scoped>
.na-dropzone {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
}

.na-dropzone__box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 200px;
  padding: var(--na-space-5, 20px);
  border: 2px dashed var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-lg, 12px);
  background: var(--na-color-bg, #f8fafc);
  color: var(--na-color-text-muted, #6b7280);
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
  text-align: center;
}

.na-dropzone__box:hover,
.na-dropzone__box.is-dragging {
  border-color: var(--na-color-primary-500, #3b82f6);
  background: var(--na-color-primary-50, #eff6ff);
}

.na-dropzone__box.is-disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.na-dropzone__icon {
  color: var(--na-color-text-muted, #6b7280);
}

.na-dropzone__preview {
  max-width: 100%;
  max-height: 240px;
  border-radius: var(--na-radius-md, 8px);
  object-fit: contain;
}

.na-dropzone__hint {
  margin: 0;
  font-size: var(--na-font-size-sm, 14px);
  color: var(--na-color-text, #111827);
}

.na-dropzone__tips {
  margin: 0;
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-dropzone__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--na-space-2, 8px);
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-dropzone__file {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
