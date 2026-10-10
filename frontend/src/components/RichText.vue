<script setup>
/**
 * 安全富文本渲染组件
 *
 * 依据任务硬约束：**不使用 `v-html` 渲染任何来自后端的内容**。
 * 这里把 markdown 子集（标题 / 无序列表 / 有序列表 / 段落 / `**加粗**`）
 * 解析成纯数据块，再用 Vue 模板逐节点渲染成真实 DOM 元素，
 * 因此即使后端返回 `<script>` 也只会被当作普通文本显示。
 *
 * 解析函数见 `utils/rich-text.js`（同样是纯字符串处理，不产生 HTML）。
 */
import { computed } from 'vue'
import { parseRichText } from '../utils/rich-text'

const props = defineProps({
  /** 原始文本（可能包含 markdown 子集） */
  source: { type: String, default: '' },
  /** 空内容占位文案 */
  placeholder: { type: String, default: '暂无内容。' },
})

/** 解析后的块数组 */
const blocks = computed(() => parseRichText(props.source || ''))

/** 是否真的没有内容（为空或只有空白） */
const isEmpty = computed(() => blocks.value.length === 0)
</script>

<template>
  <div class="na-rich-text">
    <p v-if="isEmpty" class="na-rich-text__placeholder">{{ placeholder }}</p>

    <template v-else>
      <template v-for="(block, blockIndex) in blocks" :key="blockIndex">
        <!-- 段落 -->
        <p v-if="block.type === 'paragraph'" class="na-rich-text__paragraph">
          <template v-for="(node, nodeIndex) in block.content" :key="nodeIndex">
            <strong v-if="node.type === 'bold'">{{ node.text }}</strong>
            <span v-else>{{ node.text }}</span>
          </template>
        </p>

        <!-- 标题：按层级渲染 h3~h6，避免与页面 h1/h2 冲突 -->
        <component :is="`h${Math.min(6, Math.max(3, (block.level || 3) + 2))}`" v-else-if="block.type === 'heading'" class="na-rich-text__heading">
          <template v-for="(node, nodeIndex) in block.content" :key="nodeIndex">
            <strong v-if="node.type === 'bold'">{{ node.text }}</strong>
            <span v-else>{{ node.text }}</span>
          </template>
        </component>

        <!-- 列表 -->
        <component :is="block.ordered ? 'ol' : 'ul'" v-else-if="block.type === 'list'" class="na-rich-text__list">
          <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
            <template v-for="(node, nodeIndex) in item" :key="nodeIndex">
              <strong v-if="node.type === 'bold'">{{ node.text }}</strong>
              <span v-else>{{ node.text }}</span>
            </template>
          </li>
        </component>
      </template>
    </template>
  </div>
</template>

<style scoped>
.na-rich-text {
  font-size: 14px;
  line-height: 1.8;
  color: var(--na-color-text, #111827);
}

.na-rich-text__paragraph {
  margin: 0 0 var(--na-space-3, 12px);
  white-space: pre-wrap;
  word-break: break-word;
}

.na-rich-text__heading {
  margin: var(--na-space-4, 16px) 0 var(--na-space-2, 8px);
  font-size: 15px;
  font-weight: 600;
}

.na-rich-text__list {
  margin: 0 0 var(--na-space-3, 12px);
  padding-left: 22px;
}

.na-rich-text__list li {
  margin-bottom: 4px;
}

.na-rich-text__placeholder {
  margin: 0;
  color: var(--na-color-text-muted, #6b7280);
  font-style: italic;
}
</style>
