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

// 供 api 层统一从本模块引入分页兜底，避免各处直接依赖 utils/dev
export { withEmptyPageFallback }

/** 演示数据集来源（与契约 3.3 的 source 结构一致） */
const DEMO_SOURCE = {
  id: 1,
  name: '演示数据集（不可用于对外发布）',
  code: 'DEMO_DATASET',
  sourceType: 'DEMO',
  applicableScope: '仅用于流程验证',
}

/** 生成营养行 */
function row(order, code, name, group, status, unit, amount, displayText, source = null) {
  return {
    nutrientId: order,
    nutrientCode: code,
    nutrientName: name,
    groupCode: group,
    displayOrder: order,
    valueStatus: status,
    unit,
    amount,
    displayText,
    source,
  }
}

/** 分类列表（slug 与 assets/categories/ 文件名规则对齐，见 utils/assets.js） */
export const DEMO_CATEGORIES = [
  { id: 1, name: '蔬菜', slug: 'vegetables', foodCount: 12 },
  { id: 2, name: '水果', slug: 'fruits', foodCount: 8 },
  { id: 3, name: '肉类', slug: 'meat', foodCount: 6 },
  { id: 4, name: '水产', slug: 'aquatic', foodCount: 5 },
  { id: 5, name: '蛋类', slug: 'eggs', foodCount: 3 },
  { id: 6, name: '谷物', slug: 'grains', foodCount: 7 },
  { id: 7, name: '乳制品', slug: 'dairy', foodCount: 4 },
  { id: 8, name: '坚果', slug: 'nuts', foodCount: 5 },
  { id: 9, name: '豆类', slug: 'legumes', foodCount: 6 },
  { id: 10, name: '其他', slug: 'others', foodCount: 2 },
]

