# RESTful API 契约 V1.0

> 本文件是**前后端并行的唯一集成依据**。任何一方擅自变更字段都视为破坏性变更，必须同步修改本文件并升级版本号。

- 基础路径：`/api/v1`
- 数据格式：`application/json; charset=utf-8`
- 鉴权：`Authorization: Bearer <token>`
- 契约依据：`nutrition_agent/docs/PRD.md` 第五、六章

---

## 一、统一响应结构

### 成功

```json
{
  "code": 0,
  "message": "ok",
  "data": { },
  "traceId": "8f3c1e2a4b5d6789"
}
```

### 失败

```json
{
  "code": 40001,
  "message": "食用重量必须大于 0",
  "data": null,
  "traceId": "8f3c1e2a4b5d6789"
}
```

> 约定：`code = 0` 表示成功，**非 0 一律表示失败**。HTTP 状态码同时按语义设置（400/401/403/404/429/500），
> 以便网关与前端拦截器都能判断，但**业务判断请以 `code` 为准**，不要依赖 HTTP 状态。

### 分页结构

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "list": [],
    "page": 1,
    "pageSize": 20,
    "total": 137,
    "totalPages": 7
  }
}
```

---

## 二、错误码表

错误码分段规则：`4xxyy` 客户端问题、`5xxyy` 服务端问题，`yy` 为该段内序号。

| code | HTTP | 含义 | 对应 PRD 6.5 异常 |
| --- | --- | --- | --- |
| 0 | 200 | 成功 | — |
| 40001 | 400 | 参数校验失败 | — |
| 40002 | 400 | 非法重量/份量输入 | FR-004 验收：输入非法重量返回明确错误 |
| 40101 | 401 | 未登录或令牌失效 | — |
| 40301 | 403 | 无权限（含越权访问他人记录） | 12.4 权限验收 |
| 40401 | 404 | 食品不存在 | 异常 1 |
| 40402 | 404 | 营养数据不存在 | 异常 2 |
| 40403 | 404 | 知识库无相关内容 | 异常 3 |
| 40404 | 404 | 会话/消息不存在 | — |
| 40901 | 409 | 资源冲突（重复收藏、重复别名） | — |
| 41301 | 413 | 图片文件过大 | FR-006 验收 |
| 41501 | 415 | 图片格式不支持 | FR-006 验收 |
| 42901 | 429 | 调用频率超限 | 9.2 限流 |
| 50001 | 500 | 服务器内部错误 | — |
| 50301 | 503 | AI 模型服务超时 | 异常 4 |
| 50302 | 503 | AI 返回格式错误 | 异常 5 |
| 50303 | 503 | 工具调用失败 | 异常 6 |
| 50304 | 503 | 视觉识别服务不可用 | 异常 7 |
| 50305 | 503 | 超出系统能力范围的问题 | 异常 8 |
| 50306 | 503 | 高风险健康问题拦截 | 异常 9 |

---

## 三、核心数据结构

### 3.1 `FoodSummary` — 列表项（首页卡片 / 搜索结果）

```json
{
  "id": 12,
  "code": "VEG_TOMATO",
  "name": "西红柿",
  "enName": "Tomato",
  "categoryId": 3,
  "categoryName": "蔬菜",
  "coverImageUrl": "/api/v1/files/food/veg_tomato_cover.svg",
  "intro": "又称番茄，富含维生素C和矿物质…",
  "defaultVariantId": 21,
  "energyKcal": 15,
  "energyAvailable": true,
  "tags": ["蔬菜"]
}
```

> ⚠️ `energyAvailable` 是关键字段。当营养数据缺失时必须为 `false`，**且 `energyKcal` 为 `null`**。
> 前端据此渲染"暂无数据"，**严禁用 0 兜底**（PRD 5.4 / 验收：不会把未知营养成分自动当作零）。

### 3.2 `FoodDetail` — 食品详情

```json
{
  "id": 12,
  "code": "VEG_TOMATO",
  "name": "西红柿",
  "enName": "Tomato",
  "scientificName": "Solanum lycopersicum",
  "category": { "id": 3, "name": "蔬菜", "slug": "vegetables" },
  "aliases": [
    { "alias": "番茄", "aliasType": "COLLOQUIAL" },
    { "alias": "tomato", "aliasType": "ENGLISH" }
  ],
  "intro": "…",
  "commonUsage": "…",
  "origin": "…",
  "storageMethod": "…",
  "suitableFor": "…",
  "cautions": "…",
  "images": [
    {
      "id": 101,
      "url": "/api/v1/files/food/veg_tomato_cover.svg",
      "thumbUrl": null,
      "imageType": "RAW_MATERIAL",
      "caption": "新鲜西红柿",
      "sourceNote": "项目自绘示意图",
      "licenseNote": "CC0 / 项目自制",
      "isCover": true,
      "sortOrder": 0
    }
  ],
  "variants": [
    {
      "id": 21,
      "state": "RAW",
      "stateLabel": "生",
      "cookingMethod": null,
      "nameSuffix": "（生，可食部）",
      "edibleRatio": 0.94,
      "description": "新鲜生食",
      "isDefault": true
    }
  ],
  "dataUpdatedAt": "2026-10-10T12:00:00",
  "publishStatus": "PUBLISHED"
}
```

### 3.3 `NutritionTable` — 营养表格（**三态**，核心结构）

```json
{
  "variantId": 1,
  "foodId": 1,
  "foodName": "西红柿",
  "variantLabel": "生",
  "referenceAmount": 100,
  "referenceUnit": "g",
  "referenceNote": "每100g可食部",
  "rows": [
    {
      "nutrientId": 1,
      "nutrientCode": "ENERGY",
      "nutrientName": "热量",
      "groupCode": "MACRO",
      "displayOrder": 1,
      "valueStatus": "PRESENT",
      "unit": "kcal",
      "amount": 15,
      "displayText": "15",
      "source": {
        "id": 1,
        "name": "《AI 食品营养师演示数据集（非真实来源）》",
        "code": "DEMO-SEED",
        "sourceType": "DEMO",
        "applicableScope": "成人一般人群（演示占位）"
      }
    },
    {
      "nutrientId": 10,
      "nutrientCode": "VITAMIN_D",
      "nutrientName": "维生素D",
      "groupCode": "VITAMIN",
      "displayOrder": 12,
      "valueStatus": "MISSING",
      "unit": "ug",
      "amount": null,
      "displayText": "暂无数据",
      "source": null
    }
  ],
  "dataUpdatedAt": "2025-01-01 00:00:00"
}
```

> **`rows` 一定覆盖全部启用营养素**（种子库为 14 项）。字典里存在、但该形态一条记录都没有的营养素，
> 后端会显式补成 `valueStatus: "MISSING"` 的行（实现见 `NutritionQueryService.fillAbsentNutrients`）。
> 少了整行会让界面看起来像「这个食品没有这项营养素」，而事实是「数据没录」——两者含义完全不同。
>
> **`dataUpdatedAt` 是营养数值的更新时间**，取自该形态下 `nutrition_value.updated_at` 的最大值，
> **不是** `FoodDetail.dataUpdatedAt`（那是食品档案的更新时间，与营养数值是两条独立的编辑链路：
> 管理员今天改了热量，食品档案的 `updated_at` 不会动）。格式统一为 `yyyy-MM-dd HH:mm:ss`。
>
> 顶层**没有** `incomplete` / `missingCount` 两个字段：契约 3.5 才有 `missingCount`。营养表这边
> 由前端按 `rows` 里 `MISSING` 的条数自行统计（`frontend/src/components/NutritionTable.vue`），
> 接口不重复给一份可能与 `rows` 不一致的数字——两份真相迟早会打架。

> **`valueStatus` 三态语义（必须严格遵守）**
>
> | valueStatus | amount | displayText | 含义 |
> | --- | --- | --- | --- |
> | `PRESENT` | 数值 | `"15"` | 已知且非零 |
> | `ZERO` | `0` | `"0"` | **已知为零**（如脂肪含量标为 0） |
> | `MISSING` | `null` | `"暂无数据"` | **尚未录入/不可核实**，绝不能参与求和 |
>
> `displayText` 只带数值本身，**单位在 `unit` 字段里**，由前端拼接（`"15" + " " + "kcal"`）。
> 接口不返回 `"15 kcal"` 这种拼好的字符串：数值与单位分开，前端换算或换单位时才不会去解析字符串。
>
> 汇总计算（`POST /nutrition/calculate`）跳过 `MISSING` 行，并把它们的**中文名**放进
> `missingNutrients`（契约 3.4）——前端拿到就能直接显示，不必再翻一次字典。

### 3.4 `NutritionCalculation` — 按重量计算结果

```json
{
  "foodId": 1,
  "foodName": "西红柿",
  "variantId": 1,
  "variantLabel": "生",
  "inputWeight": 200,
  "inputUnit": "g",
  "referenceAmount": 100,
  "referenceUnit": "g",
  "items": [
    {
      "nutrientId": 2,
      "nutrientCode": "PROTEIN",
      "nutrientName": "蛋白质",
      "unit": "g",
      "originalAmount": 0.9,
      "computedAmount": 1.8,
      "displayText": "1.8",
      "formula": "0.9 g/100g × 200 g ÷ 100g = 1.8 g"
    }
  ],
  "estimated": true,
  "estimatedReason": "基于每 100g 参考值的线性折算，未考虑烹饪损失",
  "missingNutrients": ["维生素D"],
  "sources": [
    {
      "id": 1,
      "name": "《AI 食品营养师演示数据集（非真实来源）》",
      "code": "DEMO-SEED",
      "sourceType": "DEMO",
      "applicableScope": "成人一般人群（演示占位）"
    }
  ]
}
```

> **计算规则（PRD 5.4）**：`某营养素摄入量 = 每 referenceAmount 营养素含量 × 实际食用重量 ÷ referenceAmount`
> - `estimated` 恒为 `true` 并给出 `estimatedReason`：PRD 8.2 要求计算结果标注为估算值/说明口径。
> - `MISSING` 的营养素**不出现在 `items` 中**，只列入 `missingNutrients`。
> - 计算结果必须能追溯回原始数据 → 每个 item 带 `formula`，整体带 `sources`。
>
> **三个容易写错的字段**
> - `missingNutrients` 装的是**中文名**（`"维生素D"`），不是内部编码（`"VITAMIN_D"`）——
>   它是给人看的清单，界面拿到就直接渲染。要编码请用 `items[].nutrientCode`。
> - `items[]` **没有** `valueStatus` 字段：能进 `items` 就意味着这一项有确定数值，
>   `MISSING` 的项根本不在里面。用 `missingNutrients` 是否包含它来判断缺失。
> - `inputWeight` / `inputUnit` 是**回显用户输入**（前端表单值），不是计算结果；
>   `originalAmount` 与 `computedAmount` 成对给出，界面才能把算式完整展示给用户复核。
> - 单位不是可选参数：`weightUnit` 缺失会返回 `40001`，不要指望后端默认成 `g`。
>   `weightUnit` 接受 `g` / `kg` / `ml` / `l`（不区分大小写），由 `NutritionMath.toGrams` 归一。

### 3.5 `AgentAnswer` — Agent 结构化输出（PRD 6.4）

```json
{
  "answer": "西红柿富含番茄红素、维生素C、钾和膳食纤维…",
  "intent": "NUTRITION_VALUE",
  "foodReferences": [
    { "foodId": 1, "foodName": "西红柿", "variantId": 1, "variantLabel": "生", "coverImageUrl": null }
  ],
  "nutritionFacts": [
    {
      "foodName": "西红柿",
      "variantLabel": "生",
      "nutrientCode": "ENERGY",
      "nutrientName": "热量",
      "amount": "15",
      "unit": "kcal",
      "valueStatus": "PRESENT",
      "displayText": "15",
      "referenceNote": "每100g可食部",
      "sourceName": "《AI 食品营养师演示数据集（非真实来源）》"
    },
    {
      "foodName": "西红柿",
      "variantLabel": "生",
      "nutrientCode": "VITAMIN_D",
      "nutrientName": "维生素D",
      "amount": null,
      "unit": "ug",
      "valueStatus": "MISSING",
      "displayText": "暂无数据",
      "referenceNote": "每100g可食部",
      "sourceName": "《AI 食品营养师演示数据集（非真实来源）》"
    }
  ],
  "recommendations": [
    { "text": "一般人群可作为日常蔬菜摄入，注意总量平衡", "scope": "一般人群", "basis": "膳食指南一般性原则" }
  ],
  "warnings": [
    { "level": "INFO", "text": "肾功能受限人群的钾摄入需遵医嘱" }
  ],
  "sources": [
    { "type": "STRUCTURED_DATA", "title": "演示数据集", "refId": "DEMO_DATASET", "url": null },
    { "type": "KNOWLEDGE", "title": "营养素基础知识：六大类营养素与能量", "refId": "KDOC_0001#chunk3", "url": null }
  ],
  "uncertainty": [
    { "code": "DATA_MISSING", "text": "该食品的部分维生素数据尚未录入，未在回答中给出相关数值" }
  ],
  "toolCalls": [
    { "tool": "searchFood", "args": { "keyword": "西红柿" }, "success": true, "durationMs": 12 }
  ],
  "model": "deterministic-stub",
  "latencyMs": 480,
  "degraded": false
}
```

> 三条硬规则：
> 1. **字段按需返回**。不适用时可以是 `[]` 或省略，**不要**为了填满字段而编造内容。
> 2. **`sources` 由后端工具层注入**，模型自己输出的来源一律丢弃（PRD FR-011 验收：不展示虚构出处）。
> 3. `uncertainty` 非空时前端必须可见地展示，不得静默丢弃。

> **`sources[].refId` 必须指向种子库中真实存在的记录。** 知识型来源由
> `AgentToolkit.searchNutritionKnowledge` 构造为 `KDOC_<docId 左补零至 4 位>#chunk<chunkId>`，
> 其中 `chunkId` 是 `knowledge_chunk.id`（全局自增主键），**不是**文档内的 `chunk_index`——
> 两者在 docId ≥ 2 时不再相等。种子库现有 6 篇知识文档、18 个分块，因此
> `KDOC_0007#chunk2` 这类示例是**查无此条**的引用。写示例、写测试、写演示数据时都必须先
> 到 `backend/src/main/resources/db/h2/data.sql` 里核对 id：编造的来源会被界面渲染成一条
> 正式的「参考来源」，用户无法分辨，这正是 FR-011 要禁止的。
> 结构化数值来源用 `STRUCTURED_DATA` + `refId = "DEMO_DATASET"`（对应 `data_source.code = 'DEMO-SEED'`）。

