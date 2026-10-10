/**
 * 演示回答与营养表的一致性校验（提交前门禁之一）。
 *
 * 为什么需要它：
 *   `frontend/src/api/demo-data.js` 里的 AI 回答是**由营养表推导**出来的
 *   （`buildDemoAnswer(foodId)`），不能再手写文案里的营养数值。
 *   但推导逻辑本身也可能写错——曾经就出现过回答说「脂肪是已知零值」，
 *   而同页面的营养表写着脂肪 0.2 g 的自相矛盾。这个脚本把那条约束固化下来，
 *   对**种子库里的每一个食品**逐一复算，炸掉任何一处不一致。
 *
 * 检查项（对每个食品的默认形态）：
 *   1. 回答里提到的已确定数值，与营养表的 `amount` 完全一致；
 *   2. `valueStatus === 'ZERO'` 的项必须出现在「已知零值」一行；
 *      反之「已知零值」一行里的项必须真的是 ZERO；
 *   3. `valueStatus === 'MISSING'` 的项必须在回答里被说明为缺失，
 *      且**绝不能**被写进「已知零值」（三态硬约束：缺失 ≠ 零）；
 *   4. `nutritionFacts` 只允许出现确定的数值，不允许 `amount` 为空；
 *   5. 有 MISSING 项就必须给出 `uncertainty`，没有 MISSING 就不该给。
 *
 * 实现方式：Node 直接 import 仓库前端源码。前端用的是 Vite 风格的无扩展名
 * import（`from './demo-dataset'`）与 `import.meta.env`，Node 原生跑不了，
 * 因此配了两个加载钩子（`scripts/hooks/`）在解析期补扩展名、在加载期垫一个
 * 最小的 `import.meta.env`。**不改动、不复制任何源码。**
 *
 * 用法：node scripts/verify-answer.mjs
 * 退出码：0 = 全部通过，1 = 发现不一致
 */
import { register } from 'node:module'
import path from 'node:path'
import process from 'node:process'

// 让前端源码能在裸 Node 里跑
register('./hooks/resolve-ext.mjs', import.meta.url)
register('./hooks/shim-import-meta-env.mjs', import.meta.url)

const entry = new URL('../src/api/demo-data.js', import.meta.url).href
const mod = await import(entry)
const {
  DEMO_FOODS,
  DEMO_DEFAULT_VARIANT,
  DEMO_NUTRITION_TABLES_BY_VARIANT,
  DEMO_NUTRIENT_BY_ID,
  buildDemoAnswer,
} = mod

if (typeof buildDemoAnswer !== 'function') {
  console.error('[verify-answer] demo-data.js 没有导出 buildDemoAnswer，校验脚本已失效，请检查该文件')
  process.exit(1)
}

const problems = []
let checked = 0

for (const food of DEMO_FOODS) {
  const variantId = DEMO_DEFAULT_VARIANT[food.id]
  const table = DEMO_NUTRITION_TABLES_BY_VARIANT[variantId]
  if (!table) {
    problems.push(`foodId=${food.id} ${food.name}：默认形态 ${variantId} 没有对应的营养表`)
    continue
  }
  const built = buildDemoAnswer(food.id)
  if (!built) {
    problems.push(`foodId=${food.id} ${food.name}：buildDemoAnswer 返回空`)
    continue
  }
  checked += 1
  const answer = String(built.answer || '')
  const zeroNames = []
  const missingNames = []

  for (const row of table.rows) {
    const meta = DEMO_NUTRIENT_BY_ID[row.nutrientId]
    const name = meta ? meta.name : String(row.nutrientId)
    if (row.valueStatus === 'ZERO') zeroNames.push(name)
    if (row.valueStatus === 'MISSING') missingNames.push(name)
    // 1. 正文提到该项就必须用表里的数值
    if (row.valueStatus !== 'MISSING' && answer.includes(`${name} `)) {
      if (!answer.includes(`${name} ${row.amount} `)) {
        const line = answer.split('\n').find((item) => item.includes(`${name} `)) || ''
        problems.push(
          `foodId=${food.id} ${food.name}：「${name}」在正文里的数值与营养表不一致（表=${row.amount}）｜${line}`,
        )
      }
    }
  }

  // 「已知零值」段的项名列表 = 标题行之后、说明句之前的那一行。
  // 不能对整段做包含判断：「脂肪」是「Omega-3脂肪酸」的子串，
  // 用 includes 会把 PRESENT 的脂肪误判成零值项（这是本脚本的第一版 bug）。
  const answerLines = answer.split('\n')
  const zeroTitleIndex = answerLines.findIndex((line) => line.includes('已知零值'))
  const zeroListLine = zeroTitleIndex >= 0 ? answerLines[zeroTitleIndex + 1] || '' : ''
  const zeroItems = zeroListLine
    .split(/[、，,]/)
    .map((item) => item.trim())
    .filter((item) => item !== '')
  const isDeclaredZero = (name) => zeroItems.includes(name)

  // 2. ZERO 必须出现在「已知零值」说明里
  for (const name of zeroNames) {
    if (!isDeclaredZero(name)) {
      problems.push(`foodId=${food.id} ${food.name}：ZERO 项「${name}」没有出现在「已知零值」一行`)
    }
  }

  // 3. MISSING 必须在正文被说明为缺失，且不得被当成零值
  for (const name of missingNames) {
    if (!answer.includes(name)) {
      problems.push(`foodId=${food.id} ${food.name}：MISSING 项「${name}」在回答里完全没有被说明`)
    }
    if (isDeclaredZero(name)) {
      problems.push(`foodId=${food.id} ${food.name}：MISSING 项「${name}」被错写成已知零值`)
    }
  }
  // 「已知零值」一行里出现的每一项都必须真的是 ZERO
  if (zeroItems.length > 0) {
    for (const row of table.rows) {
      const meta = DEMO_NUTRIENT_BY_ID[row.nutrientId]
      const name = meta ? meta.name : ''
      if (name && isDeclaredZero(name) && row.valueStatus !== 'ZERO') {
        problems.push(
          `foodId=${food.id} ${food.name}：「${name}」被写进「已知零值」，但它实际是 ${row.valueStatus}`,
        )
      }
    }
  }

  // 4. nutritionFacts 只能是确定数值
  for (const fact of built.nutritionFacts || []) {
    if (fact.amount === null || fact.amount === undefined) {
      problems.push(`foodId=${food.id} ${food.name}：nutritionFacts 出现空数值（${fact.nutrientName}）`)
    }
  }

  // 5. uncertainty 与 MISSING 必须同进同出
  const hasUncertainty = Array.isArray(built.uncertainty) && built.uncertainty.length > 0
  if (missingNames.length > 0 && !hasUncertainty) {
    problems.push(`foodId=${food.id} ${food.name}：有 ${missingNames.length} 个 MISSING 项，却没有 uncertainty`)
  }
  if (missingNames.length === 0 && hasUncertainty) {
    problems.push(`foodId=${food.id} ${food.name}：没有 MISSING 项，却给了 uncertainty`)
  }
}

console.log('='.repeat(72))
console.log('演示回答一致性校验')
console.log('='.repeat(72))
console.log(`已复算 ${checked} 个食品（共 ${DEMO_FOODS.length} 个）`)
if (problems.length === 0) {
  console.log('✓ 全部通过：回答正文、已知零值清单、缺失清单、nutritionFacts、uncertainty 与营养表一致')
  process.exit(0)
}
console.log(`✗ 发现 ${problems.length} 个问题：`)
for (const problem of problems) console.log(`  · ${problem}`)
process.exit(1)
