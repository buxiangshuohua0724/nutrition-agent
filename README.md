# AI 食品营养师（nutrition-agent）

> 让每一份食物，都为健康加分

基于**结构化食品数据库 + 确定性营养计算 + 营养知识库（RAG）+ 多模态识别**的 Web 智能食品营养分析平台。

需求来源：[`nutrition_agent/docs/PRD.md`](../nutrition_agent/docs/PRD.md)（V1.0）
界面原型：[`nutrition_agent/docs/AI食品营养师网页端原型图.png`](../nutrition_agent/docs/AI食品营养师网页端原型图.png)

---

## 一、核心设计原则（不可让步）

这套系统最容易失败的地方不是"AI 不够聪明"，而是**AI 把编造的营养数值说得像数据库事实**。因此全项目遵守一条主线：

> **事实数据与 AI 生成内容分离。**

具体落为 6 条工程约束：

| # | 约束 | 落地方式 |
| --- | --- | --- |
| 1 | 营养数值只能来自结构化数据或确定性计算 | `NutritionCalculator` 为纯函数，Agent 无权自算 |
| 2 | 视觉模型只产出候选食品 ID，不产出营养数值 | `recognizeFoodImage` 返回 `candidates[]`，无任何营养字段 |
| 3 | 缺失数据必须显示"暂无数据" | API 用 `null` 表示缺失，**永不**用 `0` 兜底 |
| 4 | 未知值 ≠ 真实零值 | `NutritionValue` 区分 `MISSING` / `ZERO` / `PRESENT` |
| 5 | 引用来源必须真实存在 | `sources[]` 由工具层注入，模型输出中的来源被丢弃 |
| 6 | 计算类问题必须调用确定性逻辑 | `calculateNutrition` 工具，禁止模型估算后当精确结果 |

---

## 二、技术栈

### 后端
- **Java 17+**（本机验证于 JDK 23.0.1）
- **Spring Boot 3.5.8**（模块化单体，非微服务）
- **MyBatis-Plus 3.5.9**（持久层）
- **MySQL 8.0**（生产）/ **H2 2.4.240**（`dev` 内存库，零依赖启动）
- 自研轻量鉴权：**有状态令牌**，令牌哈希落在 `sys_auth_token` 表（`docs/db/schema.sql`），
  登出即删除该行、可真正吊销；不引入轻量级依赖。**接口文档以 `docs/api-contract.md` 为唯一权威来源**
  （离线依赖仓库中不存在与 Spring Boot 3.5.8 兼容的 springdoc 版本，故不引入 OpenAPI 自动生成）

### 前端
- **Vue 3.5**（`<script setup>` + Composition API）
- **Vite 5** + **Vue Router 4** + **Pinia 2**
- **原生 CSS 变量**设计令牌体系（无 UI 框架依赖，设计完全可控）
- 自绘 SVG 食品插画（零版权风险）

### 工程
- Git 分支模型 + 语义化提交 + 里程碑标签（可追踪、可回溯）
- 分层测试：计算逻辑单元测试 + 接口集成测试

---

## 三、目录结构

```
nutrition-agent/
├─ docs/                    设计与契约文档（先文档后代码）
│  ├─ DESIGN.md             系统设计总览：模块划分与分层
│  ├─ use-cases.md          用例图（角色 × 用例）
│  ├─ er-diagram.md         数据库 ER 图与实体关系
│  ├─ api-contract.md       RESTful API 契约
│  ├─ agent-design.md       Agent 工具、工作流与事实校验
│  └─ db/schema.sql         建表 SQL（可执行）
├─ backend/                 Spring Boot 模块化单体
│  └─ src/main/java/com/nutritionagent/
│     ├─ common/            统一响应、异常、工具
│     ├─ config/            配置类
│     ├─ security/          鉴权与权限
│     ├─ food/              食品域
│     ├─ nutrition/         营养域（含确定性计算）
│     ├─ agent/             Agent 协调层（工具 + LLM 适配）
│     ├─ knowledge/         知识库与检索
│     ├─ user/              用户、收藏、对话
│     └─ admin/             后台管理
├─ frontend/                Vue 3 + Vite
│  └─ src/
│     ├─ api/               接口层（mock / http 双实现，可开关）
│     ├─ design/            设计令牌与基础样式
│     ├─ components/        通用组件
│     ├─ views/             页面
│     └─ stores/            Pinia 状态
└─ deploy/                  本地运行与部署说明
```

