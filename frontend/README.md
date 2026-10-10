# AI 食品营养师 · 前端工程

Vue 3 单页应用，负责「查询食品营养 → 按重量折算摄入量 → AI 问答 → 图片识别 → 营养分析报告」的用户界面，以及内容管理与系统设置后台。

- **技术栈**：Vue 3.5（`<script setup>`）+ Vite 5 + Vue Router 4 + Pinia 2
- **零 UI 框架**：不引入 Element Plus / Ant Design Vue / Naive / Vuetify / Tailwind / UnoCSS / Sass，样式全部是原生 CSS + CSS 变量
- **HTTP**：原生 `fetch` 封装（不使用 axios）
- **运行环境**：Node v24.11.1、pnpm

---

## 一、安装与启动

```bash
cd frontend
pnpm install
pnpm run dev        # 开发服务器，默认 http://localhost:5173
pnpm run build      # 生产构建，产物在 dist/
pnpm run preview    # 本地预览构建产物
```

开发服务器会把 `/api` 前缀的请求代理到后端：

| 前端请求 | 实际转发到 |
| --- | --- |
| `/api/v1/**` | `http://localhost:8080/api/v1/**` |

代理配置在 `vite.config.js` 的 `server.proxy`。**后端未启动时页面不会白屏**：接口失败会走错误提示与空状态；开发模式下部分页面会回退到内置演示数据并显著标注「演示数据」。

### 环境变量

复制 `.env.example` 为 `.env.local` 后按需修改（`.env.*` 已被 `.gitignore` 忽略，**不要提交真实密钥**）：

```bash
# 接口基础路径。留空时默认 /api/v1，由 Vite 代理转发到后端。
VITE_API_BASE=/api/v1
```

前端**只有这一个**环境变量。AI 模型密钥、视觉服务密钥、限流阈值、图片安全校验等全部由后端负责，前端不读取也不展示。

---

## 二、目录结构

```
frontend/
├── index.html                 入口 HTML（lang="zh-CN"）
├── vite.config.js             别名 @ → src、开发端口 5173、/api 代理
├── .env.example               环境变量样例
└── src/
    ├── main.js                应用装配：样式 → Pinia → 路由 → 注入 API 客户端钩子
    ├── App.vue                按路由 meta.layout 选择外壳并渲染 <RouterView>
    ├── api/                   接口层（每个函数都有 JSDoc：接口路径 + 请求/响应结构）
    │   ├── client.js          fetch 封装：统一响应 {code,message,data,traceId}、Bearer 令牌
    │   ├── error-codes.js     21 条错误码 → 中文提示映射
    │   ├── foods.js           分类 / 搜索 / 详情 / 营养表 / 热门 / 相关
    │   ├── nutrition.js       按重量折算
    │   ├── agent.js           AI 问答与会话
    │   ├── knowledge.js       知识库检索
    │   ├── recognitions.js    图片识别与确认
    │   ├── users.js           鉴权 / 资料 / 收藏 / 数据删除
    │   ├── admin.js           管理后台全部接口
    │   └── demo-data.js       开发期演示数据（结构与契约一致）
    ├── assets/                视觉素材（见第五节，由素材负责人维护）
    ├── design/                设计令牌与基础组件类（见第五节，由素材负责人维护）
    ├── components/            通用组件
    ├── layouts/               三种外壳：DefaultShell / AdminShell / BlankShell
    ├── router/                路由表与菜单定义
    ├── stores/                Pinia：auth / favorites / history / chat / toast
    ├── styles/                自维护兜底样式与后台样式（不覆盖 design/）
    ├── utils/                 format / assets / validators / rich-text / dev
    └── views/                 页面（admin/ 下为后台页面）
```

---

## 三、路由表

路由与 `docs/ROADMAP.md` 的「原型 9 板块 → 前端路由对照表」保持一致。

| 路由 | 页面 | 外壳 | 说明 |
| --- | --- | --- | --- |
| `/` | `HomeView` | 用户 | 首页：大搜索框 + 四个圆形入口 + 热门食品 |
| `/search` | `SearchView` | 用户 | 搜索：关键词 / 分类 / 排序 / 分页，条件同步到地址栏 |
| `/categories` | `CategoriesView` | 用户 | 分类浏览入口 |
| `/category/:slug` | `CategoryView` | 用户 | 某分类下的食品网格 |
| `/food/:id` | `FoodDetailView` | 用户 | 食品详情：多形态切换 + 六个标签页 |
| `/chat` | `ChatView` | 用户 | AI 问答：多轮会话、参考来源、不确定性提示 |
| `/recognize` | `RecognizeView` | 用户 | 图片识别：上传 → 候选 → 确认 / 手动搜索 |
| `/report/:foodId` | `ReportView` | 用户 | 营养分析报告 + 按重量折算 |
| `/me` | `MeView` | 用户 | 我的健康：资料编辑、数据删除 |
| `/me/favorites` | `FavoritesView` | 用户 | 我的收藏 |
| `/me/history` | `HistoryView` | 用户 | 浏览历史（仅本机浏览器保存） |
| `/login` | `LoginView` | 无外壳 | 登录 / 注册 |
| `/admin` | → 重定向 `/admin/foods` | 后台 | 后台首页 |
| `/admin/foods` | `AdminFoodsView` | 后台 | 食品管理：搜索 / 新增 / 编辑 / 审核 / 发布 / 删除 |
| `/admin/images` | `AdminImagesView` | 后台 | 图片管理 |
| `/admin/nutrition` | `AdminNutritionView` | 后台 | 营养数据管理（每条数据必须填来源标识） |
| `/admin/knowledge` | `AdminKnowledgeView` | 后台 | 知识库管理 + 重建索引 |
| `/admin/review` | `AdminReviewView` | 后台 | 内容审核（按食品的审核状态筛选） |
| `/admin/settings` | `AdminSettingsView` | 后台 | 系统设置（仅 `SYS_ADMIN` 可见菜单项） |
| `/:pathMatch(.*)*` | `NotFoundView` | 用户 | 404 |