/** 食品摘要列表（FoodSummary 结构） */
export const DEMO_FOODS = [
  {
    id: 12,
    code: 'VEG_TOMATO',
    name: '西红柿',
    enName: 'Tomato',
    categoryId: 1,
    categoryName: '蔬菜',
    categorySlug: 'vegetables',
    coverImageUrl: null,
    intro: '又称番茄，富含维生素 C、番茄红素与钾，可生食也可熟食。',
    defaultVariantId: 21,
    energyKcal: 15,
    energyAvailable: true,
    tags: ['蔬菜'],
  },
  {
    id: 13,
    code: 'VEG_CUCUMBER',
    name: '黄瓜',
    enName: 'Cucumber',
    categoryId: 1,
    categoryName: '蔬菜',
    categorySlug: 'vegetables',
    coverImageUrl: null,
    intro: '水分含量高，热量低，常作凉拌或生食。',
    defaultVariantId: 22,
    energyKcal: 16,
    energyAvailable: true,
    tags: ['蔬菜'],
  },
  {
    id: 14,
    code: 'VEG_SPINACH',
    name: '菠菜',
    enName: 'Spinach',
    categoryId: 1,
    categoryName: '蔬菜',
    categorySlug: 'vegetables',
    coverImageUrl: null,
    intro: '富含叶酸、维生素 K 与铁，建议焯水后食用。',
    defaultVariantId: 23,
    energyKcal: 24,
    energyAvailable: true,
    tags: ['蔬菜'],
  },
  {
    id: 15,
    code: 'VEG_CARROT',
    name: '胡萝卜',
    enName: 'Carrot',
    categoryId: 1,
    categoryName: '蔬菜',
    categorySlug: 'vegetables',
    coverImageUrl: null,
    intro: '富含 β-胡萝卜素，油脂同烹有利于吸收。',
    defaultVariantId: 24,
    energyKcal: null,
    energyAvailable: false,
    tags: ['蔬菜'],
  },
  {
    id: 41,
    code: 'FRUIT_APPLE',
    name: '苹果',
    enName: 'Apple',
    categoryId: 2,
    categoryName: '水果',
    categorySlug: 'fruits',
    coverImageUrl: null,
    intro: '含果胶与多种抗氧化物质，是常见日常水果。',
    defaultVariantId: 51,
    energyKcal: 53,
    energyAvailable: true,
    tags: ['水果'],
  },
  {
    id: 42,
    code: 'FRUIT_BANANA',
    name: '香蕉',
    enName: 'Banana',
    categoryId: 2,
    categoryName: '水果',
    categorySlug: 'fruits',
    coverImageUrl: null,
    intro: '钾含量较高，碳水以果糖与葡萄糖为主。',
    defaultVariantId: 52,
    energyKcal: 93,
    energyAvailable: true,
    tags: ['水果'],
  },
  {
    id: 61,
    code: 'MEAT_CHICKEN_BREAST',
    name: '鸡胸肉',
    enName: 'Chicken breast',
    categoryId: 3,
    categoryName: '肉类',
    categorySlug: 'meat',
    coverImageUrl: null,
    intro: '高蛋白低脂肪的白肉，常见健身食材。',
    defaultVariantId: 71,
    energyKcal: 133,
    energyAvailable: true,
    tags: ['肉类'],
  },
  {
    id: 81,
    code: 'SEAFOOD_SALMON',
    name: '三文鱼',
    enName: 'Salmon',
    categoryId: 4,
    categoryName: '水产',
    categorySlug: 'aquatic',
    coverImageUrl: null,
    intro: '富含 EPA / DHA 等 n-3 多不饱和脂肪酸。',
    defaultVariantId: 91,
    energyKcal: 139,
    energyAvailable: true,
    tags: ['水产'],
  },
  {
    id: 101,
    code: 'GRAIN_OAT',
    name: '燕麦',
    enName: 'Oat',
    categoryId: 6,
    categoryName: '谷物',
    categorySlug: 'grains',
    coverImageUrl: null,
    intro: '含 β-葡聚糖等可溶性膳食纤维。',
    defaultVariantId: 111,
    energyKcal: 338,
    energyAvailable: true,
    tags: ['谷物'],
  },
  {
    id: 121,
    code: 'NUT_WALNUT',
    name: '核桃',
    enName: 'Walnut',
    categoryId: 8,
    categoryName: '坚果',
    categorySlug: 'nuts',
    coverImageUrl: null,
    intro: '脂肪以多不饱和脂肪酸为主，热量密度高。',
    defaultVariantId: 131,
    energyKcal: 646,
    energyAvailable: true,
    tags: ['坚果'],
  },
]

/** 热门食品（HomeView 用，最多取 5 个） */
export const DEMO_FEATURED = DEMO_FOODS.slice(0, 5)

/** 食品详情（FoodDetail 结构） */
export const DEMO_FOOD_DETAIL = {
  id: 12,
  code: 'VEG_TOMATO',
  name: '西红柿',
  enName: 'Tomato',
  scientificName: 'Solanum lycopersicum',
  category: { id: 1, name: '蔬菜', slug: 'vegetables' },
  aliases: [
    { alias: '番茄', aliasType: 'COLLOQUIAL' },
    { alias: 'tomato', aliasType: 'ENGLISH' },
  ],
  intro: '西红柿又称番茄，是茄科茄属的一年生草本植物果实，可生食、煮食或加工成酱。',
  commonUsage: '常用于凉拌、快炒、煮汤；加热并配合少量油脂有利于番茄红素吸收。',
  origin: '原产于南美洲西部，现世界各地广泛栽培。',
  storageMethod: '常温阴凉处短期存放；成熟果实可冷藏 3~5 天，避免与叶菜同层挤压。',
  suitableFor: '一般人群均可食用，可作为日常蔬菜摄入。',
  cautions: '胃食管反流或胃酸偏多者空腹大量生食可能不适。',
  images: [
    {
      id: 101,
      url: null,
      thumbUrl: null,
      imageType: 'RAW_MATERIAL',
      caption: '新鲜西红柿',
      sourceNote: '项目自绘示意图',
      licenseNote: 'CC0 / 项目自制',
      isCover: true,
      sortOrder: 0,
    },
    {
      id: 102,
      url: null,
      thumbUrl: null,
      imageType: 'PRODUCT',
      caption: '切面示意',
      sourceNote: '项目自绘示意图',
      licenseNote: 'CC0 / 项目自制',
      isCover: false,
      sortOrder: 1,
    },
  ],
  variants: [
    {
      id: 21,
      state: 'RAW',
      stateLabel: '生',
      cookingMethod: null,
      nameSuffix: '（生，可食部）',
      edibleRatio: 0.94,
      description: '新鲜生食',
      isDefault: true,
    },
    {
      id: 22,
      state: 'COOKED',
      stateLabel: '熟',
      cookingMethod: 'BOILED',
      nameSuffix: '（煮）',
      edibleRatio: 1,
      description: '水煮后',
      isDefault: false,
    },
  ],
  dataUpdatedAt: '2026-10-10T12:00:00',
  publishStatus: 'PUBLISHED',
}

