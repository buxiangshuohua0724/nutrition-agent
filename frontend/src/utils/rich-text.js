/**
 * 安全富文本渲染（不使用 `v-html`）
 *
 * 契约与工程约束要求：**不得用 `v-html` 渲染后端内容**（XSS 风险）。
 * 这里把 markdown 的极小子集解析成"结构化节点数组"，由模板用 `v-for` + 元素渲染，
 * 任何 HTML 标签都只是纯文本，不会被浏览器解析执行。
 *
 * 支持的语法（故意保持极小）：
 *   - 空行分段；
 *   - `**加粗**` / `__加粗__`；
 *   - 行首 `- ` / `* ` / `1. ` 列表（渲染为列表块）；
 *   - 行首 `# ` ~ `###### ` 标题；
 *   - 行首 emoji 作为小标题行时按普通段落处理，保持原样。
 *
 * @param {string|null|undefined} raw 原始文本
 * @returns {Array<object>} 块数组
 */

/** 行内加粗解析 */
function parseInline(text) {
  const nodes = []
  const pattern = /\*\*(.+?)\*\*|__(.+?)__/g
  let lastIndex = 0
  let match = pattern.exec(text)

  while (match !== null) {
    if (match.index > lastIndex) {
      nodes.push({ type: 'text', text: text.slice(lastIndex, match.index) })
    }
    const boldText = match[1] !== undefined ? match[1] : match[2]
    nodes.push({ type: 'bold', text: boldText })
    lastIndex = pattern.lastIndex
    match = pattern.exec(text)
  }

  if (lastIndex < text.length) {
    nodes.push({ type: 'text', text: text.slice(lastIndex) })
  }

  return nodes.length > 0 ? nodes : [{ type: 'text', text: '' }]
}

/** 判断是否为无序列表行 */
function matchUnorderedItem(line) {
  const match = /^\s*[-*+]\s+(.*)$/.exec(line)
  return match ? match[1] : null
}

/** 判断是否为有序列表行 */
function matchOrderedItem(line) {
  const match = /^\s*(\d+)[.、)]\s+(.*)$/.exec(line)
  return match ? { order: Number(match[1]), text: match[2] } : null
}

/** 判断是否为标题行 */
function matchHeading(line) {
  const match = /^\s*(#{1,6})\s+(.*)$/.exec(line)
  return match ? { level: match[1].length, text: match[2] } : null
}

/**
 * 把原始文本解析为块数组。
 * @param {string|null|undefined} raw
 * @returns {Array<object>} 块数组，元素形如
 *   `{ type: 'paragraph'|'heading'|'list', ... }`，文本已解析为行内节点数组
 */
export function parseRichText(raw) {
  if (raw === null || raw === undefined) return []
  const text = String(raw).replace(/\r\n?/g, '\n')
  const lines = text.split('\n')
  const blocks = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]

    // 空行：跳过（段落之间靠空行分隔）
    if (line.trim() === '') {
      index += 1
      continue
    }

    // 标题
    const heading = matchHeading(line)
    if (heading) {
      blocks.push({ type: 'heading', level: heading.level, content: parseInline(heading.text) })
      index += 1
      continue
    }

    // 无序列表
    const firstUnordered = matchUnorderedItem(line)
    if (firstUnordered !== null) {
      const items = []
      while (index < lines.length) {
        const item = matchUnorderedItem(lines[index])
        if (item === null) break
        items.push(parseInline(item))
        index += 1
      }
      blocks.push({ type: 'list', ordered: false, items })
      continue
    }

    // 有序列表
    const firstOrdered = matchOrderedItem(line)
    if (firstOrdered !== null) {
      const items = []
      while (index < lines.length) {
        const item = matchOrderedItem(lines[index])
        if (item === null) break
        items.push(parseInline(item.text))
        index += 1
      }
      blocks.push({ type: 'list', ordered: true, items })
      continue
    }

    // 普通段落：连续非空行合并为一个段落（保留单换行）
    const paragraphLines = []
    while (index < lines.length && lines[index].trim() !== '') {
      const current = lines[index]
      if (matchHeading(current) || matchUnorderedItem(current) !== null || matchOrderedItem(current) !== null) {
        break
      }
      paragraphLines.push(current)
      index += 1
    }
    if (paragraphLines.length > 0) {
      blocks.push({ type: 'paragraph', content: parseInline(paragraphLines.join('\n')) })
    }
  }

  return blocks
}

/**
 * 把块数组里的行内节点拼成纯文本（用于复制、摘要、无障碍朗读）。
 * @param {Array<object>} blocks
 * @returns {string}
 */
export function richTextToPlain(blocks) {
  if (!Array.isArray(blocks)) return ''
  return blocks
    .map((block) => {
      if (block.type === 'list') {
        return block.items.map((nodes) => nodes.map((node) => node.text).join('')).join('\n')
      }
      if (block.type === 'heading') {
        return block.content.map((node) => node.text).join('')
      }
      if (block.type === 'paragraph') {
        return block.content.map((node) => node.text).join('')
      }
      return ''
    })
    .filter((text) => text !== '')
    .join('\n\n')
}

/** 组合式封装：把文本解析成块数组（响应式） */
import { computed } from 'vue'

/**
 * @param {import('vue').Ref<string|null|undefined>|(() => string|null|undefined)} source 文本来源
 * @returns {{blocks: import('vue').ComputedRef<Array<object>>}}
 */
export function useRichText(source) {
  const blocks = computed(() => parseRichText(typeof source === 'function' ? source() : source.value))
  return { blocks }
}
