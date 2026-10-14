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
│  └─ db/
│     ├─ schema.sql         建表 SQL（可执行）
│     ├─ data.sql           MySQL 种子数据（由脚本从 H2 种子生成，非手写）
│     ├─ nutrition-usda.sql 用 USDA 真实数值替换演示营养数值（脚本渲染，非手写）
│     └─ usda-selection.json 上面那份补丁选用的 USDA 条目与数值（可核查、可重放）
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
mvn spring-boot:run '-Dspring-boot.run.profiles=dev'
# 服务地址 http://localhost:8080
```

> dev profile 启动时会用 `backend/src/main/resources/db/h2/schema.sql` + `data.sql`
> 建一个**内存** H2 库并灌入 23 条演示食品。它是内存库：**每次重启都回到种子状态**，
> 因此后台操作产生的数据（审计日志、上传的图片等）不会留到下次启动。
>
> 没有 Swagger/OpenAPI 页面：本项目曾引入 springdoc，但它与当前 Spring Boot 版本
> 不兼容，已移除。接口契约以 [`docs/api-contract.md`](docs/api-contract.md) 为准。

### 演示账号（dev/demo 种子数据）

三个账号的密码**都是 `demo1234`**（由 `BCryptPasswordEncoder().encode("demo1234")` 生成）：

| 用户名 | 密码 | 角色 | 用途 |
| --- | --- | --- | --- |
| `admin` | `demo1234` | `SYS_ADMIN` + `CONTENT_ADMIN` + `USER` | 系统管理（含用户/设置）、内容管理 |
| `content` | `demo1234` | `CONTENT_ADMIN` + `USER` | 内容管理，无系统管理权限 |
| `demo` | `demo1234` | `USER` | 普通用户视角（收藏、历史、问答） |

> 这是**演示用弱口令**，公开接口下可直接登录后台，上线前必须重建账号或改为
> 环境变量注入。约束由 `backend/src/test/java/com/nutritionagent/SeedContractTest.java`
> 的 `seededPasswordMatchesDocumentedPlaintext` 守住：它要求种子里的散列必须能被
> 上面这个口令匹配——**曾经种子里放的是一个谁也登不进去的散列**（明文未知），
> 编译、单测、前端构建全都照常通过，只有真的去调登录接口才会暴露。

### 后端（MySQL）

**PowerShell（Windows）**

```powershell
cd D:\ds_harness_work\nutrition-agent

# 1) 建库建表
mysql -uroot -p -e "CREATE DATABASE nutrition_agent DEFAULT CHARSET utf8mb4;"
mysql -uroot -p --default-character-set=utf8mb4 nutrition_agent -e "source docs/db/schema.sql"

# 2) 灌种子数据 —— 这一步不能省
mysql -uroot -p --default-character-set=utf8mb4 nutrition_agent -e "source docs/db/data.sql"

# 3) 可选但强烈建议：用 USDA 真实数值替换演示营养数值
mysql -uroot -p --default-character-set=utf8mb4 nutrition_agent -e "source docs/db/nutrition-usda.sql"

# 4) 启动（口令从环境变量注入，仓库里不留明文口令）
cd backend
$env:NA_DB_PASSWORD = '<你的 MySQL 口令>'
mvn spring-boot:run '-Dspring-boot.run.profiles=mysql'
```

> ⚠️ **PowerShell 里不能写 `< docs/db/schema.sql`**：`<` 在 PowerShell 中是保留给将来使用的
> 运算符，会直接报 `ParserError: "<"运算符保留供将来使用`，命令根本不会执行。
> 上面改用 mysql 客户端的 `source` 命令，好处是文件里的 utf8mb4 中文不会经过 PowerShell
> 管道被重新编码。若确实偏好重定向写法，交给 cmd 即可：
> `cmd /c "mysql -uroot -p nutrition_agent < docs\db\schema.sql"`。
> **`--default-character-set=utf8mb4` 建议保留**，否则库里的中文会乱码。

**bash / Git Bash（macOS、Linux）**

```bash
mysql -uroot -p nutrition_agent < docs/db/schema.sql
mysql -uroot -p nutrition_agent < docs/db/data.sql
mysql -uroot -p nutrition_agent < docs/db/nutrition-usda.sql
```

> ⚠️ **只有第 3 步可以重复执行**。`schema.sql` 与 `data.sql` 都不是幂等的：
> 前者会因表已存在而报错，后者是纯 INSERT，在已有数据的库上会主键冲突。
> 想从零重建就先 `DROP DATABASE nutrition_agent;`，再按 1→2→3 重来。

> ⚠️ **漏掉第 2 步会怎样**：`application-mysql.yml` 里 `sql.init.mode: never`，
> 应用既不建表也不灌种子。只有表、没有数据时注册会失败 —— 注册要往
> `sys_user_role` 插一条 `role_id = 1`（`UserService.USER_ROLE_ID`），而空的
> `sys_role` 会让它撞上外键 `fk_ur_role`，`POST /auth/register` 直接 500。
> 这**不会**在编译、单测、前端构建里暴露，只有真去调注册接口才会。
>
> `docs/db/data.sql` **不要手工编辑**：它由 `scripts/gen_mysql_seed.py` 从
> `backend/src/main/resources/db/h2/data.sql` 生成，唯一差异是自增序列重置语句的写法
> （H2 的 `ALTER TABLE t ALTER COLUMN id RESTART WITH n` ↔ MySQL 的 `ALTER TABLE t AUTO_INCREMENT = n`）。
> 改了 H2 那份之后重新跑一次脚本即可；`scripts/check_contract.py` 会拦住不同步的提交。

> **第 3 步的 USDA 补丁是可选的，但建议执行**：种子里的 511 条营养数值全部来自
> `DEMO-SEED`（《AI 食品营养师演示数据集（非真实来源）》）。该补丁把其中 **28 个形态 × 14 个营养素
> = 392 行**换成 USDA FoodData Central 的真实数值，并单独登记来源 `USDA-FDC`；
> 剩下 9 个熟制形态（蒸/炒/炸/烤等）因 USDA 没有烹饪方式一致的条目而**保持演示数据不动**，
> 缺口清单写在 `docs/db/nutrition-usda.sql` 头部。补丁可重复执行（幂等）。
>
> `docs/db/nutrition-usda.sql` **不要手工编辑**：它由 `scripts/gen_usda_nutrition.py` 从
> `docs/db/usda-selection.json` 渲染。改了选样档案后跑一次脚本即可；
> 两者是否同步由 `scripts/check_contract.py` 的检查 I 拦住。

> 在 Windows PowerShell 下，`-D` 属性必须加引号。不加引号时 Maven 会把
> `-Dspring-boot.run.profiles=mysql` 拆成 `-Dspring-boot` 和 `.run.profiles=mysql`
> 两个参数，报 `Unknown lifecycle phase ".run.profiles=mysql"`。
> 正确写法：`mvn spring-boot:run '-Dspring-boot.run.profiles=mysql'`。

### 前端

```bash
cd frontend
npm install
npm run dev
# http://localhost:5173
```

前端**默认永远请求真实接口**，没有 mock 开关（`frontend/.env.example` 里有说明）。
只有在 `import.meta.env.DEV` 且接口请求失败（后端没启动 / 网络不通）时，才会回退到
`frontend/src/api/` 下的本地演示数据，且页面会显著标示「演示数据」；生产构建
（`DEV=false`）下这个兜底完全不生效，接口失败就是失败。

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
