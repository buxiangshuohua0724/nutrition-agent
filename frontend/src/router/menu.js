/**
 * 导航菜单定义
 *
 * 菜单项与路由表一一对应；图标名为 `src/assets/icons/` 下的文件名（不含扩展名）。
 * 图标文件缺失时 `NavIcon` 会自动渲染内联占位符，不影响菜单可用性。
 */

/** 用户侧主菜单 */
export const USER_MENU = [
  { label: '首页', to: '/', icon: 'home' },
  { label: '食品搜索', to: '/search', icon: 'search' },
  { label: '分类浏览', to: '/categories', icon: 'category' },
  { label: '图片识别', to: '/recognize', icon: 'image' },
  { label: 'AI问答', to: '/chat', icon: 'chat' },
  { label: '我的健康', to: '/me', icon: 'health' },
]

/** 用户侧底部菜单（与主菜单视觉分组） */
export const USER_FOOTER_MENU = [{ label: '设置', to: '/me', icon: 'settings' }]

/**
 * 后台菜单。
 * `roles` 为空表示任何管理角色都可访问；显式列出角色时做精确校验，
 * 因此 `CONTENT_ADMIN` 不会看到仅 `SYS_ADMIN` 可见的「系统设置」。
 */
export const ADMIN_MENU = [
  { label: '食品管理', to: '/admin/foods', icon: 'category', roles: [] },
  { label: '图片管理', to: '/admin/images', icon: 'image', roles: [] },
  { label: '营养数据管理', to: '/admin/nutrition', icon: 'chart', roles: [] },
  { label: '知识库管理', to: '/admin/knowledge', icon: 'leaf', roles: [] },
  { label: '内容审核', to: '/admin/review', icon: 'check', roles: [] },
  { label: '系统设置', to: '/admin/settings', icon: 'settings', roles: ['SYS_ADMIN'] },
]

/** 顶部标题：路由 name → 面包屑父级名称（用于详情页显示较友好的标题） */
export const ROUTE_PARENT_LABEL = {
  category: '分类浏览',
  'food-detail': '食品搜索',
  report: '营养分析报告',
  'me-favorites': '我的健康',
  'me-history': '我的健康',
}