/** 营养表（NutritionTable 结构，含 PRESENT / ZERO / MISSING 三态） */
export const DEMO_NUTRITION_TABLE = {
  variantId: 21,
  foodId: 12,
  foodName: '西红柿',
  variantLabel: '生（可食部）',
  referenceAmount: 100,
  referenceUnit: 'g',
  referenceNote: '每 100g 可食部',
  rows: [
    row(1, 'ENERGY', '热量', 'MACRO', 'PRESENT', 'kcal', 15, '15 kcal', DEMO_SOURCE),
    row(2, 'PROTEIN', '蛋白质', 'MACRO', 'PRESENT', 'g', 0.9, '0.9 g', DEMO_SOURCE),
    row(3, 'FAT', '脂肪', 'MACRO', 'ZERO', 'g', 0, '0 g', DEMO_SOURCE),
    row(4, 'CARBOHYDRATE', '碳水化合物', 'MACRO', 'PRESENT', 'g', 3.3, '3.3 g', DEMO_SOURCE),
    row(5, 'DIETARY_FIBER', '膳食纤维', 'MACRO', 'ZERO', 'g', 0, '0 g', DEMO_SOURCE),
    row(6, 'SODIUM', '钠', 'MINERAL', 'PRESENT', 'mg', 5, '5 mg', DEMO_SOURCE),
    row(7, 'POTASSIUM', '钾', 'MINERAL', 'PRESENT', 'mg', 163, '163 mg', DEMO_SOURCE),
    row(8, 'CALCIUM', '钙', 'MINERAL', 'PRESENT', 'mg', 10, '10 mg', DEMO_SOURCE),
    row(9, 'VITAMIN_A', '维生素 A', 'VITAMIN', 'MISSING', 'ug', null, '暂无数据'),
    row(10, 'VITAMIN_C', '维生素 C', 'VITAMIN', 'PRESENT', 'mg', 19, '19 mg', DEMO_SOURCE),
    row(11, 'VITAMIN_D', '维生素 D', 'VITAMIN', 'MISSING', 'ug', null, '暂无数据'),
    row(12, 'IRON', '铁', 'MINERAL', 'MISSING', 'mg', null, '暂无数据'),
  ],
  incomplete: true,
  missingCount: 3,
  dataUpdatedAt: '2026-10-10T12:00:00',
}

