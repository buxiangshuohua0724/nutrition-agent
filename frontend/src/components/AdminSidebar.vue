<script setup>
/**
 * 后台侧边栏
 *
 * 菜单按角色过滤：`roles` 非空的菜单项仅对应角色可见，
 * 从而在界面上区分 `CONTENT_ADMIN` 与 `SYS_ADMIN`（PRD 权限设计）。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import NavIcon from './NavIcon.vue'
import { ADMIN_MENU } from '../router/menu.js'
import { useAuthStore } from '../stores/auth.js'

const route = useRoute()
const authStore = useAuthStore()

/** 按当前用户角色过滤后的菜单 */
const visibleMenu = computed(() =>
  ADMIN_MENU.filter((item) => {
    if (!item.roles || item.roles.length === 0) return true
    return authStore.hasAnyRole(item.roles)
  }),
)

/**
 * @param {string} to 目标路径
 * @returns {boolean} 是否激活
 */
function isActive(to) {
  return route.path === to || route.path.startsWith(`${to}/`)
}
</script>

<template>
  <aside class="na-admin-sidebar" aria-label="后台导航">
    <div class="na-admin-sidebar__brand">
      <span class="na-admin-sidebar__mark" aria-hidden="true">管</span>
      <span class="na-admin-sidebar__brand-text">管理后台</span>
    </div>

    <nav class="na-admin-sidebar__nav">
      <RouterLink
        v-for="item in visibleMenu"
        :key="item.to"
        :to="item.to"
        class="na-admin-sidebar__link"
        :class="{ 'is-active': isActive(item.to) }"
        :aria-current="isActive(item.to) ? 'page' : undefined"
      >
        <NavIcon :name="item.icon" :size="18" />
        <span class="na-admin-sidebar__label">{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div class="na-admin-sidebar__footer">
      <RouterLink to="/" class="na-admin-sidebar__link na-admin-sidebar__link--back">
        <NavIcon name="home" :size="18" />
        <span class="na-admin-sidebar__label">返回用户端</span>
      </RouterLink>
      <p class="na-admin-sidebar__role">当前角色：{{ authStore.isAdmin ? authStore.roles.join(' / ') : '非管理角色' }}</p>
    </div>
  </aside>
</template>

<style scoped>
.na-admin-sidebar {
  flex: 0 0 auto;
  width: var(--na-nav-width, 184px);
  min-height: 100vh;
  padding: var(--na-space-4, 16px) var(--na-space-3, 12px);
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
  background: var(--na-color-surface-alt, #1f2937);
  color: #e5e7eb;
  position: sticky;
  top: 0;
  align-self: flex-start;
}

.na-admin-sidebar__brand {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
  padding: 6px 8px var(--na-space-3, 12px);
}

.na-admin-sidebar__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: var(--na-radius-md, 8px);
  background: #374151;
  color: #f9fafb;
  font-size: 13px;
  font-weight: 700;
}

.na-admin-sidebar__brand-text {
  font-size: var(--na-font-size-md, 15px);
  font-weight: 600;
  color: #f9fafb;
}

.na-admin-sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.na-admin-sidebar__link {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
  padding: 0 10px;
  height: 36px;
  border-radius: var(--na-radius-md, 8px);
  color: #d1d5db;
  font-size: var(--na-font-size-sm, 14px);
  text-decoration: none;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.na-admin-sidebar__link :deep(.na-icon) {
  filter: invert(1) brightness(1.8);
  opacity: 0.85;
}

.na-admin-sidebar__link:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.na-admin-sidebar__link.is-active {
  background: var(--na-color-primary-500, #3b82f6);
  color: #ffffff;
  font-weight: 600;
}

.na-admin-sidebar__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.na-admin-sidebar__footer {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.na-admin-sidebar__link--back {
  color: #9ca3af;
}

.na-admin-sidebar__role {
  margin: var(--na-space-2, 8px) 8px 0;
  font-size: 12px;
  line-height: 1.5;
  color: #9ca3af;
}
</style>
