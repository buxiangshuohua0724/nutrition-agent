/**
 * 开发期演示数据兜底（仅在 `import.meta.env.DEV` 且真实接口请求失败时使用）
 *
 * 重要原则：
 *   1. 默认永远走真实接口，本文件不参与正常请求路径。
 *   2. 只有接口抛错（后端未启动 / 网络不通）时才回退到这里的占位数据，
 *      页面必须显著标示「演示数据」。
 *   3. 生产构建（DEV=false）下本兜底完全不生效，接口失败即失败。
 *
 * 数据形态严格对照 `docs/api-contract.md` 第三章的核心数据结构，
 * 并且**刻意保留 MISSING 三态**，让前端在演示数据下也能验证
 * "缺失不当作零"的渲染逻辑。
 */

import { withDemoFallback, withEmptyPageFallback } from '../utils/dev'
import { formatNutrition } from '../utils/format'

// 供校验脚本（frontend/scripts/verify-answer.mjs）复用同一个渲染口径：
// 它要断言「回答正文里的数值与营养表一致」，如果自己另写一份拼接规则，
// 两份规则迟早会分叉，脚本就会开始对着错的东西点头。
export { formatNutrition }
import {
  DEMO_CATEGORY_LIST,
  DEMO_DEFAULT_VARIANT,
  DEMO_FOOD_DETAILS,
  DEMO_FOOD_LIST,
  DEMO_NUTRIENT_BY_CODE,
  DEMO_NUTRIENT_BY_ID,
  DEMO_NUTRITION_TABLES,
} from './demo-dataset'

// 供 api 层统一从本模块引入分页兜底，避免各处直接依赖 utils/dev
export { withEmptyPageFallback }

/* =============================================================================
 * 一、事实数据 —— 全部来自 `demo-dataset.js`（由 scripts/gen_demo_dataset.py 从
 * 后端种子数据 `backend/src/main/resources/db/h2/data.sql` 生成）。
 *
 * 这一层**不允许手写**：手写就会和数据库各自漂移。曾经出现过「分类页写“蔬菜 12
 * 种”、点进去却是空列表」以及「演示只有 10 条食品、好几个分类永远是空的」。
 * 需要改演示数据时，改种子 SQL 再重新运行生成器。
 * ========================================================================== */

/** 分类列表（FoodCategory[]），foodCount 由食品清单统计得出 */
export const DEMO_CATEGORIES = DEMO_CATEGORY_LIST

/** 食品摘要列表（FoodSummary[]），与种子库 23 条食品一一对应 */
export const DEMO_FOODS = DEMO_FOOD_LIST

/** 详情表：foodId -> FoodDetail */
export const DEMO_FOOD_DETAILS_BY_ID = DEMO_FOOD_DETAILS

/** 营养表：variantId -> NutritionTable */
export const DEMO_NUTRITION_TABLES_BY_VARIANT = DEMO_NUTRITION_TABLES

/** foodId -> 默认形态 variantId */
export { DEMO_DEFAULT_VARIANT }

/** 营养素编码 -> 元数据（中文名 / 单位），界面显示编码字段时用它翻译 */
export { DEMO_NUTRIENT_BY_CODE }

/** 营养素 id -> 元数据（营养行里只带 id，展示前要翻成中文名） */
export { DEMO_NUTRIENT_BY_ID }

/**
 * 兜底用的演示对象：种子库里 foodId=12 的**苹果**。
 *
 * 只在「请求的 id 在演示数据集里找不到」时使用（见 `frontend/src/api/foods.js`
 * 的 `getFoodDetail`）。这里**不能**写成"默认演示对象是西红柿"——foodId 与食品名
 * 的对应关系一律以种子数据为准，写错就会让详情页显示错食品的文案。
 */
export const DEMO_FOOD_DETAIL = DEMO_FOOD_DETAILS[12]

/* =============================================================================
 * 二、演示文案 —— 由人撰写，与种子数据无关。
 * 只允许放"给人看的示例文本"（AI 回答、检索片段、用户资料…），
 * 绝不允许放营养数值：所有数字必须来自上面的事实数据层。
 * ========================================================================== */

