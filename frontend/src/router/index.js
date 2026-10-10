/**
 * 路由表
 *
 * 路由路径与 `docs/ROADMAP.md`「原型界面 → 页面实现对照」完全一致，
 * 并补全 PRD FR-007 / FR-008 的「我的健康」相关页面。
 *
 * 约定：
 *   - 全部使用懒加载（`() => import(...)`），按路由切分产物
 *   - 每个路由都带 `meta.title`，由 `router.afterEach` 写入 `document.title`
 *   - `meta.layout` 决定外壳：`default`（用户侧）| `admin`（后台侧）| `blank`（无外壳）
 *   - `meta.requiresAdmin` / `meta.requiresSysAdmin` 仅用于**体验层**的菜单与提示，
 *     真正的权限校验由后端返回 `40301`（PRD 12.4）
 */

import { createRouter, createWebHistory } from 'vue-router'

/** 站点名，用于拼 document.title */
export const SITE_NAME = 'AI 食品营养师'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/HomeView.vue'),
    meta: { title: '首页', layout: 'default' },
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('../views/SearchView.vue'),
    meta: { title: '食品搜索', layout: 'default' },
  },
  {
    path: '/categories',
    name: 'categories',
    component: () => import('../views/CategoriesView.vue'),
    meta: { title: '分类浏览', layout: 'default' },
  },
  {
    path: '/category/:slug',
    name: 'category',
    component: () => import('../views/CategoryView.vue'),
    meta: { title: '分类食品', layout: 'default' },
  },
  {
    path: '/food/:id',
    name: 'food-detail',
    component: () => import('../views/FoodDetailView.vue'),
    meta: { title: '食品详情', layout: 'default' },
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('../views/ChatView.vue'),
    meta: { title: 'AI 营养师问答', layout: 'default' },
  },
  {
    path: '/recognize',
    name: 'recognize',
    component: () => import('../views/RecognizeView.vue'),
    meta: { title: '图片识别', layout: 'default' },
  },
  {
    path: '/report/:foodId',
    name: 'report',
    component: () => import('../views/ReportView.vue'),
    meta: { title: '营养分析报告', layout: 'default' },
  },
  {
    path: '/me',
    name: 'me',
    component: () => import('../views/MeView.vue'),
    meta: { title: '我的健康', layout: 'default' },
  },
  {
    path: '/me/favorites',
    name: 'me-favorites',
    component: () => import('../views/FavoritesView.vue'),
    meta: { title: '我的收藏', layout: 'default' },
  },
  {
    path: '/me/history',
    name: 'me-history',
    component: () => import('../views/HistoryView.vue'),
    meta: { title: '浏览与对话历史', layout: 'default' },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { title: '登录 / 注册', layout: 'blank' },
  },

  // ---------------- 管理后台（独立外壳，不使用用户侧侧边栏） ----------------
  {
    path: '/admin',
    redirect: '/admin/foods',
  },
  {
    path: '/admin/foods',
    name: 'admin-foods',
    component: () => import('../views/admin/AdminFoodsView.vue'),
    meta: { title: '食品管理', layout: 'admin', requiresAdmin: true },
  },
  {
    path: '/admin/images',
    name: 'admin-images',
    component: () => import('../views/admin/AdminImagesView.vue'),
    meta: { title: '图片管理', layout: 'admin', requiresAdmin: true },
  },
  {
    path: '/admin/nutrition',
    name: 'admin-nutrition',
    component: () => import('../views/admin/AdminNutritionView.vue'),
    meta: { title: '营养数据管理', layout: 'admin', requiresAdmin: true },
  },
  {
    path: '/admin/knowledge',
    name: 'admin-knowledge',
    component: () => import('../views/admin/AdminKnowledgeView.vue'),
    meta: { title: '知识库管理', layout: 'admin', requiresAdmin: true },
  },
  {
    path: '/admin/review',
    name: 'admin-review',
    component: () => import('../views/admin/AdminReviewView.vue'),
    meta: { title: '内容审核', layout: 'admin', requiresAdmin: true },
  },
  {
    path: '/admin/settings',
    name: 'admin-settings',
    component: () => import('../views/admin/AdminSettingsView.vue'),
    meta: { title: '系统设置', layout: 'admin', requiresAdmin: true, requiresSysAdmin: true },
  },

  // ---------------- 404 ----------------
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../views/NotFoundView.vue'),
    meta: { title: '页面不存在', layout: 'default' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

/**
 * 设置文档标题。
 * @param {import('vue-router').RouteLocationNormalized} route 目标路由
 */
function applyDocumentTitle(route) {
  const title = route.meta && route.meta.title
  document.title = title ? `${title} · ${SITE_NAME}` : SITE_NAME
}

router.afterEach((to) => {
  applyDocumentTitle(to)
})

export { routes }
export default router
