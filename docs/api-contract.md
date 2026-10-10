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
  "variantId": 21,
  "foodId": 12,
  "foodName": "西红柿",
  "variantLabel": "生（可食部）",
  "referenceAmount": 100,
  "referenceUnit": "g",
  "referenceNote": "每 100g 可食部",
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
      "displayText": "15 kcal",
      "source": {
        "id": 1,
        "name": "演示数据集（不可用于对外发布）",
        "code": "DEMO_DATASET",
        "sourceType": "DEMO",
        "applicableScope": "仅用于流程验证"
      }
    },
    {
      "nutrientId": 9,
      "nutrientCode": "VITAMIN_A",
      "nutrientName": "维生素A",
      "groupCode": "VITAMIN",
      "displayOrder": 20,
      "valueStatus": "MISSING",
      "unit": "ug",
      "amount": null,
      "displayText": "暂无数据",
      "source": null
    }
  ],
  "incomplete": true,
  "missingCount": 1,
  "dataUpdatedAt": "2026-10-10T12:00:00"
}
```

> **`valueStatus` 三态语义（必须严格遵守）**
>
> | valueStatus | amount | displayText | 含义 |
> | --- | --- | --- | --- |
> | `PRESENT` | 数值 | `"15 kcal"` | 已知且非零 |
> | `ZERO` | `0` | `"0 g"` | **已知为零**（如脂肪含量标为 0） |
> | `MISSING` | `null` | `"暂无数据"` | **尚未录入/不可核实**，绝不能参与求和 |
>
> `incomplete` 为 `true` 时，前端必须显示"数据不完整"提示；后端在汇总计算中跳过 `MISSING` 行并回传 `missingCount`。

### 3.4 `NutritionCalculation` — 按重量计算结果

```json
{
  "foodId": 12,
  "foodName": "西红柿",
  "variantId": 21,
  "variantLabel": "生（可食部）",
  "inputWeight": 150,
  "inputUnit": "g",
  "referenceAmount": 100,
  "referenceUnit": "g",
  "items": [
    {
      "nutrientCode": "PROTEIN",
      "nutrientName": "蛋白质",
      "valueStatus": "PRESENT",
      "originalAmount": 0.9,
      "computedAmount": 1.35,
      "unit": "g",
      "formula": "0.9 g/100g × 150 g ÷ 100 = 1.35 g"
    }
  ],
  "estimated": true,
  "estimatedReason": "基于每 100g 参考值的线性折算，未考虑烹饪损失",
  "missingNutrients": ["VITAMIN_A"],
  "sources": [
    { "id": 1, "name": "演示数据集（不可用于对外发布）", "code": "DEMO_DATASET" }
  ]
}
```

> **计算规则（PRD 5.4）**：`某营养素摄入量 = 每 referenceAmount 营养素含量 × 实际食用重量 ÷ referenceAmount`
> - `estimated` 恒为 `true` 并给出 `estimatedReason`：PRD 8.2 要求计算结果标注为估算值/说明口径。
> - `MISSING` 的营养素**不出现在 `items` 中**，只列入 `missingNutrients`。
> - 计算结果必须能追溯回原始数据 → 每个 item 带 `formula`，整体带 `sources`。

### 3.5 `AgentAnswer` — Agent 结构化输出（PRD 6.4）

```json
{
  "answer": "西红柿富含番茄红素、维生素C、钾和膳食纤维…",
  "intent": "NUTRITION_VALUE",
  "foodReferences": [
    { "foodId": 12, "name": "西红柿", "variantId": 21, "variantLabel": "生（可食部）", "coverImageUrl": "…" }
  ],
  "nutritionFacts": [
    {
      "foodName": "西红柿",
      "variantLabel": "生（可食部）",
      "nutrientName": "热量",
      "amount": 15,
      "unit": "kcal",
      "referenceNote": "每 100g 可食部",
      "sourceName": "演示数据集"
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
    { "type": "KNOWLEDGE", "title": "膳食纤维的作用", "refId": "KDOC_0007#chunk2", "url": null }
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

### 3.6 `ImageRecognitionResult` — 识别结果

```json
{
  "recognitionId": 501,
  "status": "SUCCESS",
  "imageUrl": "/api/v1/files/recognition/2026/10/ab12cd.png",
  "candidates": [
    { "foodId": 12, "foodName": "西红柿", "confidence": 0.92, "reason": "形态与颜色匹配", "coverImageUrl": "…" },
    { "foodId": 18, "foodName": "圣女果", "confidence": 0.61, "reason": "同为小型红色浆果", "coverImageUrl": "…" }
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

**响应**：`AgentAnswer`（见 3.5）。

> 降级要求（PRD 6.5 末段）：模型服务不可用时返回 `degraded: true` + `50301/50302`，
> **且不影响** `/foods/**`、`/nutrition/**`、`/categories` 等非 AI 接口。

### 4.4 知识库检索

| 方法 | 路径 | 鉴权 | 说明 | PRD |
| --- | --- | --- | --- | --- |
| GET | `/knowledge/search` | 公开 | 关键词检索已发布知识 | FR-010 |

**响应 `data.list[]`**

```json
{
  "docId": 7, "chunkId": 23, "title": "膳食纤维的作用", "heading": "可溶性纤维",
  "snippet": "…", "category": "NUTRIENT", "score": 3.2,
  "source": { "name": "…", "publisher": "…", "version": "…", "url": null, "applicableScope": "成人一般人群" }
}
```

> 只检索 `publish_status = PUBLISHED`、`review_status = APPROVED` 且未过 `valid_until` 的内容。

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
