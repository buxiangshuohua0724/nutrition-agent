/**
 * Node 加载钩子：给无扩展名的相对 import 补上 `.js` 再解析。
 *
 * 前端源码用的是 Vite 风格写法（`from './demo-dataset'`），Node 的 ESM 解析器
 * 要求写全扩展名，因此在校验脚本里注册本钩子补齐。只影响校验进程，不影响
 * Vite 构建——构建时走的是 Vite 自己的解析器。
 */
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

export async function resolve(specifier, context, nextResolve) {
  try {
    return await nextResolve(specifier, context)
  } catch (error) {
    if (specifier.startsWith('.') && !/\.[cm]?js$/.test(specifier) && context.parentURL) {
      const candidate = new URL(`${specifier}.js`, context.parentURL)
      if (existsSync(fileURLToPath(candidate))) {
        return { url: candidate.href, shortCircuit: true }
      }
    }
    throw error
  }
}
