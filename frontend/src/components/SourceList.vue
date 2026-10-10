<script setup>
/**
 * 参考来源列表
 *
 * 渲染 `AgentAnswer.sources[]`（由后端工具层注入）或详情页的来源区。
 * 后端未给出 URL 时只展示来源名（纯文本，不用 v-html）。
 */
import { computed } from 'vue'

import { MISSING_TEXT } from '../utils/format'

const props = defineProps({
  /** 来源数组，元素形如 `{title, name, url, snippet, type}` */
  sources: {
    type: Array,
    default: () => [],
  },
  /** 分组标题 */
  title: {
    type: String,
    default: '参考来源',
  },
  /** 紧凑模式 */
  compact: {
    type: Boolean,
    default: false,
  },
})

/** 规范化后的来源项（保留原始字段，仅补展示名） */
const items = computed(() =>
  (Array.isArray(props.sources) ? props.sources : []).map((source, index) => ({
    key: source.id || source.url || `${index}`,
    label: source.title || source.name || source.label || `来源 ${index + 1}`,
    url: source.url || '',
    snippet: source.snippet || source.summary || '',
    type: source.type || '',
  })),
)
</script>

<template>
  <section class="na-source-list" :class="{ 'na-source-list--compact': compact }">
    <h4 v-if="title" class="na-source-list__title">{{ title }}</h4>

    <ol v-if="items.length > 0" class="na-source-list__items">
      <li v-for="item in items" :key="item.key" class="na-source-list__item">
        <a v-if="item.url" class="na-source-list__link" :href="item.url" target="_blank" rel="noopener noreferrer">
          {{ item.label }}
        </a>
        <span v-else class="na-source-list__name">{{ item.label }}</span>

        <span v-if="item.type" class="na-source-list__type">{{ item.type }}</span>
        <p v-if="item.snippet && !compact" class="na-source-list__snippet">{{ item.snippet }}</p>
      </li>
    </ol>

    <p v-else class="na-source-list__empty">{{ MISSING_TEXT }}</p>
  </section>
</template>

<style scoped>
.na-source-list {
  padding: var(--na-space-3, 12px);
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-md, 8px);
  background: var(--na-color-bg, #f8fafc);
}

.na-source-list--compact {
  padding: var(--na-space-2, 8px);
}

.na-source-list__title {
  margin: 0 0 var(--na-space-2, 8px);
  font-size: 13px;
  font-weight: 600;
  color: var(--na-color-text, #111827);
}

.na-source-list__items {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.na-source-list__item {
  font-size: 13px;
  line-height: 1.6;
  color: var(--na-color-text, #111827);
}

.na-source-list__link {
  color: var(--na-color-primary-600, #2563eb);
  text-decoration: none;
}

.na-source-list__link:hover {
  text-decoration: underline;
}

.na-source-list__type {
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 8px;
  background: var(--na-color-neutral-100, #f3f4f6);
  color: var(--na-color-text-muted, #6b7280);
  font-size: 11px;
}

.na-source-list__snippet {
  margin: 2px 0 0;
  color: var(--na-color-text-muted, #6b7280);
  font-size: 12px;
}

.na-source-list__empty {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #9ca3af);
  font-style: italic;
}
</style>