/**
 * 首页「热门食品」的推荐顺序（按食品 code，见原型图板块 1）。
 *
 * 这是一份**展示编排**，不是数据：它只决定"先展示哪几个"，不改变任何字段。
 * 种子库里的食品按 id 排序，前五个是蔬菜类，首页会显得单调；
 * 原型图的热门食品横跨蔬菜/蛋类/水产/蔬菜/水果，所以这里显式给出顺序。
 * 不在列表中的食品按原 order 排在其后，因此新增食品不会导致首页崩溃。
 */
const FEATURED_CODE_ORDER = [
  'veg-tomato',
  'egg-chicken-egg',
  'aquatic-salmon',
  'veg-broccoli',
  'fruit-blueberry',
]

/** 热门食品（HomeView 用）：按上面的编排顺序取前若干个，数量由调用方限制。 */
export const DEMO_FEATURED = DEMO_FOODS.slice().sort((a, b) => {
  const left = FEATURED_CODE_ORDER.indexOf(a.code)
  const right = FEATURED_CODE_ORDER.indexOf(b.code)
  if (left === -1 && right === -1) return 0
  if (left === -1) return 1
  if (right === -1) return -1
  return left - right
})

/**
 * 生成 AI 回答（AgentAnswer 结构）。
 *
 * **关键约束**：回答正文里出现的每一个营养数值都必须来自种子数据。
 * 早先这里写死了「脂肪与膳食纤维在演示数据集中标记为已知零值」「维生素 A、D、铁尚未录入」，
 * 而种子数据里西红柿的脂肪是 0.2 g、膳食纤维 1.2 g、维生素 A 是 83 ug，
 * 于是回答与同一页面下方的营养表自相矛盾。现在改为**逐项从营养表推导**：
 * 已录入的只列举宏量营养素，缺失的按真实缺失清单说明，零值按真实零值清单说明。
 *
 * @param {number} foodId 食品 id
 * @returns {object|null} AgentAnswer；食品或营养表不存在时返回 null
 *
 * 导出是为了让校验脚本能对**任意食品**复算一遍（`DEMO_AGENT_ANSWER` 只是 foodId=1 的实例）。
 */
