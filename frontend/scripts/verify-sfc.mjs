/**
 * 全量 .vue / .js 静态复核脚本（不依赖 Vite 与 esbuild 子进程）。
 *
 * 为什么需要它：
 *   `vite build` 会经过 esbuild 的可执行文件，在某些沙箱环境（如 DSH 的
 *   workspace-write 模式禁止管道）下会因 EPERM 失败。本脚本改用 Node 直接调用
 *   `@vue/compiler-sfc` 对每个单文件组件做**真实编译**（script + template），
 *   在不启动构建器的前提下拿到等价的结构性错误，适合当作提交前的快速门禁。
 *
 * 除编译外还检查几项本项目特有的约定：
 *   · 禁止出现 `v-html`（PRD 要求展示内容一律转义，避免注入）
 *   · 禁止 `|| 0` / `?? 0` 兜底（营养三态要求「未知」绝不能塌成 0，见 PRD 4.4）
 *   · 不使用 CSS 预处理器（项目只用原生 CSS + 设计令牌变量）
 *
 * 用法：node scripts/verify-sfc.mjs
 * 退出码：0 = 全部通过，1 = 有问题，2 = 环境缺依赖
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import process from 'node:process'

const require = createRequire(import.meta.url)
const root = path.join(process.cwd(), 'src')

function walk(dir) {
  const out = []
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry)
    if (statSync(full).isDirectory()) out.push(...walk(full))
    else out.push(full)
  }
  return out
}

let sfc
try {
  sfc = require('@vue/compiler-sfc')
  if (sfc && sfc.default && sfc.default.parse) sfc = sfc.default
} catch (error) {
  // pnpm 的 node_modules 是严格布局：只有 package.json 里**显式声明**的依赖
  // 才能被 require 到，@vitejs/plugin-vue 的传递依赖 @vue/compiler-sfc 不在
  // 顶层可见范围。这里退一步到 pnpm 的 .pnpm 目录里按目录名找它，
  // 避免为了一个复核脚本再去改 package.json。
  const pnpmDir = path.join(process.cwd(), 'node_modules', '.pnpm')
  let found = null
  try {
    for (const entry of readdirSync(pnpmDir)) {
      if (!entry.startsWith('@vue+compiler-sfc@')) continue
      const candidate = path.join(
        pnpmDir, entry, 'node_modules', '@vue', 'compiler-sfc', 'dist', 'compiler-sfc.cjs.js',
      )
      if (statSync(candidate).isFile()) {
        found = candidate
        break
      }
    }
  } catch {
    found = null
  }
  if (!found) {
    console.log('无法加载 @vue/compiler-sfc：' + error.message)
    console.log('请先在 frontend/ 下执行 `pnpm install`。')
    process.exit(2)
  }
  sfc = require(found)
  if (sfc && sfc.default && sfc.default.parse) sfc = sfc.default
}

const files = walk(root)
const vueFiles = files.filter((f) => f.endsWith('.vue'))
const jsFiles = files.filter((f) => f.endsWith('.js'))
const problems = []

/**
 * 去掉注释后再做「禁止写法」检查。
 *
 * 为什么必须去注释：这些规则本身要在文档注释、组件头注释里被**说明**，
 * 例如 RichText.vue 的头注释写着"不使用 v-html"、format.js 的注释里写着
 * "不允许出现 value ?? 0"。若不过滤注释，说明文字本身就会把门禁弄红，
 * 久而久之大家就会习惯性忽略这条检查。
 *
 * 处理范围：块注释、行注释、以及 HTML 模板注释。
 * 这只是启发式的文本过滤（不是真正的语法分析），但对"是否存在禁止写法"
 * 这个判断足够可靠：真出现 `v-html="..."` 或 `?? 0` 一定在代码里，不会被误伤。
 * @param {string} source 文件原文
 * @returns {string} 去掉注释后的文本
 */
function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1 ')
    .replace(/<!--[\s\S]*?-->/g, ' ')
}

for (const file of vueFiles) {
  const rel = path.relative(process.cwd(), file)
  const source = readFileSync(file, 'utf8')
  const { descriptor, errors } = sfc.parse(source, { filename: file })
  if (errors && errors.length) {
    for (const e of errors) problems.push(`[parse] ${rel}: ${e.message}`)
    continue
  }
  try {
    const compiled = sfc.compileScript(descriptor, { id: rel })
    if (!compiled.content) problems.push(`[script] ${rel}: empty output`)
  } catch (error) {
    problems.push(`[script] ${rel}: ${error.message}`)
  }
  if (descriptor.template) {
    const result = sfc.compileTemplate({
      source: descriptor.template.content,
      filename: file,
      id: rel,
      compilerOptions: { bindingMetadata: {} },
      slotted: false,
    })
    if (result.errors && result.errors.length) {
      for (const e of result.errors) problems.push(`[template] ${rel}: ${e.message || e}`)
    }
  } else {
    problems.push(`[template] ${rel}: 缺少 <template>`)
  }
  for (const style of descriptor.styles) {
    if (style.lang) problems.push(`[style] ${rel}: 使用了预处理器 lang=${style.lang}`)
  }
}

for (const file of [...vueFiles, ...jsFiles]) {
  const rel = path.relative(process.cwd(), file)
  const code = stripComments(readFileSync(file, 'utf8'))
  if (/\bv-html\b/.test(code)) {
    problems.push(`[v-html] ${rel}: 出现 v-html（应改用 RichText/纯文本渲染）`)
  }
  if (/\|\|\s*0\b/.test(code) || /\?\?\s*0\b/.test(code)) {
    problems.push(`[零兜底] ${rel}: 出现 || 0 或 ?? 0 —— 缺失值绝不能塌成 0，请人工确认`)
  }
}

console.log(`checked: ${vueFiles.length} vue, ${jsFiles.length} js`)
if (problems.length === 0) {
  console.log('ALL OK')
  process.exit(0)
}
console.log(`problems: ${problems.length}`)
for (const p of problems) console.log(' -', p)
process.exit(1)
