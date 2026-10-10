/**
 * 营养计算接口 —— 前端**不做任何营养学计算与兜底**
 *
 * 契约依据：`docs/api-contract.md` 4.2
 *   计算规则：某营养素摄入量 = 每 referenceAmount 营养素含量 × 实际食用重量 ÷ referenceAmount
 *   该公式由后端 `NutritionCalculator` 纯函数实现；前端只负责传参、渲染 `displayText`。
 *   `MISSING` 的营养素不会出现在 `items` 中，只列入 `missingNutrients`。
 */

import { get, post } from './client'
import { DEMO_CALCULATION, demoClone, withDemo } from './demo-data'

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
    () =>
      demoClone(DEMO_CALCULATION, {
        foodId: Number(foodId) || DEMO_CALCULATION.foodId,
        inputWeight: Number(weight),
        inputUnit: weightUnit,
      }),
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
    () => [],
  )
}
