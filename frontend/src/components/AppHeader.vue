<script setup>
/**
 * 顶部标题栏
 *
 * 左侧显示当前页面标题（来自路由 `meta.title`），可选择显示一个轻量搜索框；
 * 右侧是用户区：已登录显示昵称 + 角色 + 退出，未登录显示「登录 / 注册」。
 * 后台版式（`variant === 'admin'`）额外显示「管理员」标识。
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import NavIcon from './NavIcon.vue'
import { useAuthStore } from '../stores/auth.js'
import { useToastStore } from '../stores/toast.js'
import { SITE_NAME } from '../router/index.js'
import { primaryRoleLabel } from '../utils/roles.js'

const props = defineProps({
  /** `user` | `admin` */
  variant: {
    type: String,
    default: 'user',
  },
})

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

const headerSearch = ref('')
const menuOpen = ref(false)

const title = computed(() => (route.meta && route.meta.title) || SITE_NAME)
const isAdminLayout = computed(() => props.variant === 'admin')

/** 用户昵称首字母，用于头像占位 */
const avatarInitial = computed(() => {
  const name = authStore.displayName || ''
  return name ? name.slice(0, 1).toUpperCase() : '?'
})

/** 角色中文名（统一走 utils/roles，避免各页各写一套） */
const roleLabel = computed(() => primaryRoleLabel(authStore.user))

/** 提交顶栏搜索 */
function submitSearch() {
  const keyword = headerSearch.value.trim()
  if (keyword === '') return
  router.push({ path: '/search', query: { q: keyword } })
}

/** 退出登录 */
async function handleLogout() {
  menuOpen.value = false
  await authStore.logout()
  toastStore.success('已退出登录')
  if (isAdminLayout.value) {
    router.push('/')
  }
}

/** 打开/关闭用户菜单 */
function toggleMenu() {
  menuOpen.value = !menuOpen.value
}
</script>

<template>
  <header class="na-header" :class="{ 'na-header--admin': isAdminLayout }">
    <div class="na-header__left">
      <h1 class="na-header__title">
        <span v-if="isAdminLayout" class="na-header__admin-tag">{{ roleLabel }}</span>
        {{ title }}
      </h1>
    </div>

    <div class="na-header__center">
      <form v-if="!isAdminLayout" class="na-header__search" role="search" @submit.prevent="submitSearch">
        <NavIcon name="search" :size="16" />
        <input
          v-model="headerSearch"
          class="na-header__search-input"
          type="search"
          placeholder="搜索食品名称"
          aria-label="搜索食品名称"
        />
        <button class="na-header__search-btn" type="submit">搜索</button>
      </form>
    </div>

    <div class="na-header__right">
      <template v-if="authStore.isLoggedIn">
        <button class="na-header__user" type="button" :aria-expanded="menuOpen" @click="toggleMenu">
          <span class="na-header__avatar" aria-hidden="true">{{ avatarInitial }}</span>
          <span class="na-header__user-meta">
            <span class="na-header__user-name">{{ authStore.displayName }}</span>
            <span class="na-header__user-role">{{ roleLabel }}</span>
          </span>
          <NavIcon name="chevron-down" :size="14" />
        </button>

        <div v-if="menuOpen" class="na-header__menu" role="menu">
          <RouterLink class="na-header__menu-item" to="/me" role="menuitem" @click="menuOpen = false">
            我的健康
          </RouterLink>
          <RouterLink class="na-header__menu-item" to="/me/favorites" role="menuitem" @click="menuOpen = false">
            我的收藏
          </RouterLink>
          <RouterLink
            v-if="authStore.isAdmin"
            class="na-header__menu-item"
            :to="isAdminLayout ? '/' : '/admin/foods'"
            role="menuitem"
            @click="menuOpen = false"
          >
            {{ isAdminLayout ? '返回用户端' : '进入管理后台' }}
          </RouterLink>
          <button class="na-header__menu-item na-header__menu-item--danger" type="button" role="menuitem" @click="handleLogout">
            退出登录
          </button>
        </div>
      </template>

      <template v-else>
        <RouterLink class="na-btn na-btn--primary na-btn--sm" to="/login">登录 / 注册</RouterLink>
      </template>
    </div>
  </header>