export function buildDemoAnswer(foodId) {
  const food = DEMO_FOOD_DETAILS[foodId]
  const table = DEMO_NUTRITION_TABLES[DEMO_DEFAULT_VARIANT[foodId]]
  if (!food || !table) return null
  const rows = Array.isArray(table.rows) ? table.rows : []
  const macroIds = [1, 2, 3, 4, 5] // 热量 / 蛋白质 / 脂肪 / 碳水化合物 / 膳食纤维
  const macroText = rows
    .filter((row) => macroIds.includes(row.nutrientId) && row.valueStatus !== 'MISSING')
    // 走 formatNutrition 而不是直接拼 displayText：displayText 只有数值，
    // 单位在 unit 字段里。不拼单位，回答正文就会写成「热量 15、蛋白质 0.9」这种
    // 少了单位的句子，而同一页面的营养表里是有单位的，两处对不上。
    .map((row) => `${row.nutrientName} ${formatNutrition(row)}`)
    .join('、')
  const missingNames = rows
    .filter((row) => row.valueStatus === 'MISSING')
    .map((row) => DEMO_NUTRIENT_BY_ID[row.nutrientId] && DEMO_NUTRIENT_BY_ID[row.nutrientId].name)
    .filter(Boolean)
  const zeroNames = rows
    .filter((row) => row.valueStatus === 'ZERO')
    .map((row) => DEMO_NUTRIENT_BY_ID[row.nutrientId] && DEMO_NUTRIENT_BY_ID[row.nutrientId].name)
    .filter(Boolean)
  const energy = rows.find((row) => row.nutrientId === 1)
  const sections = [
    `🍚 主要营养特点\n${food.name}（${table.variantLabel}，${table.referenceNote}）：${macroText}。`,
  ]
  if (energy) {
    sections.push(
      `📊 数据说明\n以上数值全部取自已录入的每 100g 参考值；页面下方的「营养事实」与这里同源。`,
    )
  }
  if (zeroNames.length > 0) {
    // 项名列表与说明之间用换行隔开：既好读，也让校验脚本能精确切出项名
    // （写成「…Omega-3脂肪酸在数据集中经过确认」会连成一个词，无法逐项比对）
    sections.push(`0️⃣ 已知零值\n${zeroNames.join('、')}\n在数据集中经过确认，确实为 0，与「暂无数据」含义不同。`)
  }
  if (missingNames.length > 0) {
    sections.push(`⚠️ 需要注意\n${missingNames.join('、')}\n在本数据集中尚未录入，本次回答不给出这些项的数值。`)
  } else {
    sections.push('⚠️ 需要注意\n该形态的营养数据在本数据集中是完整的，没有缺项。')
  }
  return {
    answer: sections.join('\n\n'),
    intent: 'NUTRITION_VALUE',
    // 会话 id 由后端在首次提问时创建并回传（`ChatResponse.sessionId`），
    // 演示数据也要带上它，否则演示态下多轮问答每轮都会当成新会话。
    sessionId: 900001,
    foodReferences: [
      {
        foodId,
        foodName: food.name,
        variantId: table.variantId,
        variantLabel: table.variantLabel,
        coverImageUrl: null,
      },
    ],
    // 营养事实只列「已确定」的行，且数值原样搬运，不做任何换算或补齐
    nutritionFacts: rows
      .filter((row) => row.valueStatus !== 'MISSING' && macroIds.includes(row.nutrientId))
      .slice(0, 4)
      .map((row) => ({
        foodName: food.name,
        variantLabel: table.variantLabel,
        nutrientCode: row.nutrientCode,
        nutrientName: row.nutrientName,
        amount: row.amount,
        unit: row.unit,
        valueStatus: row.valueStatus,
        displayText: row.displayText,
        referenceNote: table.referenceNote,
        sourceName: '演示数据集',
      })),
    recommendations: [
      { text: '一般人群可作为日常食物摄入，注意膳食总量平衡', scope: '一般人群', basis: '膳食指南一般性原则' },
    ],
    warnings: [],
    sources: [
      { type: 'STRUCTURED_DATA', title: '演示数据集', refId: 'DEMO_DATASET', url: null },
      {
        // 引用必须指向种子库里真实存在的知识分块（doc=5《营养数据常见问题：
        // 缺失值、零值与参考重量》，chunk_index=1「零值与缺失值的区别」）。
        // 此前这里写的是并不存在的 KDOC_0007，属于「展示了不存在的引用」，已修正。
        //
        // 注意 refId 里的 chunk 号是 knowledge_chunk.id（**全局主键**），不是文档内的
        // chunk_index。这一条 doc_id=5 / chunk_index=1 的分块全局 id 是 14
        // （backend/src/main/resources/db/h2/data.sql:718），所以是 #chunk14。
        // 写成 #chunk1 会解析回 doc_id=1 的分块——引用指向了另一篇文档，
        // 而且这种错误在界面上看起来完全正常（标题是我们自己写的），只有对着
        // 种子数据核对才会发现，因此必须由注释和 check 脚本双重守住。
        type: 'KNOWLEDGE',
        title: '营养数据常见问题：缺失值、零值与参考重量',
        refId: 'KDOC_0005#chunk14',
        url: null,
      },
    ],
    uncertainty:
      missingNames.length > 0
        ? [{ code: 'DATA_MISSING', text: `${missingNames.join('、')}尚未录入，未在回答中给出相关数值` }]
        : [],
    toolCalls: [{ tool: 'searchFood', args: { keyword: food.name }, success: true, durationMs: 12 }],
    model: 'deterministic-stub',
    latencyMs: 480,
    degraded: false,
  }
}

/** AI 回答（演示用；默认对象是种子库里 id=1 的西红柿，与详情页演示对象一致） */
export const DEMO_AGENT_ANSWER = buildDemoAnswer(1)

/** 会话 */
export const DEMO_SESSION = { id: 88, title: '西红柿的营养价值', createdAt: '2026-10-10T12:00:00' }