/** 另一形态（熟）的营养表，用于验证形态切换 */
export const DEMO_NUTRITION_TABLE_COOKED = {
  ...DEMO_NUTRITION_TABLE,
  variantId: 22,
  variantLabel: '煮（可食部）',
  rows: [
    row(1, 'ENERGY', '热量', 'MACRO', 'PRESENT', 'kcal', 17, '17 kcal', DEMO_SOURCE),
    row(2, 'PROTEIN', '蛋白质', 'MACRO', 'PRESENT', 'g', 0.8, '0.8 g', DEMO_SOURCE),
    row(3, 'FAT', '脂肪', 'MACRO', 'ZERO', 'g', 0, '0 g', DEMO_SOURCE),
    row(4, 'CARBOHYDRATE', '碳水化合物', 'MACRO', 'PRESENT', 'g', 3.6, '3.6 g', DEMO_SOURCE),
    row(5, 'DIETARY_FIBER', '膳食纤维', 'MACRO', 'PRESENT', 'g', 1.1, '1.1 g', DEMO_SOURCE),
    row(6, 'SODIUM', '钠', 'MINERAL', 'PRESENT', 'mg', 6, '6 mg', DEMO_SOURCE),
    row(7, 'POTASSIUM', '钾', 'MINERAL', 'PRESENT', 'mg', 179, '179 mg', DEMO_SOURCE),
    row(8, 'CALCIUM', '钙', 'MINERAL', 'MISSING', 'mg', null, '暂无数据'),
    row(9, 'VITAMIN_A', '维生素 A', 'VITAMIN', 'MISSING', 'ug', null, '暂无数据'),
    row(10, 'VITAMIN_C', '维生素 C', 'VITAMIN', 'PRESENT', 'mg', 13, '13 mg', DEMO_SOURCE),
    row(11, 'VITAMIN_D', '维生素 D', 'VITAMIN', 'MISSING', 'ug', null, '暂无数据'),
    row(12, 'IRON', '铁', 'MINERAL', 'MISSING', 'mg', null, '暂无数据'),
  ],
  missingCount: 4,
}

/** 按重量计算结果（NutritionCalculation 结构） */
export const DEMO_CALCULATION = {
  foodId: 12,
  foodName: '西红柿',
  variantId: 21,
  variantLabel: '生（可食部）',
  inputWeight: 150,
  inputUnit: 'g',
  referenceAmount: 100,
  referenceUnit: 'g',
  items: [
    {
      nutrientCode: 'ENERGY',
      nutrientName: '热量',
      valueStatus: 'PRESENT',
      originalAmount: 15,
      computedAmount: 22.5,
      unit: 'kcal',
      formula: '15 kcal/100g × 150 g ÷ 100 = 22.5 kcal',
    },
    {
      nutrientCode: 'PROTEIN',
      nutrientName: '蛋白质',
      valueStatus: 'PRESENT',
      originalAmount: 0.9,
      computedAmount: 1.35,
      unit: 'g',
      formula: '0.9 g/100g × 150 g ÷ 100 = 1.35 g',
    },
    {
      nutrientCode: 'FAT',
      nutrientName: '脂肪',
      valueStatus: 'ZERO',
      originalAmount: 0,
      computedAmount: 0,
      unit: 'g',
      formula: '0 g/100g × 150 g ÷ 100 = 0 g',
    },
    {
      nutrientCode: 'CARBOHYDRATE',
      nutrientName: '碳水化合物',
      valueStatus: 'PRESENT',
      originalAmount: 3.3,
      computedAmount: 4.95,
      unit: 'g',
      formula: '3.3 g/100g × 150 g ÷ 100 = 4.95 g',
    },
  ],
  estimated: true,
  estimatedReason: '基于每 100g 参考值的线性折算，未考虑烹饪损失',
  missingNutrients: ['VITAMIN_A', 'VITAMIN_D', 'IRON'],
  sources: [{ id: 1, name: '演示数据集（不可用于对外发布）', code: 'DEMO_DATASET' }],
}

