/**
 * Node 加载钩子：给前端源码垫一个最小的 `import.meta.env`。
 *
 * Vite 在构建时会把 `import.meta.env.DEV` 这类表达式替换成字面量；裸 Node 里
 * `import.meta.env` 是 undefined，一旦某个模块在顶层读它就会抛
 * `Cannot read properties of undefined (reading 'DEV')`。
 *
 * 这里只补校验必需的几个字段（校验跑在"开发模式"语义下），
 * **不改变被加载文件的逻辑**，也不写回磁盘。
 */
export async function load(url, context, nextLoad) {
  const result = await nextLoad(url, context)
  if (result.format === 'module' && result.source && url.includes('/src/')) {
    const code = result.source.toString()
    if (code.includes('import.meta.env')) {
      const shim =
        'const __naImportMetaEnv = { DEV: true, PROD: false, MODE: "development", BASE_URL: "/" }\n'
      return { ...result, source: shim + code.replaceAll('import.meta.env', '__naImportMetaEnv') }
    }
  }
  return result
}