### 3.6 `ImageRecognitionResult` — 识别结果

```json
{
  "recognitionId": 501,
  "status": "SUCCESS",
  "imageUrl": "/api/v1/files/recognition/2026/10/ab12cd.png",
  "candidates": [
    { "foodId": 11, "foodName": "蓝莓", "confidence": 0.92, "reason": "形态与颜色匹配", "coverImageUrl": null },
    { "foodId": 21, "foodName": "葡萄", "confidence": 0.61, "reason": "同为深色小型浆果", "coverImageUrl": null }
  ],
  "confirmedFoodId": null,
  "notice": "识别结果仅为候选，请您确认后再查看营养数据。混合菜品的食材、重量及烹饪用油无法仅凭图片确定。",
  "failureReason": null,
  "latencyMs": 1240
}
```

> `candidates` **不含任何营养数值字段**（PRD 5.6：视觉模型只负责识别候选）。
> `status` 可能为 `NEED_MANUAL_SEARCH`，此时前端必须提供手动搜索入口。

---

## 四、接口清单

### 4.1 分类与食品

| 方法 | 路径 | 鉴权 | 说明 | PRD |
| --- | --- | --- | --- | --- |
| GET | `/categories` | 公开 | 分类列表（含食品数量） | FR-001 |
| GET | `/categories/{slug}/foods` | 公开 | 分类下食品分页 | FR-002 |
| GET | `/foods/search` | 公开 | 搜索食品 | FR-002 |
| GET | `/foods/{id}` | 公开 | 食品详情 | FR-003 |
| GET | `/foods/{id}/nutrition` | 公开 | 指定形态营养表 | FR-004 |
| GET | `/foods/featured` | 公开 | 热门/推荐/近期新增食品 | FR-001 |
| GET | `/foods/{id}/related` | 公开 | 相关食品/食谱推荐 | FR-003 |

