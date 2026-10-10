/**
 * 素材解析工具 —— 用 `import.meta.glob` 动态解析三类 SVG 素材
 *
 * 素材由另一位同事维护，可能尚未全部生成，因此：
 *   - 用 glob 建立"文件名 → URL"映射；文件不存在时键不存在，**不会**导致构建失败；
 *   - 解析不到时返回 `null`，调用方用内联占位符兜底；
 *   - 兼容"契约 slug"与"素材实际文件名"的历史差异（见 CATEGORY_SLUG_ALIASES）。
 *
 * 三类素材的命名约定（由 `nutrition-agent/scripts/check_contract.py` 校验）：
 *   1. `assets/icons/*.svg`       — 24×24 currentColor 描边图标，名称为图标语义名
 *   2. `assets/categories/*.svg`  — 64×64 圆形分类徽章，文件名 = 分类 slug（复数）
 *   3. `assets/foods/*.svg`       — 160×120 食品插画，文件名 = 后端 `food.code` 的 kebab-case
 */

/** 图标：24×24 描边图标 */
const iconModules = import.meta.glob('../assets/icons/*.svg', {
  eager: true,
  query: '?url',
  import: 'default',
})

/** 分类徽章：64×64 圆形 */
const categoryModules = import.meta.glob('../assets/categories/*.svg', {
  eager: true,
  query: '?url',
  import: 'default',
})

/** 食品插画：160×120 */
const foodModules = import.meta.glob('../assets/foods/*.svg', {
  eager: true,
  query: '?url',
  import: 'default',
})

/**
 * 把 glob 的模块映射整理成 `文件名（不含扩展名）→ URL` 的普通对象。
 * @param {Record<string, unknown>} modules glob 结果
 * @returns {Record<string, string>}
 */
function toNameUrlMap(modules) {
  const map = {}
  Object.entries(modules).forEach(([path, url]) => {
    const fileName = path.split('/').pop() || ''
    const baseName = fileName.replace(/\.svg$/i, '')
    if (baseName && typeof url === 'string') {
      map[baseName] = url
    }
  })
  return map
}

/** 图标名 → URL */
const iconUrlMap = toNameUrlMap(iconModules)
/** 分类 slug → URL */
const categoryUrlMap = toNameUrlMap(categoryModules)
/** 食品 code → URL */
const foodUrlMap = toNameUrlMap(foodModules)

/**
 * 分类 slug 别名表。
 *
 * **权威取值**为 `backend/src/main/resources/db/h2/data.sql` 的 `food_category` 种子数据，
 * 与 `frontend/src/assets/categories/<slug>.svg` 文件名、
 * `frontend/src/design/tokens.css` 的 `--na-color-cat-<slug>-bg` 变量名三者完全一致：
 *   `vegetables` / `fruits` / `meat` / `aquatic` / `eggs` / `grains` /
 *   `dairy` / `nuts` / `legumes` / `others`
 *
 * 注意「水产」的权威 slug 是 **`aquatic`**（不是 `seafood`）。
 * 本表只为**历史写法与外部导入数据**兜底（早期文档/契约示例里写过单数与 `seafood`），
 * 后端自己的种子数据不需要依赖它：
 *   `nutrition-agent/scripts/check_contract.py` 会强制三者取值一致。
 * @type {Record<string, string[]>}
 */
export const CATEGORY_SLUG_ALIASES = {
  vegetables: ['vegetable'],
  vegetable: ['vegetables'],
  fruits: ['fruit'],
  fruit: ['fruits'],
  aquatic: ['seafood'],
  seafood: ['aquatic'],
  eggs: ['egg'],
  egg: ['eggs'],
  grains: ['grain'],
  grain: ['grains'],
  nuts: ['nut'],
  nut: ['nuts'],
  legumes: ['legume'],
  legume: ['legumes'],
  others: ['other'],
  other: ['others'],
}

/**
 * 把 slug 归一化：转小写、空格与下划线转连字符。
 * @param {string|null|undefined} slug
 * @returns {string}
 */
export function normalizeSlug(slug) {
  if (!slug) return ''
  return String(slug).trim().toLowerCase().replace(/[\s_]+/g, '-')
}

/**
 * 取分类徽章图片地址。
 * @param {string|null|undefined} slug 分类 slug（单复数均可）
 * @returns {string|null} 图片 URL；素材缺失时返回 null
 */