/** 会话消息 */
export const DEMO_MESSAGES = [
  {
    id: 1,
    sessionId: 88,
    role: 'USER',
    content: '西红柿的营养价值有哪些？',
    createdAt: '2026-10-10T12:00:00',
  },
  {
    id: 2,
    sessionId: 88,
    role: 'ASSISTANT',
    content: DEMO_AGENT_ANSWER.answer,
    structuredAnswer: DEMO_AGENT_ANSWER,
    createdAt: '2026-10-10T12:00:05',
  },
]

/**
 * 按 `code` 在**生成的事实数据集**里定位一个食品，取它真实的 `id` 与 `name`。
 *
 * 为什么必须这样写：演示识别候选曾经是手写的 `{foodId: 12, foodName: '西红柿'}`，
 * 而种子库里 12 其实是苹果 —— 于是识别页显示「西红柿」，点进 `/food/12` 却是苹果；
 * 第三个候选更写了数据集里根本不存在的 `foodId: 41`，确认时必然失败。
 * 手写 id↔名称的组合没有任一门禁能拦住，所以这里改为**只按 code 查表**。
 *
 * 同步关系：候选里的 `foodId` / `foodName` 必须与后端种子数据
 * `backend/src/main/resources/db/h2/data.sql` 一致（本文件的事实层就是由该 SQL 生成的）。
 * 之所以把这条约束写在代码里：id 与名称错位**不会报错、不会白屏**，
 * 页面照常显示一个"看起来能用"的识别结果，只是确认后看到的是另一种食品的营养——
 * 这类错误比直接抛异常难发现得多，所以对应关系必须显式记下来、由 code 反查保证。
 *
 * `code` 不存在时直接抛错：宁可让校验脚本与构建当场失败，
 * 也不要静默退化成 `foodId: undefined` 的候选（那又会伪造一个不存在的食品）。
 *
 * @param {string} code 种子库里的 `food.code`（如 `veg-tomato`）
 * @returns {{foodId: number, foodName: string}} 真实 id 与名称
 * @throws {Error} 数据集里没有该 code 时
 */
function demoFoodRef(code) {
  const food = DEMO_FOOD_LIST.find((item) => item.code === code)
  if (!food) {
    throw new Error(`演示数据集里不存在 food.code = ${code}，无法生成演示识别候选`)
  }
  return { foodId: food.id, foodName: food.name }
}

/** 三个演示识别候选：真实 id / 名称一律由 code 解析，人工只提供置信度与理由文案。 */
export const DEMO_RECOGNITION = {
  recognitionId: 501,
  status: 'SUCCESS',
  imageUrl: null,
  candidates: [
    { ...demoFoodRef('veg-tomato'), confidence: 0.92, reason: '形态与颜色匹配', coverImageUrl: null },
    {
      ...demoFoodRef('veg-cucumber'),
      confidence: 0.61,
      reason: '同为常见红色/绿色茄果类蔬菜',
      coverImageUrl: null,
    },
    { ...demoFoodRef('fruit-apple'), confidence: 0.12, reason: '圆形轮廓相似度较低', coverImageUrl: null },
  ],
  confirmedFoodId: null,
  notice: '识别结果仅为候选，请您确认后再查看营养数据。混合菜品的食材、重量及烹饪用油无法仅凭图片确定。',
  failureReason: null,
  latencyMs: 1240,
}

/** 需要手动搜索的识别结果 */
export const DEMO_RECOGNITION_MANUAL = {
  recognitionId: 502,
  status: 'NEED_MANUAL_SEARCH',
  imageUrl: null,
  candidates: [],
  confirmedFoodId: null,
  notice: '未能从图片中匹配到食品，请手动搜索食品名称。',
  failureReason: '视觉服务未识别出明确的食品候选',
  latencyMs: 860,
}