**`GET /foods/search` 查询参数**

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | — | 支持标准名、英文名、别名模糊匹配 |
| `categoryId` | long | — | 分类筛选 |
| `page` | int | 1 | 页码 |
| `pageSize` | int | 20 | 每页条数，上限 100 |
| `sort` | string | `relevance` | `relevance` / `name` / `energyAsc` / `energyDesc` |

> 业务规则：**只返回 `publish_status = PUBLISHED` 且 `review_status = APPROVED` 且未删除的食品**
> （PRD 5.2：下架食品默认不出现在普通用户的公开搜索结果中）。
> 排序：标准名精确匹配 > 标准名前缀 > 别名精确匹配 > 别名包含 > 关键词包含；同分按 `id` 稳定排序。

### 4.2 营养计算

| 方法 | 路径 | 鉴权 | 说明 | PRD |
| --- | --- | --- | --- | --- |
| POST | `/nutrition/calculate` | 公开 | 按重量计算营养摄入 | FR-004 |
| GET | `/nutrition/nutrients` | 公开 | 营养素字典 | FR-010 |

**`POST /nutrition/calculate` 请求体**

```json
{ "foodId": 12, "variantId": 21, "weight": 150, "weightUnit": "g" }
```

校验规则（PRD FR-004 验收：输入非法重量返回明确错误）：
- `foodId` 必填；`variantId` 省略时取该食品的默认形态
- `weight` 必须 `> 0` 且 `<= 100000`，否则 `40002`
- `weightUnit` ∈ `g` / `kg` / `ml` / `l`，`kg`/`l` 换算为 `×1000`
- 食品不存在 → `40401`；该形态无任何营养数据 → `40402`

