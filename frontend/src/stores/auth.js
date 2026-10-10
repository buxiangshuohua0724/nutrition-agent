/**
 * 鉴权状态（Pinia）
 *
 * 依据契约 4.6：
 *   - 登录 `POST /auth/login`、注册 `POST /auth/register`、退出 `POST /auth/logout`
 *   - 当前用户 `GET /users/me`
 *   - 所有 `/users/me/**` 以令牌中的 userId 为准，**前端绝不传 userId**
 *
 * 权限设计（PRD 12.4）：后台接口要求 `CONTENT_ADMIN` 或 `SYS_ADMIN`，
 * 而这两种角色在权限上是**可区分**的：
 *   - `CONTENT_ADMIN` 内容管理员：食品/图片/营养数据/知识库的录入与审核
 *   - `SYS_ADMIN` 系统管理员：额外拥有系统设置、用户管理、审计日志
 * 因此这里保存完整的角色数组，并提供 `hasRole` / `hasAnyRole` 精确判断，
 * 不做"有任一管理角色就全放行"的粗粒度合并。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as usersApi from '../api/users'
import { TOKEN_STORAGE_KEY } from '../api/client'

/** 角色常量（与后端 RBAC 角色码一致） */
export const ROLES = {
  USER: 'USER',
  CONTENT_ADMIN: 'CONTENT_ADMIN',
  SYS_ADMIN: 'SYS_ADMIN',
}

/** 可进入后台的角色 */
export const ADMIN_ROLES = [ROLES.CONTENT_ADMIN, ROLES.SYS_ADMIN]

/** localStorage 中缓存用户信息的键 */
const USER_STORAGE_KEY = 'na_user'