/** 知识库检索结果 */
export const DEMO_KNOWLEDGE = [
  {
    // 必须指向种子库里真实存在的知识分块：knowledge_doc.id = 5、
    // knowledge_chunk.id = 14（doc=5 的第 1 号分块，chunk_index 从 0 起）。
    // 引用展示的硬约束是「来源必须真实存在」，所以这里的 id 不允许臆造。
    docId: 5,
    chunkId: 14,
    title: '营养数据常见问题：缺失值、零值与参考重量',
    heading: '零值与缺失值的区别',
    snippet:
      '零值表示该项营养成分经过确认确实为零；缺失值表示尚未获得可靠数据。两者在数据库里用不同的状态标记区分，在页面上也应当有不同的呈现方式：零值可以正常参与求和与平均，缺失值则应排除在统计分母之外，并在界面提示数据不完整。',
    category: 'DATA_QUALITY',
    score: 3.2,
    source: {
      name: '《AI 食品营养师演示数据集（非真实来源）》',
      publisher: '项目自制（演示占位）',
      version: 'v0.1',
      url: null,
      applicableScope: '成人一般人群（演示占位）',
    },
  },
]

/** 当前用户 */
export const DEMO_USER = {
  id: 1,
  username: '用户123',
  nickname: '用户123',
  email: 'demo@example.com',
  roles: ['USER'],
  preferences: { goal: 'BALANCED', avoid: [] },
  createdAt: '2026-10-10T12:00:00',
}

/**
 * 后台食品列表行。
 *
 * `variantCount` / `nutrientFilledCount` / `nutrientMissingCount` 一律**从演示数据集统计**，
 * 不允许手写：早先这里给每条食品都写死「已填 9 项、缺 3 项」，与真实种子数据不符。
 * 口径与后端一致：统计默认形态的 14 个营养素里 PRESENT/ZERO 与 MISSING 各多少。
 * `publishStatus` / `reviewStatus` 属演示流程状态（种子库里的值另有含义），故按序构造。
 */
export const DEMO_ADMIN_FOODS = DEMO_FOODS.map((food, index) => {
  const variantIds = Object.keys(DEMO_NUTRITION_TABLES)
    .map(Number)
    .filter((variantId) => DEMO_NUTRITION_TABLES[variantId].foodId === food.id)
  const table = DEMO_NUTRITION_TABLES[DEMO_DEFAULT_VARIANT[food.id]]
  const rows = table && Array.isArray(table.rows) ? table.rows : []
  return {
    id: food.id,
    code: food.code,
    name: food.name,
    categoryId: food.categoryId,
    categoryName: food.categoryName,
    coverImageUrl: food.coverImageUrl,
    publishStatus: index % 4 === 3 ? 'DRAFT' : 'PUBLISHED',
    reviewStatus: index % 4 === 3 ? 'PENDING' : 'APPROVED',
    variantCount: variantIds.length,
    nutrientFilledCount: rows.filter((row) => row.valueStatus !== 'MISSING').length,
    nutrientMissingCount: rows.filter((row) => row.valueStatus === 'MISSING').length,
    updatedAt: '2026-10-10T12:00:00',
  }
})

/**
 * 为请求包一层"演示数据兜底"。
 * @param {() => Promise<unknown>} loader 真实请求
 * @param {() => unknown} demoFactory 演示数据工厂（惰性求值，避免无谓构造）
 * @returns {Promise<{ data: unknown, isDemoData: boolean, error: Error|null }>}
 */
export function withDemo(loader, demoFactory) {
  return withDemoFallback(loader, demoFactory)
}

/**
 * 渲染演示对象前先做一次浅拷贝并覆盖部分字段。
 *
 * 用途：演示数据只有一份（例如西红柿），但用户可能访问 `/food/41`。
 * 直接返回演示数据会让"页面标题"与"演示内容"对不上，因此在演示数据上
 * 覆盖 `id`/`name` 等**纯标识字段**，使演示内容与用户操作的对象一致。
 * 注意：只覆盖标识字段，绝不改动任何营养数值（工程硬约束）。
 *
 * @param {object} base 演示数据原型
 * @param {object} [overrides] 需要覆盖的字段
 * @returns {object} 覆盖后的新对象
 */
export function demoClone(base, overrides = {}) {
  return { ...base, ...overrides }
}
