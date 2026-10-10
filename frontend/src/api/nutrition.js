/**
 * 营养计算接口 —— 前端**不做任何营养学计算与兜底**
 *
 * 契约依据：`docs/api-contract.md` 4.2
 *   计算规则：某营养素摄入量 = 每 referenceAmount 营养素含量 × 实际食用重量 ÷ referenceAmount
 *   该公式由后端 `NutritionCalculator` 纯函数实现；前端只负责传参、渲染 `displayText`。
 *   `MISSING` 的营养素不会出现在 `items` 中，只列入 `missingNutrients`。
 */

import { get, post } from './client'
import {
  DEMO_DEFAULT_VARIANT,
  DEMO_NUTRIENT_BY_CODE,
  DEMO_NUTRITION_TABLES_BY_VARIANT,
  demoClone,
  withDemo,
} from './demo-data'

/** 营养素编码 -> 中文名（中文名一律取自分发的元数据，前端不另行维护一份） */
export function nutrientNameOf(code) {
  const meta = DEMO_NUTRIENT_BY_CODE[String(code)]
  return meta && meta.name ? meta.name : String(code)
}

/** 重量单位换算到克。演示态只支持克/千克；毫升/升按水密度 1:1 处理并在口径中说明。 */
const UNIT_TO_GRAM = { g: 1, kg: 1000, ml: 1, l: 1000 }

/** 最多保留 3 位小数并去掉尾随 0（用于计算结果的展示文本） */
function trimNumber(value) {
  if (value === null || value === undefined) return null
  const num = Number(value)
  if (!Number.isFinite(num)) return null
  return String(Number(num.toFixed(3)))
}

/**
 * 由演示数据集里的营养表推导出「按重量计算结果」。
 *
 * 严格照 `docs/PRD.md` 5.4 的确定性公式逐项折算：
 *   `摄入量 = 每 referenceAmount 的含量 × 实际重量 ÷ referenceAmount`
 * 这是**确定性算术**，不是"估算营养值"；`MISSING` 的营养素一律不进入 `items`，
 * 只列入 `missingNutrients`，绝不用 0 顶替。
 *
 * @param {object} table 演示数据集里的 NutritionTable
 * @param {number} weight 实际重量（已换算为克）
 * @param {string} inputUnit 用户输入的单位
 * @returns {object|null} NutritionCalculation，表不存在时返回 null
 */
function buildDemoCalculation(table, weight, inputUnit) {
  if (!table || !Array.isArray(table.rows)) return null
  const reference = Number(table.referenceAmount) || 100
  const items = []
  const missingNutrients = []
  for (const cell of table.rows) {
    if (cell.valueStatus === 'MISSING') {
      missingNutrients.push(cell.nutrientCode)
      continue
    }
    const original = cell.amount === null || cell.amount === undefined ? null : Number(cell.amount)
    if (original === null || !Number.isFinite(original)) {
      missingNutrients.push(cell.nutrientCode)
      continue
    }
    const computed = (original * weight) / reference
    items.push({
      nutrientCode: cell.nutrientCode,
      nutrientName: cell.nutrientName,
      groupCode: cell.groupCode,
      displayOrder: cell.displayOrder,
      valueStatus: cell.valueStatus,
      originalAmount: original,
      computedAmount: Number(computed.toFixed(3)),
      unit: cell.unit,
      displayText:
        cell.unit === null || cell.unit === undefined
          ? String(trimNumber(computed))
          : `${trimNumber(computed)} ${cell.unit}`,
      formula: `${original} ${cell.unit || ''}/100g × ${trimNumber(weight)} g ÷ ${reference} = ${trimNumber(computed)} ${
        cell.unit || ''
      }`.trim(),
    })
  }
  return {
    foodId: table.foodId,
    foodName: table.foodName,
    variantId: table.variantId,
    variantLabel: table.variantLabel,
    inputWeight: weight,
    inputUnit,
    referenceAmount: reference,
    referenceUnit: table.referenceUnit || 'g',
    items,
    estimated: true,
    estimatedReason: '基于每 100g 参考值的线性折算，未考虑烹饪损失',
    missingNutrients,
    sources: Array.isArray(table.sources) ? table.sources : [],
  }
}

/**
 * 按重量计算营养摄入。
 *
 * 接口：`POST /nutrition/calculate`（公开）
 * 请求体：`{ foodId: number, variantId?: number|null, weight: number, weightUnit: 'g'|'kg'|'ml'|'l' }`
 * 响应：`NutritionCalculation`（契约 3.4）
 *   `{ foodId, foodName, variantId, variantLabel, inputWeight, inputUnit,
 *      referenceAmount, referenceUnit, items[], estimated, estimatedReason,
 *      missingNutrients[], sources[] }`
 *
 * 校验规则（后端负责，前端仅做预校验以便尽早反馈）：
 *   - `foodId` 必填；`variantId` 省略时取默认形态
 *   - `weight` 必须 > 0 且 ≤ 100000，否则 `40002`
 *   - 食品不存在 → `40401`；该形态无营养数据 → `40402`
 *
 * @param {{foodId: number|string, variantId?: number|string|null, weight: number, weightUnit?: string}} payload
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: object|null, isDemoData: boolean, error: Error|null}>}
 */
export function calculateNutrition(payload, options = {}) {
  const { foodId, variantId = null, weight, weightUnit = 'g' } = payload || {}
  const body = { foodId: Number(foodId), variantId: variantId === null ? null : Number(variantId), weight: Number(weight), weightUnit }
  return withDemo(
    () => post('/nutrition/calculate', body, options),
    () => {
      const requestedFood = Number(foodId)
      const requestedVariant =
        variantId === null || variantId === undefined || variantId === '' ? null : Number(variantId)
      const hasVariant = requestedVariant !== null && DEMO_NUTRITION_TABLES_BY_VARIANT[requestedVariant]
      const table = hasVariant
        ? DEMO_NUTRITION_TABLES_BY_VARIANT[requestedVariant]
        : DEMO_NUTRITION_TABLES_BY_VARIANT[DEMO_DEFAULT_VARIANT[requestedFood]] || null
      // 重量必须原样参与折算（此前演示分支忽略入参、公式恒为 150 g，是错的）
      const factor = UNIT_TO_GRAM[String(weightUnit)] || 1
      const weightInGram = Number(weight) * factor
      const calculation = buildDemoCalculation(table, weightInGram, weightUnit)
      if (!calculation) return null
      // 只覆盖纯标识字段，数值一律来自上面按公式的确定性折算
      return demoClone(calculation, { foodId: requestedFood || calculation.foodId })
    },
  )
}

/**
 * 营养素字典。
 *
 * 接口：`GET /nutrition/nutrients`（公开）
 * 响应：`Nutrient[]`
 *   `{ id: number, code: string, name: string, unit: string, groupCode: string, displayOrder: number }`
 *
 * @param {object} [options] 请求选项
 * @returns {Promise<{data: Array<object>|null, isDemoData: boolean, error: Error|null}>}
 */
export function getNutrients(options = {}) {
  return withDemo(
    () => get('/nutrition/nutrients', undefined, options),
    () =>
      Object.keys(DEMO_NUTRIENT_BY_CODE)
        .map((code) => {
          const meta = DEMO_NUTRIENT_BY_CODE[code]
          return {
            id: meta.id,
            code,
            name: meta.name,
            unit: meta.unit,
            groupCode: meta.groupCode,
            displayOrder: meta.displayOrder,
          }
        })
        .sort((a, b) => a.displayOrder - b.displayOrder),
  )
}