/** AI 回答（AgentAnswer 结构） */
export const DEMO_AGENT_ANSWER = {
  answer:
    '🍅 主要营养特点\n西红柿的水分含量高，热量较低，含有维生素 C、钾与一定量的膳食纤维。\n\n📊 数据说明\n每 100g 可食部的热量、蛋白质、碳水化合物的具体数值见下方「营养事实」。脂肪与膳食纤维在演示数据集中标记为已知零值。\n\n⚠️ 需要注意\n维生素 A、维生素 D、铁在本数据集中尚未录入，因此本次回答不给出这三项的数值。',
  intent: 'NUTRITION_VALUE',
  foodReferences: [
    { foodId: 12, name: '西红柿', variantId: 21, variantLabel: '生（可食部）', coverImageUrl: null },
  ],
  nutritionFacts: [
    {
      foodName: '西红柿',
      variantLabel: '生（可食部）',
      nutrientName: '热量',
      amount: 15,
      unit: 'kcal',
      referenceNote: '每 100g 可食部',
      sourceName: '演示数据集',
    },
    {
      foodName: '西红柿',
      variantLabel: '生（可食部）',
      nutrientName: '碳水化合物',
      amount: 3.3,
      unit: 'g',
      referenceNote: '每 100g 可食部',
      sourceName: '演示数据集',
    },
  ],
  recommendations: [
    { text: '一般人群可作为日常蔬菜摄入，注意总量平衡', scope: '一般人群', basis: '膳食指南一般性原则' },
  ],
  warnings: [{ level: 'INFO', text: '肾功能受限人群的钾摄入需遵医嘱' }],
  sources: [
    { type: 'STRUCTURED_DATA', title: '演示数据集', refId: 'DEMO_DATASET', url: null },
    { type: 'KNOWLEDGE', title: '膳食纤维的作用', refId: 'KDOC_0007#chunk2', url: null },
  ],
  uncertainty: [
    { code: 'DATA_MISSING', text: '该食品的部分维生素与矿物质数据尚未录入，未在回答中给出相关数值' },
  ],
  toolCalls: [{ tool: 'searchFood', args: { keyword: '西红柿' }, success: true, durationMs: 12 }],
  model: 'deterministic-stub',
  latencyMs: 480,
  degraded: false,
}

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

/** 识别结果（ImageRecognitionResult 结构，候选不含任何营养数值） */
export const DEMO_RECOGNITION = {
  recognitionId: 501,
  status: 'SUCCESS',
  imageUrl: null,
  candidates: [
    { foodId: 12, foodName: '西红柿', confidence: 0.92, reason: '形态与颜色匹配', coverImageUrl: null },
    { foodId: 13, foodName: '黄瓜', confidence: 0.61, reason: '同为常见红色/绿色茄果类蔬菜', coverImageUrl: null },
    { foodId: 41, foodName: '苹果', confidence: 0.12, reason: '圆形轮廓相似度较低', coverImageUrl: null },
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
    docId: 7,
    chunkId: 23,
    title: '膳食纤维的作用',
    heading: '可溶性纤维',
    snippet: '可溶性膳食纤维可延缓胃排空、降低餐后血糖上升速度，并有助于维持肠道菌群。',
    category: 'NUTRIENT',
    score: 3.2,
    source: {
      name: '演示知识条目',
      publisher: '项目自制',
      version: 'v0.1',
      url: null,
      applicableScope: '成人一般人群',
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

/** 后台食品列表行 */
export const DEMO_ADMIN_FOODS = DEMO_FOODS.map((food, index) => ({
  id: food.id,
  code: food.code,
  name: food.name,
  categoryId: food.categoryId,
  categoryName: food.categoryName,
  coverImageUrl: food.coverImageUrl,
  publishStatus: index % 4 === 3 ? 'DRAFT' : 'PUBLISHED',
  reviewStatus: index % 4 === 3 ? 'PENDING' : 'APPROVED',
  variantCount: 1,
  nutrientFilledCount: 9,
  nutrientMissingCount: 3,
  updatedAt: '2026-10-10T12:00:00',
}))

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
