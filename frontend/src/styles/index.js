/**
 * 样式装载器 —— 设计令牌容错方案
 *
 * 背景：`src/design/tokens.css` 与 `src/design/base.css` 由另一位同事维护，
 * 在本工程骨架阶段可能尚未生成。若用静态 `import` 引入，缺文件会直接让
 * `vite build` 报 "Failed to resolve import" 而失败。
 *
 * 方案：
 *   1. 先无条件引入本地兜底令牌 `fallback-tokens.css`（永远存在），
 *      保证任何情况下都有完整的 `--na-*` 变量与最小基础样式可用。
 *   2. 用 `import.meta.glob` 探测并动态引入 `design/tokens.css` 与
 *      `design/base.css`。glob 是惰性映射：文件不存在时只是键不存在，
 *      **不会**产生无法解析的 import。
 *   3. 两个文件都存在时，给 `<html>` 加上 `.na-design-ready` 类，
 *      兜底变量的选择器 `:root:not(.na-design-ready)` 随即整体失效，
 *      正式设计令牌完全接管，兜底样式绝不会覆盖正式设计。
 *
 * 注意：这里使用顶层 await，因此 `vite.config.js` 的 `build.target` 设为
 * `esnext`（顶层 await 属于 ES2022）。
 */

import './fallback-tokens.css'
// 本工程自维护的补充类（.na-chip / .na-btn--danger / 共用版式类）。
// 放在 design/*.css 之后引入，保证正式设计令牌就绪时仍能生效且不覆盖设计。
import './components-extra.css'
import './admin.css'

/** 惰性映射：键为相对本文件的路径，值返回 Promise<模块> */
const projectStyles = import.meta.glob('../design/*.css')

/** 按"先令牌、后基础样式"的固定顺序引入，避免依赖 glob 的键顺序 */
const STYLE_ORDER = ['../design/tokens.css', '../design/base.css']

/** 是否成功接入了正式设计样式 */
export let isDesignStylesReady = false

/** 正式设计样式缺失的具体文件名（用于页面提示） */
export const missingDesignStyles = []

/** 记录装载失败的路径与原因，便于排查（不阻断渲染） */
export const styleLoadErrors = []

try {
  for (const stylePath of STYLE_ORDER) {
    const loader = projectStyles[stylePath]
    if (typeof loader !== 'function') {
      missingDesignStyles.push(stylePath)
      continue
    }
    await loader()
  }

  if (missingDesignStyles.length === 0) {
    isDesignStylesReady = true
    // 兜底令牌失效，正式令牌接管
    document.documentElement.classList.add('na-design-ready')
  } else {
    console.warn(
      '[样式] 未找到正式设计样式，已启用 src/styles/fallback-tokens.css 兜底：',
      missingDesignStyles.join('、'),
    )
  }
} catch (error) {
  // 样式文件存在但内容有语法错误时也不能白屏，降级为兜底样式继续渲染
  styleLoadErrors.push(String(error && error.message ? error.message : error))
  console.warn('[样式] 正式设计样式装载失败，已降级为兜底样式：', error)
}