### 4.3 AI Agent

| 方法 | 路径 | 鉴权 | 说明 | PRD |
| --- | --- | --- | --- | --- |
| POST | `/agent/chat` | 公开（游客有限次） | 提交问题，返回结构化回答 | FR-005 |
| GET | `/agent/sessions` | 登录 | 会话列表 | FR-008 |
| POST | `/agent/sessions` | 公开（游客可） | 新建会话 | FR-005 |
| GET | `/agent/sessions/{id}/messages` | 登录/游客 | 会话消息 | FR-005 |
| DELETE | `/agent/sessions/{id}` | 登录 | 删除会话 | FR-008 |
| GET | `/agent/capabilities` | 公开 | 当前能力与限制（用于前端提示，以及模型未配置时的降级展示） | 6.5 |

**`POST /agent/chat` 请求体**

```json
{ "sessionId": 88, "question": "西红柿的营养价值有哪些？", "foodId": 12, "variantId": 21, "weight": null }
```

**响应**：`ChatResponse`——**不是**裸的 `AgentAnswer`。

```json
{ "sessionId": 88, "sessionTitle": "西红柿的营养价值有哪些？", "answer": { /* AgentAnswer，见 3.5 */ } }
```

> 为什么要在 `AgentAnswer` 外面包一层：首次提问时后端会按问题自动建会话，前端必须拿到
> `sessionId` 才能继续多轮对话（否则每轮都会开一个新会话）。会话元信息属于「这次请求的结果」，
> 不属于「回答内容」，因此不并进 `AgentAnswer`——那个结构会被原样存进
> `chat_message.structured_output`，塞进易过期的会话字段只会让历史记录失真。
> 前端在 `frontend/src/api/agent.js` 里摊平：视图层拿到的仍是契约 3.5 的形状，外加 `sessionId`。

