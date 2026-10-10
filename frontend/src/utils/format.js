/**
 * 格式化工具 —— 全部基于契约字段，不做任何营养学推断与数值兜底
 *
 * 最高优先级约束（README 工程约束 3、契约 3.3）：
 *   营养数值一律原样使用后端返回的 `displayText`；
 *   `MISSING` 一律显示「暂无数据」；
 *   **任何地方都不允许出现 `value ?? 0` / `|| 0` / `|| '0'` 这类把缺失当零的写法。**
 */

/** 缺失数据的统一文案 */
export const MISSING_TEXT = '暂无数据'

/** 三态常量（与契约 3.3 的 valueStatus 取值一致） */
export const VALUE_STATUS = {
  PRESENT: 'PRESENT',
  ZERO: 'ZERO',
  MISSING: 'MISSING',
}

/**
 * 判断一个营养单元格是否为"未录入"。
 * 只认契约明确给出的状态与文案，绝不通过数值真假来判断（否则会把 0 误判为缺失）。
 * @param {{valueStatus?: string, displayText?: string}|null|undefined} cell 营养单元格（NutritionRow）
 * @returns {boolean}
 */
export function isMissingCell(cell) {
  if (!cell) return true
  if (cell.valueStatus === VALUE_STATUS.MISSING) return true
  // 状态字段缺失时，退化为依据契约文案判断；注意 0 与 '0 g' 都不等于 '暂无数据'
  if (cell.valueStatus === undefined || cell.valueStatus === null) {
    return cell.displayText === undefined || cell.displayText === null || cell.displayText === ''
  }
  return false
}

/**
 * 判断营养单元格是否为"已知为零"。
 * @param {{valueStatus?: string}|null|undefined} cell
 * @returns {boolean}
 */
export function isZeroCell(cell) {
  return Boolean(cell) && cell.valueStatus === VALUE_STATUS.ZERO
}

/**
 * 渲染营养单元格文本。
 *
 * 严格按契约三态处理：
 *   - `PRESENT` → `displayText` + 单位（如 `"15"` + `" kcal"`）
 *   - `ZERO`    → `"0"` + 单位（**已知为零必须显示出来**，不等于「暂无数据」）
 *   - `MISSING` → 「暂无数据」
 *
 * **`displayText` 里只有数值，没有单位。** 后端的 `NutritionMath.displayText()`
 * 只负责把一个数去掉无意义的尾随零（`15.0` → `"15"`、`0` → `"0"`），单位在 `unit`
 * 字段里，由这里拼接。之所以不在后端拼成 `"15 kcal"`：那样前端一旦要换单位、
 * 做同比或对齐小数位，就只能去解析字符串。
 *
 * 这个拼接不是可选的：漏掉单位，界面上就会出现一个孤零零的 `"30"`，
 * 用户无从判断是 30 千卡还是 30 克——而营养表恰恰是靠数字加单位才有意义的。
 *
 * @param {{valueStatus?: string, displayText?: string, unit?: string}|null|undefined} cell
 * @returns {string} 可直接渲染的文本
 */
export function formatNutrition(cell) {
  if (cell === null || cell === undefined) return MISSING_TEXT
  // 先判缺失：MISSING 无论 displayText 为何都显示「暂无数据」
  if (cell.valueStatus === VALUE_STATUS.MISSING) return MISSING_TEXT
  if (typeof cell.displayText === 'string' && cell.displayText.trim() !== '') {
    return withUnit(cell.displayText, cell.unit)
  }
  // 契约的 NutritionCalculation.items[] 使用 computedAmount 表达折算后的数值，
  // 此时按「数值 + 单位」渲染；数值本身仍来自后端，不做任何推断。
  if (cell.valueStatus === VALUE_STATUS.PRESENT && typeof cell.computedAmount === 'number' && Number.isFinite(cell.computedAmount)) {
    return withUnit(String(cell.computedAmount), cell.unit)
  }
  if (cell.valueStatus === VALUE_STATUS.ZERO) {
    return withUnit('0', cell.unit)
  }
  if (cell.valueStatus === VALUE_STATUS.PRESENT) {
    // 契约要求 PRESENT 必须带 displayText 或 computedAmount；都没有时不猜数值
    return MISSING_TEXT
  }
  return MISSING_TEXT
}

