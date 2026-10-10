<script setup>
/**
 * 用户侧侧边栏
 *
 * 宽度取自设计令牌 `--na-nav-width`（兜底 184px）。
 * 高亮规则：`/` 只在精确匹配时高亮，其余路径按前缀匹配（让 `/food/12` 仍高亮「食品搜索」）。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import NavIcon from './NavIcon.vue'
import { USER_MENU, USER_FOOTER_MENU } from '../router/menu.js'
import { useAuthStore } from '../stores/auth.js'
import { useFavoritesStore } from '../stores/favorites.js'
import { primaryRoleLabel } from '../utils/roles.js'

const route = useRoute()
const authStore = useAuthStore()
const favoritesStore = useFavoritesStore()

/** 「我的健康」分组下的二级入口 */
const personalLinks = computed(() => [
  { label: '我的收藏', to: '/me/favorites', count: favoritesStore.count },
  { label: '浏览历史', to: '/me/history' },
])

/**
 * 判断菜单是否处于激活态。
 * @param {string} to 目标路径
 * @returns {boolean}
 */
function isActive(to) {
  const path = route.path
  if (to === '/') return path === '/'
  return path === to || path.startsWith(`${to}/`)
}

/** 收藏数角标（仅在大于 0 时显示） */
const favoriteBadge = computed(() => (favoritesStore.count > 0 ? String(favoritesStore.count) : ''))
</script>

<template>
  <aside class="na-sidebar" aria-label="主导航">
    <RouterLink to="/" class="na-sidebar__logo">
      <span class="na-sidebar__logo-mark" aria-hidden="true">AI</span>
      <span class="na-sidebar__logo-text">食品营养师</span>
    </RouterLink>

    <nav class="na-sidebar__nav">
      <RouterLink
        v-for="item in USER_MENU"
        :key="item.to"
        :to="item.to"
        class="na-sidebar__link"
        :class="{ 'is-active': isActive(item.to) }"
        :aria-current="isActive(item.to) ? 'page' : undefined"
      >
        <NavIcon :name="item.icon" :size="18" />
        <span class="na-sidebar__label">{{ item.label }}</span>
        <span v-if="item.to === '/me' && favoriteBadge" class="na-sidebar__badge">{{ favoriteBadge }}</span>
      </RouterLink>
    </nav>

    <div class="na-sidebar__divider" role="presentation"></div>

    <nav class="na-sidebar__nav na-sidebar__nav--secondary" aria-label="我的健康">
      <RouterLink
        v-for="item in personalLinks"
        :key="item.to"
        :to="item.to"
        class="na-sidebar__link na-sidebar__link--sub"
        :class="{ 'is-active': isActive(item.to) }"
        :aria-current="isActive(item.to) ? 'page' : undefined"
      >
        <span class="na-sidebar__dot" aria-hidden="true"></span>
        <span class="na-sidebar__label">{{ item.label }}</span>
        <span v-if="item.count" class="na-sidebar__badge">{{ item.count }}</span>
      </RouterLink>
    </nav>

    <div class="na-sidebar__footer">
      <RouterLink
        v-for="item in USER_FOOTER_MENU"
        :key="item.label"
        :to="item.to"
        class="na-sidebar__link"
        :class="{ 'is-active': isActive(item.to) }"
      >
        <NavIcon :name="item.icon" :size="18" />
        <span class="na-sidebar__label">{{ item.label }}</span>
      </RouterLink>

      <div class="na-sidebar__user">
        <template v-if="authStore.isLoggedIn">
          <span class="na-sidebar__user-name">{{ authStore.displayName }}</span>
          <span class="na-sidebar__user-role">{{ primaryRoleLabel(authStore.user) }}</span>
        </template>
        <template v-else>
          <span class="na-sidebar__user-name">未登录</span>
          <RouterLink class="na-sidebar__user-link" to="/login">登录 / 注册</RouterLink>
        </template>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.na-sidebar {
  flex: 0 0 auto;
  width: var(--na-nav-width, 184px);
  min-height: 100vh;
  padding: var(--na-space-4, 16px) var(--na-space-3, 12px);
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
  background: var(--na-color-surface, #ffffff);
  border-right: 1px solid var(--na-color-border, #e5e7eb);
  position: sticky;
  top: 0;
  align-self: flex-start;
}

.na-sidebar__logo {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
  padding: 6px 8px var(--na-space-3, 12px);
  text-decoration: none;
  color: var(--na-color-text, #111827);
}

.na-sidebar__logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: var(--na-radius-md, 8px);
  background: var(--na-color-primary-500, #3b82f6);
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.na-sidebar__logo-text {
  font-size: var(--na-font-size-md, 15px);
  font-weight: 600;
}

.na-sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.na-sidebar__link {
  display: flex;
  align-items: center;
  gap: var(--na-space-2, 8px);
  padding: 0 10px;
  height: 36px;
  border-radius: var(--na-radius-md, 8px);
  color: var(--na-color-text-muted, #6b7280);
  font-size: var(--na-font-size-sm, 14px);
  text-decoration: none;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.na-sidebar__link:hover {
  background: var(--na-color-primary-50, #eff6ff);
  color: var(--na-color-primary-600, #2563eb);
}

.na-sidebar__link.is-active {
  background: var(--na-color-primary-50, #eff6ff);
  color: var(--na-color-primary-600, #2563eb);
  font-weight: 600;
}

.na-sidebar__link--sub {
  height: 32px;
  padding-left: 22px;
}

.na-sidebar__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.5;
}

.na-sidebar__label {
  flex: 1 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.na-sidebar__badge {
  flex: 0 0 auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--na-color-primary-500, #3b82f6);
  color: #ffffff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.na-sidebar__divider {
  height: 1px;
  margin: var(--na-space-2, 8px) 8px;
  background: var(--na-color-border, #e5e7eb);
}

.na-sidebar__footer {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.na-sidebar__user {
  margin-top: var(--na-space-3, 12px);
  padding: 10px;
  border-radius: var(--na-radius-md, 8px);
  background: var(--na-color-bg, #f8fafc);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.na-sidebar__user-name {
  font-size: var(--na-font-size-sm, 14px);
  font-weight: 600;
  color: var(--na-color-text, #111827);
}

.na-sidebar__user-role {
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-sidebar__user-link {
  font-size: 12px;
  color: var(--na-color-primary-600, #2563eb);
  text-decoration: none;
}

@media (max-width: 900px) {
  .na-sidebar {
    width: 64px;
    padding: var(--na-space-3, 12px) 6px;
  }

  .na-sidebar__label,
  .na-sidebar__logo-text,
  .na-sidebar__badge,
  .na-sidebar__user {
    display: none;
  }

  .na-sidebar__link {
    justify-content: center;
    padding: 0;
  }
}
</style>