/** 安全读取 localStorage */
function readStorage(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

/** 安全写入 localStorage */
function writeStorage(key, value) {
  try {
    if (value === null || value === undefined) {
      localStorage.removeItem(key)
    } else {
      localStorage.setItem(key, value)
    }
  } catch {
    // 隐私模式下 localStorage 可能不可写，忽略即可
  }
}

/** 从 localStorage 恢复缓存的用户信息 */
function restoreUser() {
  const raw = readStorage(USER_STORAGE_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  /** @type {import('vue').Ref<string|null>} 访问令牌 */
  const token = ref(readStorage(TOKEN_STORAGE_KEY))

  /** @type {import('vue').Ref<object|null>} 当前用户信息 */
  const user = ref(restoreUser())

  /** 是否已完成一次用户信息拉取（避免重复请求） */
  const profileLoaded = ref(false)

  /** 是否登录 */
  const isLoggedIn = computed(() => Boolean(token.value))

  /**
   * 是否游客。
   * 契约中 AI 问答与图片识别允许游客有限次使用，因此"未登录"即游客态。
   */
  const isGuest = computed(() => !isLoggedIn.value)

  /** 当前用户角色数组（始终是数组，绝不为 null） */
  const roles = computed(() => {
    const list = user.value && Array.isArray(user.value.roles) ? user.value.roles : []
    return list.filter((role) => typeof role === 'string' && role !== '')
  })

  /** 是否为内容管理员（精确判断，不与系统管理员混同） */
  const isContentAdmin = computed(() => roles.value.includes(ROLES.CONTENT_ADMIN))

  /** 是否为系统管理员（精确判断） */
  const isSysAdmin = computed(() => roles.value.includes(ROLES.SYS_ADMIN))

  /** 是否可进入后台（内容管理员或系统管理员） */
  const isAdmin = computed(() => roles.value.some((role) => ADMIN_ROLES.includes(role)))

  /** 展示名：昵称 → 用户名 → 游客 */
  const displayName = computed(() => {
    if (!user.value) return '未登录'
    return user.value.nickname || user.value.username || '用户'
  })

  /** 头像地址 */
  const avatarUrl = computed(() => (user.value && user.value.avatarUrl) || null)

  /**
   * 判断当前用户是否拥有某个角色（精确匹配，大小写不敏感）。
   * @param {string} role 角色码，例如 `CONTENT_ADMIN`
   * @returns {boolean}
   */
  function hasRole(role) {
    if (!role) return false
    const target = String(role).toUpperCase()
    return roles.value.some((item) => item.toUpperCase() === target)
  }

  /**
   * 判断当前用户是否拥有其中任意一个角色。
   * @param {string[]} roleList 角色码数组
   * @returns {boolean}
   */
  function hasAnyRole(roleList) {
    const list = Array.isArray(roleList) ? roleList : []
    return list.some((role) => hasRole(role))
  }

  /**
   * 判断当前用户是否拥有全部角色。
   * @param {string[]} roleList 角色码数组
   * @returns {boolean}
   */
  function hasAllRoles(roleList) {
    const list = Array.isArray(roleList) ? roleList : []
    if (list.length === 0) return false
    return list.every((role) => hasRole(role))
  }

  /**
   * 写入令牌。
   * @param {string|null} value 令牌
   */
  function setToken(value) {
    token.value = value || null
    writeStorage(TOKEN_STORAGE_KEY, token.value)
  }

  /**
   * 写入用户信息。
   * @param {object|null} value 用户对象
   */
  function setUser(value) {
    user.value = value || null
    writeStorage(USER_STORAGE_KEY, value ? JSON.stringify(value) : null)
  }

  /**
   * 登录。
   * @param {{username: string, password: string}} credentials
   * @returns {Promise<{user: object|null, roles: string[]}>}
   * @throws {import('../api/client').ApiError} 登录失败（含 401/40001 等）时抛出
   */
  async function login(credentials) {
    const data = await usersApi.login(credentials)
    const accessToken = data && (data.token || data.accessToken)
    if (!accessToken) {
      throw new Error('登录响应中没有令牌字段，请检查后端是否符合接口契约')
    }
    setToken(accessToken)
    const profile = (data && data.user) || null
    setUser(profile)
    profileLoaded.value = Boolean(profile)
    if (!profile) {
      // 后端只返回令牌时，补一次 /users/me
      await fetchProfile().catch(() => {})
    }
    return { user: user.value, roles: roles.value }
  }

  /**
   * 注册。契约：`POST /auth/register`。
   * 注册成功后不自动登录（由后端策略决定），此处仅返回结果。
   * @param {{username: string, password: string, nickname?: string, email?: string}} payload
   * @returns {Promise<object|null>}
   */
  async function register(payload) {
    return usersApi.register(payload)
  }

  /**
   * 拉取当前用户信息（`GET /users/me`）。
   * @param {object} [options]
   * @param {boolean} [options.force=false] 强制刷新
   * @returns {Promise<object|null>}
   */
  async function fetchProfile(options = {}) {
    if (!token.value) {
      return null
    }
    if (profileLoaded.value && !options.force) {
      return user.value
    }
    const profile = await usersApi.getCurrentUser()
    setUser(profile)
    profileLoaded.value = true
    return profile
  }

  /**
   * 更新资料（`PUT /users/me/profile`）并同步本地。
   * @param {object} payload 资料字段
   * @returns {Promise<object|null>}
   */
  async function updateProfile(payload) {
    const updated = await usersApi.updateProfile(payload)
    if (updated && typeof updated === 'object') {
      setUser({ ...(user.value || {}), ...updated })
    }
    return user.value
  }

  /**
   * 退出登录：先通知后端，无论成功与否都清空本地登录态。
   * @returns {Promise<void>}
   */
  async function logout() {
    if (token.value) {
      // 后端退出失败不应阻塞用户，静默处理
      await usersApi.logout({ silent: true }).catch(() => {})
    }
    clearAuth()
  }

  /** 仅清空本地登录态（401 拦截、令牌失效时调用） */
  function clearAuth() {
    setToken(null)
    setUser(null)
    profileLoaded.value = false
  }

  return {
    // 状态
    token,
    user,
    profileLoaded,
    // 派生
    isLoggedIn,
    isGuest,
    roles,
    isAdmin,
    isContentAdmin,
    isSysAdmin,
    displayName,
    avatarUrl,
    // 方法
    hasRole,
    hasAnyRole,
    hasAllRoles,
    setToken,
    setUser,
    login,
    register,
    fetchProfile,
    updateProfile,
    logout,
    clearAuth,
  }
})