/**
 * 把数值文本与单位拼起来。
 *
 * 幂等：调用方若已经带上了单位（例如演示数据集里历史生成的 `"15 kcal"`），
 * 不再重复拼接——否则会显示成 `"15 kcal kcal"`。判据是「文本末尾已经是这个单位」，
 * 而不是「文本里有空格」。
 *
 * @param {string} text  数值文本（可能已含单位）
 * @param {string} [unit] 单位，可能缺失
 * @returns {string}
 */
function withUnit(text, unit) {
  const value = String(text).trim()
  const suffix = typeof unit === 'string' ? unit.trim() : ''
  if (suffix === '') return value
  if (value === suffix || value.endsWith(` ${suffix}`)) return value
  return `${value} ${suffix}`
}

/**
 * 取出营养单元格的数值，**仅供展示性换算使用**（如能量占比）。
 * `MISSING` 返回 null，调用方必须显式处理 null，禁止用 0 兜底。
 * @param {{valueStatus?: string, amount?: number|null, computedAmount?: number|null}|null|undefined} cell
 * @returns {number|null} PRESENT/ZERO 返回数值；MISSING 或字段缺失返回 null
 */
export function nutritionAmount(cell) {
  if (cell === null || cell === undefined) return null
  if (cell.valueStatus === VALUE_STATUS.MISSING) return null
  if (cell.valueStatus === VALUE_STATUS.PRESENT || cell.valueStatus === VALUE_STATUS.ZERO) {
    if (typeof cell.amount === 'number' && Number.isFinite(cell.amount)) return cell.amount
    // 计算接口的 items[] 用 computedAmount 表达折算后的数值
    if (typeof cell.computedAmount === 'number' && Number.isFinite(cell.computedAmount)) return cell.computedAmount
    return null
  }
  return null
}

/**
 * 数字格式化：保留最多 `digits` 位小数，去掉多余的 0。
 * 传 null/undefined 时返回「暂无数据」，**不返回 0**。
 * @param {number|null|undefined} value 数值
 * @param {number} [digits=2] 最多保留的小数位
 * @returns {string}
 */
export function formatNumber(value, digits = 2) {
  if (value === null || value === undefined) return MISSING_TEXT
  const num = Number(value)
  if (!Number.isFinite(num)) return MISSING_TEXT
  if (Number.isInteger(num)) return String(num)
  return String(Number(num.toFixed(digits)))
}

/**
 * 数值 + 单位。数值缺失时返回「暂无数据」。
 * @param {number|null|undefined} value 数值
 * @param {string} [unit] 单位
 * @param {number} [digits=2] 小数位
 * @returns {string}
 */
export function formatAmountWithUnit(value, unit, digits = 2) {
  const text = formatNumber(value, digits)
  if (text === MISSING_TEXT) return MISSING_TEXT
  return unit ? `${text} ${unit}` : text
}

/**
 * 热量文案：`热量 xxx kcal / 100g`。
 * @param {number|null|undefined} energyKcal 热量数值
 * @param {number|null} [referenceAmount=100] 参考量
 * @param {string|null} [referenceUnit='g'] 参考单位
 * @returns {string}
 */
export function formatEnergyReference(energyKcal, referenceAmount = 100, referenceUnit = 'g') {
  if (energyKcal === null || energyKcal === undefined || !Number.isFinite(Number(energyKcal))) {
    return `热量${MISSING_TEXT}`
  }
  const amountText = referenceAmount === null || referenceAmount === undefined ? '' : String(referenceAmount)
  const unitText = referenceUnit || ''
  return `热量 ${formatNumber(energyKcal)} kcal / ${amountText}${unitText}`
}

/**
 * 把接口返回的时间值转成 Date。
 *
 * 兼容两种写法：ISO 8601（`2026-10-10T12:00:00`）与数据库风格
 * （`2026-10-10 12:00:00`，空格分隔）。后者在部分浏览器（尤其是 Safari）
 * 里 `new Date()` 会直接返回 Invalid Date，因此这里先规范化再解析。
 *
 * @param {string|number|Date|null|undefined} value
 * @returns {Date|null} 无法解析时返回 null
 */