> 降级要求（PRD 6.5 末段）：模型服务不可用时返回 `degraded: true` + `50301/50302`，
> **且不影响** `/foods/**`、`/nutrition/**`、`/categories` 等非 AI 接口。

### 4.4 知识库检索

| 方法 | 路径 | 鉴权 | 说明 | PRD |
| --- | --- | --- | --- | --- |
| GET | `/knowledge/search` | 公开 | 关键词检索已发布知识 | FR-010 |

**响应 `data.list[]`**

```json
{
  "docId": 1, "chunkId": 3, "title": "营养素基础知识：六大类营养素与能量", "heading": "碳水化合物与膳食纤维",
  "snippet": "…", "category": "NUTRIENT", "score": 3.2,
  "source": { "name": "《中国食物成分表》第6版", "publisher": "北京大学医学出版社", "version": "第6版", "url": null, "applicableScope": "成人一般人群（演示占位）" }
}
```

> 只检索 `publish_status = PUBLISHED`、`review_status = APPROVED` 且未过 `valid_until` 的内容。
> `chunkId` 是 `knowledge_chunk.id`（全局自增主键），**不是**文档内的 `chunk_index`；上例对应种子库
> `backend/src/main/resources/db/h2/data.sql:707`（`knowledge_chunk`：`id=3`、`doc_id=1`、`chunk_index=2`）
> 与 `h2/data.sql:694`（文档「营养素基础知识：六大类营养素与能量」，`source_id=2`）。
> **2026-10 修订**：本示例原先写作 `docId: 7 / chunkId: 23 / title: "膳食纤维的作用"`，三者在种子库中**均不存在**
> （`knowledge_doc` 只有 `id 1~6`，`knowledge_chunk` 只有 `id 1~18`），已按真实数据替换。

### 4.5 图片识别

