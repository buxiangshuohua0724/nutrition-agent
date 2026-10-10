/**
 * 角色显示名
 *
 * 角色**码**（`USER` / `CONTENT_ADMIN` / `SYS_ADMIN`）是接口契约里的值，
 * 只能出现在请求与逻辑判断里；凡是给人看的地方一律走本模块翻译成中文。
 *
 * 为什么单独抽一个模块：此前顶栏写「管理员」、侧边栏写「非管理角色」、
 * 「我的健康」页写「内容管理员」，同一个用户在三个地方有三种叫法。
 */

import { ROLES } from '../stores/auth.js'

/** 角色码 -> 中文名 */
export const ROLE_LABELS = {
  [ROLES.USER]: '普通用户',
  [ROLES.CONTENT_ADMIN]: '内容管理员',
  [ROLES.SYS_ADMIN]: '系统管理员',
}

/** 游客（未登录）的显示名 */
export const GUEST_LABEL = '游客'

/**
 * 单个角色码的中文名，未知角色码原样返回（便于暴露后端新增了角色而前端未适配）。
 * @param {string} role 角色码
 * @returns {string}
 */
export function roleLabel(role) {
  return ROLE_LABELS[role] || String(role || '')
}

/**
 * 多个角色码的中文名，用「、」连接。
 * @param {string[]} roles 角色码数组
 * @returns {string}
 */
export function roleLabels(roles) {
  const list = Array.isArray(roles) ? roles.filter(Boolean) : []
  if (list.length === 0) return ''
  return list.map(roleLabel).join('、')
}

/**
 * 由用户对象推导**最高身份**的显示名。
 *
 * 顺序即优先级：系统管理员 > 内容管理员 > 普通用户 > 游客。
 * 同时拥有多个角色时只显示最高身份（admin 在种子数据里同时挂 SYS_ADMIN 与 CONTENT_ADMIN）。
 *
 * @param {{roles?: string[]}|null} user 用户对象
 * @returns {string}
 */
export function primaryRoleLabel(user) {
  const roles = user && Array.isArray(user.roles) ? user.roles : []
  if (roles.includes(ROLES.SYS_ADMIN)) return ROLE_LABELS[ROLES.SYS_ADMIN]
  if (roles.includes(ROLES.CONTENT_ADMIN)) return ROLE_LABELS[ROLES.CONTENT_ADMIN]
  if (roles.includes(ROLES.USER)) return ROLE_LABELS[ROLES.USER]
  return GUEST_LABEL
}
