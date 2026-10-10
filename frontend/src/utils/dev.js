/**
 * 开发期工具 —— 真实接口失败时的"演示数据"兜底开关
 *
 * 约定（不可让步）：
 *   - 默认永远请求真实接口；本兜底只在 `import.meta.env.DEV === true`
 *     且请求抛错时生效。
 *   - 生产构建（`vite build`）下 DEV 为 false，兜底彻底失效，
 *     接口失败就是失败——绝不能用假数据掩盖线上问题。
 *   - 一旦启用兜底，返回值带 `isDemoData: true`，页面**必须**显著标示「演示数据」。
 */

/** 是否允许使用演示数据兜底（仅开发模式） */
export const isDemoFallbackEnabled = Boolean(import.meta.env.DEV)

/**
 * 执行真实请求；仅在开发模式且请求失败时回退到演示数据。
 * @template T
 * @param {() => Promise<T>} loader 真实请求
 * @param {() => T} demoFactory 演示数据工厂（惰性求值）
 * @returns {Promise<{ data: T|null, isDemoData: boolean, error: Error|null }>}
 *   `error` 仅在既没有真实数据也没有演示兜底时非空。
 */
export async function withDemoFallback(loader, demoFactory) {
  try {
    const data = await loader()
    return { data, isDemoData: false, error: null }
  } catch (error) {
    if (!isDemoFallbackEnabled) {
      return { data: null, isDemoData: false, error }
    }
    // 开发模式下静默降级：错误已经由 client.js 弹过提示，这里只补一条控制台日志
    console.info('[演示数据] 接口调用失败，已回退到内联演示数据：', error && error.message ? error.message : error)
    try {
      return { data: demoFactory(), isDemoData: true, error: null }
    } catch (factoryError) {
      return { data: null, isDemoData: false, error: factoryError }
    }
  }
}

/**
 * 分页兜底：真实请求失败且处于开发模式时，返回一个空结果集（而不是假数据）。
 * 列表页用空态提示"后端未就绪"比伪造一堆条目更诚实。
 * @template T
 * @param {() => Promise<T>} loader 真实请求
 * @param {{page?: number, pageSize?: number}} [params] 分页参数，用于生成空结果集
 * @returns {Promise<{ data: {list: T[], page: number, pageSize: number, total: number, totalPages: number}, isDemoData: boolean, error: Error|null }>}
 */
export async function withEmptyPageFallback(loader, params = {}) {
  try {
    const data = await loader()
    if (data && Array.isArray(data.list)) {
      return { data, isDemoData: false, error: null }
    }
    // 后端返回数组时补成统一分页结构
    const list = Array.isArray(data) ? data : []
    return {
      data: { list, page: 1, pageSize: list.length, total: list.length, totalPages: 1 },
      isDemoData: false,
      error: null,
    }
  } catch (error) {
    if (!isDemoFallbackEnabled) {
      return { data: null, isDemoData: false, error }
    }
    console.info('[演示数据] 列表接口调用失败，已回退为空结果集：', error && error.message ? error.message : error)
    const page = Number(params.page) > 0 ? Number(params.page) : 1
    const pageSize = Number(params.pageSize) > 0 ? Number(params.pageSize) : 20
    return {
      data: { list: [], page, pageSize, total: 0, totalPages: 0 },
      isDemoData: true,
      error: null,
    }
  }
}

/**
 * 从后端分页响应里取出分页元信息，缺失字段回落为默认值。
 *
 * 注意：这里只处理**分页计数**，与营养数值无关；分页总数缺失时回落 0
 * 只是让「没有下一页」成立，不参与任何营养学展示。营养数值一律禁止任何兜底，
 * 因此本函数不得用于营养素字段。
 *
 * @param {object|null|undefined} data 分页响应体（`{list,page,pageSize,total,totalPages}`）
 * @param {{page?: number, pageSize?: number}} [fallback] 缺失时的回落值
 * @returns {{page: number, pageSize: number, total: number, totalPages: number}}
 */
export function pageMetaOf(data, fallback = {}) {
  const page = Number(data && data.page)
  const pageSize = Number(data && data.pageSize)
  const total = Number(data && data.total)
  const totalPages = Number(data && data.totalPages)
  const fallbackPage = Number(fallback.page)
  const fallbackPageSize = Number(fallback.pageSize)
  return {
    page: Number.isFinite(page) && page > 0 ? page : Number.isFinite(fallbackPage) && fallbackPage > 0 ? fallbackPage : 1,
    pageSize:
      Number.isFinite(pageSize) && pageSize > 0
        ? pageSize
        : Number.isFinite(fallbackPageSize) && fallbackPageSize > 0
          ? fallbackPageSize
          : 20,
    total: Number.isFinite(total) && total > 0 ? total : 0,
    totalPages: Number.isFinite(totalPages) && totalPages > 0 ? totalPages : 0,
  }
}

/**
 * 构造统一的"演示数据"返回结构（不分页场景）。
 * @template T
 * @param {T} data 演示数据
 * @returns {{ data: T, isDemoData: true, error: null }}
 */
export function demoResult(data) {
  return { data, isDemoData: true, error: null }
}