| 方法 | 路径 | 鉴权 | 说明 | PRD |
| --- | --- | --- | --- | --- |
| POST | `/recognitions` | 公开（游客有限次） | 上传图片识别（`multipart/form-data`，字段名 `file`） | FR-006 |
| POST | `/recognitions/{id}/confirm` | 公开 | 确认候选食品 | FR-006 |
| GET | `/recognitions/{id}` | 公开 | 查询识别记录 | FR-006 |

**约束**：允许 `image/jpeg`、`image/png`、`image/webp`；大小 ≤ 10MB；超限分别返回 `41501` / `41301`。
**不做的校验会直接导致安全验收失败（PRD 12.4）**：必须校验 MIME 与文件头魔数，不能只信扩展名。

### 4.6 用户

| 方法 | 路径 | 鉴权 | 说明 | PRD |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | 公开 | 注册 | FR-007 |
| POST | `/auth/login` | 公开 | 登录 | FR-007 |
| POST | `/auth/logout` | 登录 | 退出 | FR-007 |
| GET | `/users/me` | 登录 | 当前用户 | FR-007 |
| PUT | `/users/me/profile` | 登录 | 更新资料与偏好 | FR-007 |
| GET | `/users/me/favorites` | 登录 | 收藏列表 | FR-008 |
| POST | `/users/me/favorites` | 登录 | 添加收藏 | FR-008 |
| DELETE | `/users/me/favorites/{foodId}` | 登录 | 取消收藏 | FR-008 |
| DELETE | `/users/me/data` | 登录 | 删除个人数据 | FR-007 删除权 |

> **越权防护（PRD 12.4）**：所有 `/users/me/**` 一律以令牌中的 `userId` 为准，
> **禁止**从路径或请求体接收 `userId`；`DELETE /agent/sessions/{id}` 必须校验会话归属。

### 4.7 后台管理（需 `CONTENT_ADMIN` 或 `SYS_ADMIN`）

统一前缀 `/admin`。**普通用户访问必须返回 40301（PRD 12.4）。**

| 方法 | 路径 | 说明 | PRD |
| --- | --- | --- | --- |
| GET | `/admin/foods` | 食品列表（含草稿） | FR-009 |
| POST | `/admin/foods` | 新增食品 | FR-009 |
| PUT | `/admin/foods/{id}` | 编辑食品 | FR-009 |
| DELETE | `/admin/foods/{id}` | 下架食品 | FR-009 |
| POST | `/admin/foods/{id}/review` | 审核（通过/驳回） | FR-009 |
| POST | `/admin/foods/{id}/publish` | 发布 | FR-009 |
| POST | `/admin/foods/{id}/variants` | 新增食品形态 | FR-004 |
| GET | `/admin/variants/{id}/nutrition` | 形态营养数据（含草稿） | FR-009 |
| PUT | `/admin/variants/{id}/nutrition` | 修改营养数据（须带来源） | FR-009 |
| POST | `/admin/foods/{id}/images` | 上传食品图片 | FR-009 |
| DELETE | `/admin/images/{id}` | 删除食品图片 | FR-009 |
| GET | `/admin/knowledge` | 知识列表 | FR-010 |
| POST | `/admin/knowledge` | 新增知识 | FR-010 |
| PUT | `/admin/knowledge/{id}` | 编辑知识 | FR-010 |
| POST | `/admin/knowledge/reindex` | 重建检索索引 | FR-010 |
| GET | `/admin/audit-logs` | 审计日志 | 9.3 |
| GET | `/admin/users` | 用户管理 | 角色D |

> 校验要求：`PUT /admin/variants/{id}/nutrition` 若缺少 `sourceId` 必须 **40001 拒绝**——
> 这是"来源可追溯率 100%"在代码层的强制点，不能只靠约定。

### 4.8 文件访问

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/files/**` | 公开 | 静态文件（食品图片、识别图片） |

---

## 五、限流与超时约定

| 接口 | 限流 | 超时 |
| --- | --- | --- |
| `POST /agent/chat` | 登录 30 次/分钟；游客 5 次/天（PRD 角色 A「有限次数」） | LLM 调用 30s |
| `POST /recognitions` | 登录 20 次/小时；游客 3 次/天 | 视觉调用 30s |
| 其他读接口 | 120 次/分钟 | 5s |

超限返回 `42901`，且响应不暴露内部配额实现细节。

## 六、变更记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| V1.0 | 2026-10-10 | 初版：与 `docs/db/schema.sql` V1.0 对应 |
