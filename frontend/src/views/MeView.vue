<script setup>
/**
 * 我的健康（个人中心）
 *
 * 职责边界：这里只看"我的健康状态"——个人资料摘要、饮食偏好、收藏与浏览历史入口。
 * 账号资料编辑、退出登录、删除个人数据属于**设置**，见 `MeSettingsView.vue`（`/me/settings`）。
 *
 * 数据来源：
 *   - `GET /users/me`              当前用户（含 `preferences`）
 *   - `GET /users/me/favorites`    收藏（由 `favoritesStore` 持有）
 *
 * 未登录时展示登录引导，不展示任何需要鉴权的数据。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import NoticeBanner from '../components/NoticeBanner.vue'
import EmptyState from '../components/EmptyState.vue'
import { useAuthStore } from '../stores/auth'
import { useFavoritesStore } from '../stores/favorites'
import { useHistoryStore } from '../stores/history'
import { formatDateTime, textOrPlaceholder } from '../utils/format'
import { primaryRoleLabel } from '../utils/roles'

const router = useRouter()
const authStore = useAuthStore()
const favoritesStore = useFavoritesStore()
const historyStore = useHistoryStore()

const loadError = ref('')

/** 是否有可展示的资料 */
const hasProfile = computed(() => Boolean(authStore.user))

/** 角色文案（统一走 utils/roles，与后台、设置页保持同一套叫法） */
const roleText = computed(() => primaryRoleLabel(authStore.user))

/** 饮食目标（用户资料里的偏好；未设置时显示占位，不臆造默认目标） */
const goalText = computed(() => {
  const preferences = (authStore.user && authStore.user.preferences) || null
  return textOrPlaceholder(preferences && preferences.goal ? preferences.goal : '')
})

/** 忌口 / 规避项（数组；为空时显示占位） */
const avoidList = computed(() => {
  const preferences = (authStore.user && authStore.user.preferences) || null
  const avoid = preferences && Array.isArray(preferences.avoid) ? preferences.avoid : []
  return avoid.filter((item) => typeof item === 'string' && item.trim() !== '')
})

/** 加载资料 */
async function loadProfile() {
  if (!authStore.isLoggedIn) return
  loadError.value = ''
  try {
    await authStore.fetchProfile({ force: true })
  } catch (error) {
    loadError.value = '暂时无法获取个人资料，请稍后重试。'
  }
}

onMounted(loadProfile)
</script>

<template>
  <div class="na-me">
    <header class="na-me__head">
      <h2 class="na-me__title">我的健康</h2>
      <p class="na-me__desc">查看健康资料、饮食偏好，以及收藏与浏览历史。</p>
    </header>

    <!-- 未登录 -->
    <EmptyState
      v-if="!authStore.isLoggedIn"
      icon="user"
      title="还没有登录"
      description="登录后可以同步收藏与偏好设置；游客也可以正常查询营养数据。"
      action-text="登录 / 注册"
      @action="router.push('/login')"
    />

    <template v-else>
      <NoticeBanner v-if="loadError" variant="danger" title="加载失败" :message="loadError" />

      <div class="na-me__layout">
        <!-- 左：资料卡片 -->
        <section class="na-card na-me__profile">
          <div class="na-me__avatar">
            <img v-if="authStore.avatarUrl" :src="authStore.avatarUrl" :alt="authStore.displayName" />
            <span v-else class="na-me__avatar-initial">{{ authStore.displayName.slice(0, 1) }}</span>
          </div>
          <p class="na-me__name">{{ authStore.displayName }}</p>
          <p class="na-me__username">账号：{{ textOrPlaceholder(hasProfile ? authStore.user.username : '') }}</p>
          <p class="na-me__role">{{ roleText }}</p>

          <dl class="na-me__meta">
            <div>
              <dt>注册时间</dt>
              <dd>{{ formatDateTime(hasProfile ? authStore.user.createdAt : null) }}</dd>
            </div>
            <div>
              <dt>收藏数量</dt>
              <dd>{{ favoritesStore.count }}</dd>
            </div>
            <div>
              <dt>浏览历史</dt>
              <dd>{{ historyStore.count }} 条</dd>
            </div>
          </dl>

          <div class="na-me__actions">
            <RouterLink class="na-btn na-btn--ghost" to="/me/favorites">我的收藏</RouterLink>
            <RouterLink class="na-btn na-btn--ghost" to="/me/history">浏览历史</RouterLink>
            <RouterLink class="na-btn na-btn--ghost" to="/me/settings">设置</RouterLink>
          </div>
        </section>

        <!-- 右：饮食偏好（只读展示；编辑入口在设置页） -->
        <div class="na-me__right">
          <section class="na-card na-me__prefs">
            <h3 class="na-me__section-title">饮食偏好</h3>
            <p class="na-me__prefs-hint">
              偏好会作为 AI 问答的背景信息使用，不会改变任何营养数据；
              营养数值始终来自数据库，不会因偏好而调整。
            </p>
            <dl class="na-me__prefs-list">
              <div>
                <dt>饮食目标</dt>
                <dd>{{ goalText }}</dd>
              </div>
              <div>
                <dt>忌口 / 规避</dt>
                <dd>
                  <template v-if="avoidList.length">
                    <span v-for="item in avoidList" :key="item" class="na-me__tag">{{ item }}</span>
                  </template>
                  <template v-else>暂无数据</template>
                </dd>
              </div>
            </dl>
            <RouterLink class="na-btn na-btn--ghost" to="/me/settings">前往设置修改</RouterLink>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.na-me {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-me__title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.na-me__desc {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-me__layout {
  display: grid;
  grid-template-columns: minmax(0, 300px) minmax(0, 1fr);
  gap: var(--na-space-4, 16px);
  align-items: start;
}

.na-me__profile {
  padding: var(--na-space-5, 20px);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--na-space-2, 8px);
}

.na-me__avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--na-color-primary-50, #eff6ff);
  color: var(--na-color-primary-600, #2563eb);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.na-me__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.na-me__avatar-initial {
  font-size: 28px;
  font-weight: 600;
}

.na-me__name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.na-me__username,
.na-me__role {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-me__role {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--na-color-surface-muted, #f3f4f6);
}

.na-me__meta {
  width: 100%;
  margin: var(--na-space-3, 12px) 0 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.na-me__meta > div {
  display: flex;
  justify-content: space-between;
}

.na-me__meta dt {
  color: var(--na-color-text-muted, #6b7280);
}

.na-me__meta dd {
  margin: 0;
}

.na-me__actions {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
  width: 100%;
  margin-top: var(--na-space-3, 12px);
}

.na-me__right {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-me__prefs {
  padding: var(--na-space-4, 16px);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--na-space-3, 12px);
}

.na-me__section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.na-me__prefs-hint {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--na-color-text-muted, #6b7280);
}

.na-me__prefs-list {
  width: 100%;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}

.na-me__prefs-list > div {
  display: flex;
  justify-content: space-between;
  gap: var(--na-space-3, 12px);
}

.na-me__prefs-list dt {
  color: var(--na-color-text-muted, #6b7280);
}

.na-me__prefs-list dd {
  margin: 0;
  text-align: right;
}

.na-me__tag {
  display: inline-block;
  margin-left: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--na-color-surface-muted, #f3f4f6);
}

@media (max-width: 860px) {
  .na-me__layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