function toDate(value) {
  if (value === null || value === undefined || value === '') return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  if (typeof value === 'string') {
    const normalized = value.trim().replace(' ', 'T')
    const parsed = new Date(normalized)
    if (!Number.isNaN(parsed.getTime())) return parsed
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

/**
 * 格式化日期时间（ISO 字符串 → `2026-10-10 12:00`）。
 * @param {string|number|Date|null|undefined} value ISO 时间字符串或时间戳
 * @param {boolean} [withTime=true] 是否带时分
 * @returns {string} 空值返回「暂无数据」
 */
export function formatDateTime(value, withTime = true) {
  const date = toDate(value)
  if (!date) return MISSING_TEXT
  const pad = (n) => String(n).padStart(2, '0')
  const datePart = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  if (!withTime) return datePart
  return `${datePart} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * 格式化日期（仅年月日）。
 * @param {string|number|Date|null|undefined} value
 * @returns {string}
 */
export function formatDate(value) {
  return formatDateTime(value, false)
}

/**
 * 相对时间（用于历史记录、会话列表）。
 * @param {string|number|Date|null|undefined} value
 * @returns {string}
 */
export function formatRelativeTime(value) {
  if (value === null || value === undefined || value === '') return MISSING_TEXT
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return MISSING_TEXT
  const diffMs = Date.now() - date.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diffMs < minute) return '刚刚'
  if (diffMs < hour) return `${Math.floor(diffMs / minute)} 分钟前`
  if (diffMs < day) return `${Math.floor(diffMs / hour)} 小时前`
  if (diffMs < 30 * day) return `${Math.floor(diffMs / day)} 天前`
  return formatDate(date)
}

/**
 * 百分比格式化。
 * 数值为 null（例如某项营养缺失）时返回「暂无数据」，**绝不返回 0%**。
 * @param {number|null|undefined} ratio 0~1 或 0~100 的比例值
 * @param {object} [options]
 * @param {boolean} [options.isPercent=false] 传入值是否已经是百分数
 * @param {number} [options.digits=1] 小数位
 * @returns {string}
 */
export function formatPercent(ratio, options = {}) {
  const { isPercent = false, digits = 1 } = options
  if (ratio === null || ratio === undefined) return MISSING_TEXT
  const num = Number(ratio)
  if (!Number.isFinite(num)) return MISSING_TEXT
  const percent = isPercent ? num : num * 100
  const clamped = Math.max(0, Math.min(100, percent))
  return `${formatNumber(clamped, digits)}%`
}

/**
 * 置信度（0~1）→ 百分比文本。
 * @param {number|null|undefined} confidence
 * @returns {string}
 */
export function formatConfidence(confidence) {
  if (confidence === null || confidence === undefined) return MISSING_TEXT
  const num = Number(confidence)
  if (!Number.isFinite(num)) return MISSING_TEXT
  return `${formatNumber(Math.max(0, Math.min(1, num)) * 100, 0)}%`
}

/**
 * 文件体积格式化。
 * @param {number|null|undefined} bytes
 * @returns {string}
 */
export function formatFileSize(bytes) {
  if (bytes === null || bytes === undefined || !Number.isFinite(Number(bytes))) return MISSING_TEXT
  const size = Number(bytes)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${formatNumber(size / 1024, 1)} KB`
  return `${formatNumber(size / (1024 * 1024), 2)} MB`
}

/**
 * 文本截断（列表摘要）。
 * @param {string|null|undefined} text
 * @param {number} [max=80]
 * @returns {string}
 */
export function truncate(text, max = 80) {
  if (!text) return ''
  const str = String(text)
  return str.length > max ? `${str.slice(0, max)}…` : str
}

/**
 * 空值安全文本：null/undefined/'' → 统一占位文案。
 * 注意：数值 0 与字符串 '0' 都会原样返回。
 * @param {unknown} value
 * @param {string} [placeholder='—']
 * @returns {string}
 */
export function textOrPlaceholder(value, placeholder = '—') {
  if (value === null || value === undefined || value === '') return placeholder
  return String(value)
}