export function categoryImage(slug) {
  const key = normalizeSlug(slug)
  if (!key) return null
  if (categoryUrlMap[key]) return categoryUrlMap[key]
  const aliases = CATEGORY_SLUG_ALIASES[key] || []
  for (const alias of aliases) {
    if (categoryUrlMap[alias]) return categoryUrlMap[alias]
  }
  return null
}

/**
 * 分类徽章底色所对应的 CSS 变量名（与 assets/categories 插画底色一致）。
 * @param {string|null|undefined} slug
 * @returns {string} 形如 `var(--na-color-cat-vegetables-bg)`，未知分类回落到 others
 */
export function categoryBgVar(slug) {
  const key = normalizeSlug(slug)
  const resolved = categoryUrlMap[key] ? key : (CATEGORY_SLUG_ALIASES[key] || [])[0] || key
  const known = Object.keys(categoryUrlMap)
  const finalSlug = known.includes(resolved) ? resolved : 'others'
  return `var(--na-color-cat-${finalSlug}-bg)`
}

/**
 * 把食品 `code` 转成素材文件名（kebab-case）。
 *
 * 素材文件名规则是 `分类前缀-名称` 的 kebab-case，例如：
 *   `VEG_TOMATO` → `veg-tomato`
 *   `AQUATIC_SALMON` → `aquatic-salmon`
 *   `MEAT_CHICKEN_BREAST` → `meat-chicken-breast`
 * @param {string|null|undefined} code 食品 code
 * @returns {string}
 */
export function foodCodeToSlug(code) {
  if (!code) return ''
  return String(code).trim().toLowerCase().replace(/[\s_]+/g, '-')
}

/**
 * 取食品插画地址。
 *
 * `food.code` 与素材文件名当前是 **一一对应** 的（23 个 code ↔ 23 个
 * `assets/foods/<code>.svg`），由 `scripts/check_contract.py` 强制校验。
 * 因此正常情况下第一跳就命中；后面的兜底只是为了让素材尚未补齐时
 * 仍能退到"省略分类前缀"的写法，不至于整块空白。
 * @param {string|null|undefined} code 食品 code
 * @returns {string|null} 图片 URL；素材缺失时返回 null
 */
export function foodImage(code) {
  const slug = foodCodeToSlug(code)
  if (!slug) return null
  if (foodUrlMap[slug]) return foodUrlMap[slug]
  // 兜底：兼容"文件名省略分类前缀"的写法，例如 `tomato.svg`
  const parts = slug.split('-')
  if (parts.length > 1 && foodUrlMap[parts.slice(1).join('-')]) {
    return foodUrlMap[parts.slice(1).join('-')]
  }
  return null
}

/**
 * 取图标地址。
 * @param {string|null|undefined} name 图标名（不含扩展名）
 * @returns {string|null} 图标 URL；图标缺失时返回 null
 */
export function iconUrl(name) {
  const key = normalizeSlug(name)
  if (!key) return null
  return iconUrlMap[key] || null
}

/**
 * 商品/分类图片兜底：优先使用后端 `coverImageUrl`，其次本地素材，最后 null。
 * @param {{coverImageUrl?: string|null, code?: string|null, slug?: string|null}} source
 * @returns {string|null}
 */
export function resolveCoverImage(source) {
  if (!source) return null
  if (source.coverImageUrl) return source.coverImageUrl
  if (source.code) {
    const local = foodImage(source.code)
    if (local) return local
  }
  if (source.slug) {
    const local = categoryImage(source.slug)
    if (local) return local
  }
  return null
}

/** 当前已解析到的三类素材数量（README / 调试用） */
export const assetStats = {
  icons: Object.keys(iconUrlMap).length,
  categories: Object.keys(categoryUrlMap).length,
  foods: Object.keys(foodUrlMap).length,
}

/** 已存在的分类素材 slug 列表（用于渲染分类 chips 时判断是否有图） */
export const availableCategorySlugs = Object.keys(categoryUrlMap)

/** 已存在的食品素材 slug 列表 */
export const availableFoodSlugs = Object.keys(foodUrlMap)

/** 已存在的图标名列表 */
export const availableIconNames = Object.keys(iconUrlMap)