</template>

<style scoped>
.na-header {
  position: sticky;
  top: 0;
  z-index: 20;
  flex: 0 0 auto;
  height: var(--na-header-height, 56px);
  display: flex;
  align-items: center;
  gap: var(--na-space-4, 16px);
  padding: 0 var(--na-space-6, 24px);
  background: var(--na-color-surface, #ffffff);
  border-bottom: 1px solid var(--na-color-border, #e5e7eb);
}

.na-header--admin {
  background: var(--na-color-surface-alt, #1f2937);
  border-bottom-color: #374151;
  color: #f9fafb;
}

.na-header__left {
  flex: 0 1 auto;
  min-width: 0;
}

.na-header__title {
  margin: 0;
  font-size: var(--na-font-size-lg, 17px);
  font-weight: 600;
  color: var(--na-color-text, #111827);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
}

.na-header--admin .na-header__title {
  color: #f9fafb;
}

.na-header__admin-tag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 10px;
  background: #374151;
  color: #f9fafb;
  font-size: 12px;
  font-weight: 500;
}

.na-header__center {
  flex: 1 1 auto;
  display: flex;
  justify-content: center;
  min-width: 0;
}

.na-header__search {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
  width: 100%;
  max-width: 420px;
  height: var(--na-control-height-md, 36px);
  padding: 0 4px 0 10px;
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-md, 8px);
  background: var(--na-color-bg, #f8fafc);
  color: var(--na-color-text-muted, #6b7280);
}

.na-header__search-input {
  flex: 1 1 auto;
  min-width: 0;
  height: 100%;
  border: none;
  background: transparent;
  font-size: var(--na-font-size-sm, 14px);
  color: var(--na-color-text, #111827);
  outline: none;
}

.na-header__search-btn {
  flex: 0 0 auto;
  height: 28px;
  padding: 0 10px;
  border: none;
  border-radius: var(--na-radius-sm, 6px);
  background: var(--na-color-primary-500, #3b82f6);
  color: #ffffff;
  font-size: 13px;
  cursor: pointer;
}

.na-header__right {
  position: relative;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
}

.na-header__user {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
  height: var(--na-control-height-md, 36px);
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: var(--na-radius-md, 8px);
  background: transparent;
  cursor: pointer;
  color: inherit;
}

.na-header__user:hover {
  background: var(--na-color-bg, #f8fafc);
}

.na-header--admin .na-header__user:hover {
  background: rgba(255, 255, 255, 0.08);
}

.na-header__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--na-color-primary-500, #3b82f6);
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
}

.na-header__user-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.2;
}

.na-header__user-name {
  font-size: var(--na-font-size-sm, 14px);
  font-weight: 600;
}

.na-header__user-role {
  font-size: 11px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-header--admin .na-header__user-role {
  color: #9ca3af;
}

.na-header__menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 160px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-md, 8px);
  background: var(--na-color-surface, #ffffff);
  box-shadow: var(--na-shadow-md, 0 8px 24px rgba(15, 23, 42, 0.12));
}

.na-header__menu-item {
  display: block;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: var(--na-radius-sm, 6px);
  background: transparent;
  text-align: left;
  font-size: var(--na-font-size-sm, 14px);
  color: var(--na-color-text, #111827);
  text-decoration: none;
  cursor: pointer;
}

.na-header__menu-item:hover {
  background: var(--na-color-bg, #f8fafc);
}

.na-header__menu-item--danger {
  color: var(--na-color-danger, #ef4444);
}

@media (max-width: 900px) {
  .na-header__center,
  .na-header__user-meta {
    display: none;
  }
}
</style>