---

## 四、快速开始

### 后端（dev profile，H2 内存库，无需装数据库）

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# 接口文档 http://localhost:8080/swagger-ui.html
```

### 后端（MySQL）

```bash
mysql -uroot -p -e "CREATE DATABASE nutrition_agent DEFAULT CHARSET utf8mb4;"
mysql -uroot -p nutrition_agent < docs/db/schema.sql
cd backend
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
# http://localhost:5173
```

前端默认走 **mock 数据**（`VITE_USE_MOCK=true`），接口先行、前后端可并行开发。
要连真实后端，把 `frontend/.env.development` 里的 `VITE_USE_MOCK` 改为 `false`。

---

## 五、开发流程与版本管理

本项目用 Git 做**可追踪、可回溯**的开发记录，规则如下：

### 分支模型
| 分支 | 用途 |
| --- | --- |
| `main` | 稳定分支，每个提交点均处于可运行状态 |
| `feat/<scope>` | 单个功能开发 |
| `docs/<scope>` | 仅文档变更 |
| `fix/<scope>` | 缺陷修复 |

### 提交信息规范（Conventional Commits）
```
<type>(<scope>): <subject>

type:  feat | fix | docs | style | refactor | test | chore | perf
scope: backend | frontend | agent | nutrition | food | db | docs | deploy
```

### 标签（里程碑）
| 标签 | 含义 |
| --- | --- |
| `v0.1.0` | 需求与设计基线：文档 + 数据库 schema + API 契约 |
| `v0.2.0` | 后端核心域可用：食品、营养、搜索、计算 |
| `v0.3.0` | Agent 与知识检索可用 |
| `v0.4.0` | 前端六大界面可用 |
| `v1.0.0` | MVP 闭环打通 |

### 回溯方式
```bash
git log --oneline --graph --decorate   # 查看开发脉络
git tag -l                             # 查看里程碑
git show v0.2.0                        # 查看某里程碑内容
git diff v0.2.0..v0.3.0                # 对比两个里程碑
```

---

## 六、数据来源规范

**所有已发布营养数据的来源可追溯率必须为 100%**（PRD 2.2）。

每条营养数据强制携带：
- `unit`：计量单位（如 `g`、`mg`、`kcal`）—— 落在 `nutrient.unit`（指标字典），**不在** `nutrition_value` 上
- `reference_weight`：参考重量与基准（如 `100g 可食部`）→ `nutrition_value.reference_amount` / `reference_unit` / `reference_note`
- `food_state`：食品状态（`RAW` 生 / `COOKED` 熟 / `PEELED` 去皮 / `PROCESSED` 加工）→ `food_state_variant.state`
- `source_id`：数据来源标识 → `data_source` 表

> ⚠️ **种子数据声明**：仓库内 `dev`/`demo` 种子数据仅用于打通流程与自动化测试，
> 数值标注为 `(演示数据)`（`data_source.code = 'DEMO-SEED'`、`source_type = 'DEMO'`），
> **不得**直接作为对外发布的权威营养数据。
> 正式数据必须由内容管理员依据权威来源（如《中国食物成分表》）录入并审核发布。
>
> ⚠️ **种子数据为什么是"已发布"**：`h2/data.sql` 里 23 个食品全部
> `publish_status='PUBLISHED'`，是为了让后端公开接口在开发期有可见数据
> （`PUBLISHED + APPROVED + deleted_at IS NULL` 是公开查询的第一道过滤，
> 全部下架会让接口无对象可返）。这**不构成产品级发布承诺**，
> 也不意味着它们可以对外发布；该豁免的范围与边界见
> [`docs/data-source-policy.md`](docs/data-source-policy.md) 7.1 的 D9/D10。
> 因此**开发期查询 C-01 必然非 0 行**，属预期状态；上线前必须归零（D8）。

---

## 七、当前进度

见 [`docs/ROADMAP.md`](docs/ROADMAP.md)。