所有路由均为懒加载（`() => import(...)`），`router.afterEach` 会把 `meta.title` 写入 `document.title`。

**权限约定**：角色分 `USER` / `CONTENT_ADMIN` / `SYS_ADMIN`，前端按角色过滤后台菜单项，但**最终权限校验始终以后端为准**。

---

## 四、与后端联调的要点

1. **统一响应结构**：`{ code, message, data, traceId }`，`code === 0` 表示成功。`code !== 0` 时 `client.js` 抛出带 `code` / `message` / `traceId` 的 `ApiError`，并按 `error-codes.js` 的映射弹出中文提示。
2. **登录态**：令牌存 `localStorage` 的 `na_token`，请求自动携带 `Authorization: Bearer <token>`；收到 `401` / `40101` 时清除登录态并跳转 `/login`，同时带上 `redirect` 查询参数。
3. **限流**：`429` / `42901` 统一提示「调用过于频繁」。各接口的真实阈值由后端配置（问答：登录 30/分、游客 5/天；识别：登录 20/小时、游客 3/天；其他读接口 120/分）。
4. **分页**：`{ list, page, pageSize, total, totalPages }`。
5. **图片上传**：`POST /recognitions`，`multipart/form-data`，字段名固定为 `file`。前端只做类型（`image/jpeg`、`image/png`、`image/webp`）与大小（≤10MB）预校验，**文件内容（魔数）校验必须由后端完成，前端不做也不假装做**。
6. **营养数据零兜底（本工程最重要的一条约束）**：营养数值一律原样展示接口返回的 `displayText`；缺失显示「暂无数据」；**任何地方都不允许 `value ?? 0`、`|| 0`、`|| '0'` 这类把缺失当零的写法**，计算占比时同样禁止。营养表的三态含义：
   - `PRESENT`：有值，展示 `displayText`；
   - `ZERO`：经过确认的 0（例如 `0 g`），照实展示，占比可以显示 0%；
   - `MISSING`：数据缺失，展示「暂无数据」，`amount` 为 `null`，**绝不参与求和与占比**。
7. **不渲染后端 HTML**：AI 回答、食品介绍等文本一律经 `utils/rich-text.js` 解析成块数组后用真实元素渲染（`RichText.vue`），**不使用 `v-html`**。
8. **演示数据兜底**：只在 `import.meta.env.DEV`（开发模式）且接口失败时生效，且页面上必须显示「演示数据」横幅；生产构建下兜底彻底失效。列表页失败时回退的是**空结果集**，不是假数据。

---

## 五、素材约定（`src/assets/` 与 `src/design/`）

> 这两个目录由素材负责人维护，**业务代码不修改其中的文件**。完整清单见 `src/assets/README.md`。

- `src/design/tokens.css`：全部设计令牌，变量以 `--na-` 前缀定义在 `:root`，例如 `--na-color-primary-500`、`--na-nav-width: 184px`、`--na-header-height: 56px`。
- `src/design/base.css`：reset 与基础类，例如 `.na-btn` / `.na-btn--primary` / `.na-btn--ghost`、`.na-input`、`.na-card`、`.na-badge`、`.na-table`、`.na-empty`、`.na-skeleton`、`.na-tabs`、`.na-sr-only`。
- `src/assets/icons/*.svg`：33 个 24×24 图标，`stroke="currentColor"`，跟随父元素颜色。
- `src/assets/categories/*.svg`：10 个 64×64 分类插画，**文件名 = 分类 `slug`**（`vegetables.svg` / `fruits.svg` / `meat.svg` / `aquatic.svg` / `eggs.svg` / `grains.svg` / `dairy.svg` / `nuts.svg` / `legumes.svg` / `others.svg`）。
- `src/assets/foods/*.svg`：20 个 160×120 食品插画，**文件名 = `food.code` 小写并把下划线换成连字符**（`VEG_TOMATO` → `veg-tomato.svg`）。

素材通过 `import.meta.glob` 动态解析（`utils/assets.js`），**找不到时返回 `null` 并由组件渲染内联占位图形**，因此素材缺失不会导致构建或运行报错。分类 slug 存在单复数与用词差异（如 `vegetable` ↔ `vegetables`、`seafood` ↔ `aquatic`），`utils/assets.js` 中的别名表已覆盖这两套写法。

**设计令牌缺失时的兜底**：`styles/fallback-tokens.css` 定义同名 `--na-*` 变量与基础组件类，且全部写在 `:root:not(.na-design-ready)` 之下。`styles/index.js` 先静态引入兜底样式，再用 `import.meta.glob('../design/*.css')` 惰性探测并加载正式令牌；加载成功时给 `<html>` 加上 `.na-design-ready`，兜底立即失效。因此 `design/` 目录临时缺文件时页面依然可用，且正式令牌永远不会被兜底覆盖；缺失时页面顶部会显示一条提示条说明当前在使用内置兜底样式。

---

## 六、无障碍与安全

- 交互元素使用语义化标签（`<nav>` / `<main>` / `<button>` / `<label>`），装饰性图标加 `aria-hidden="true"`，纯视觉信息提供 `.na-sr-only` 文本。
- 弹窗（`ConfirmDialog`、后台编辑弹窗）支持 `Esc` 关闭，遮罩点击可关闭。
- 加载状态、空状态、错误状态、无权限状态分别用不同组件与文案表达，避免把接口错误说成「没有数据」。
- **不提交真实密钥**；前端不读取、不展示任何服务端密钥。
