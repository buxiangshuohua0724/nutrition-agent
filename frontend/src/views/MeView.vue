<script setup>
/**
 * 我的健康（个人中心）
 *
 * 数据来源：
 *   - `GET /users/me`            当前用户
 *   - `PUT /users/me/profile`    更新昵称 / 邮箱 / 偏好
 *   - `DELETE /users/me/data`    删除个人数据（FR-007 删除权，二次确认）
 *
 * 未登录时展示登录引导，不展示任何需要鉴权的数据。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import ConfirmDialog from '../components/ConfirmDialog.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import EmptyState from '../components/EmptyState.vue'
import NavIcon from '../components/NavIcon.vue'
import { useAuthStore } from '../stores/auth'
import { useFavoritesStore } from '../stores/favorites'
import { useHistoryStore } from '../stores/history'
import { useToastStore } from '../stores/toast'
import { deleteMyData } from '../api/users'
import { formatDateTime, textOrPlaceholder } from '../utils/format'

const router = useRouter()
const authStore = useAuthStore()
const favoritesStore = useFavoritesStore()
const historyStore = useHistoryStore()
const toastStore = useToastStore()

const form = reactive({ nickname: '', email: '' })
const saving = ref(false)
const deleteDialogOpen = ref(false)
const deleting = ref(false)
const loadError = ref('')

/** 是否有可展示的资料 */
const hasProfile = computed(() => Boolean(authStore.user))

/** 角色文案（区分 CONTENT_ADMIN 与 SYS_ADMIN） */
const roleText = computed(() => {
  if (authStore.isSysAdmin) return '系统管理员'
  if (authStore.isContentAdmin) return '内容管理员'
  if (authStore.isLoggedIn) return '普通用户'
  return '游客'
})

/** 资料写入表单（接口返回后同步一次） */
function syncForm() {
  form.nickname = authStore.user && authStore.user.nickname ? authStore.user.nickname : ''
  form.email = authStore.user && authStore.user.email ? authStore.user.email : ''
}

/** 加载资料 */
async function loadProfile() {
  if (!authStore.isLoggedIn) return
  loadError.value = ''
  try {
    await authStore.fetchProfile({ force: true })
    syncForm()
  } catch (error) {
    loadError.value = '暂时无法获取个人资料，请稍后重试。'
  }
}

/** 保存资料 */
async function saveProfile() {
  if (saving.value) return
  saving.value = true
  try {
    // 走 store 的 updateProfile，保存成功后会同步本地缓存的用户信息
    await authStore.updateProfile({ nickname: form.nickname, email: form.email })
    syncForm()
    toastStore.success('资料已保存')
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    saving.value = false
  }
}

/** 退出登录 */
async function handleLogout() {
  await authStore.logout()
  toastStore.success('已退出登录')
  router.push('/')
}

/** 真正执行"删除个人数据" */
async function confirmDeleteData() {
  deleting.value = true
  try {
    await deleteMyData()
    favoritesStore.clear()
    historyStore.clear()
    authStore.clearAuth()
    toastStore.success('个人数据已删除')
    router.push('/')
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    deleting.value = false
    deleteDialogOpen.value = false
  }
}

onMounted(async () => {
  await loadProfile()
  syncForm()
})
</script>

<template>
  <div class="na-me">
    <header class="na-me__head">
      <h2 class="na-me__title">我的健康</h2>
      <p class="na-me__desc">管理个人资料、收藏与浏览历史。</p>
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
            <button type="button" class="na-btn na-btn--ghost" @click="handleLogout">退出登录</button>
          </div>
        </section>

        <!-- 右：可编辑资料 + 危险操作 -->
        <div class="na-me__right">
          <section class="na-card na-me__form-card">
            <h3 class="na-me__section-title">编辑资料</h3>
            <form class="na-me__form" @submit.prevent="saveProfile">
              <div class="na-me__field">
                <label for="na-me-nickname">昵称</label>
                <input id="na-me-nickname" v-model="form.nickname" class="na-input" type="text" maxlength="32" placeholder="请输入昵称" />
              </div>
              <div class="na-me__field">
                <label for="na-me-email">邮箱</label>
                <input id="na-me-email" v-model="form.email" class="na-input" type="email" maxlength="64" placeholder="请输入邮箱（可选）" />
              </div>
              <button type="submit" class="na-btn na-btn--primary" :disabled="saving">
                {{ saving ? '保存中…' : '保存资料' }}
              </button>
            </form>
          </section>

          <section class="na-card na-me__danger">
            <h3 class="na-me__section-title">
              <NavIcon name="warning" :size="18" />
              危险操作
            </h3>
            <p class="na-me__danger-desc">
              删除个人数据会清除账号下的收藏、偏好与问答会话记录，该操作不可撤销。
            </p>
            <button type="button" class="na-btn na-me__danger-btn" @click="deleteDialogOpen = true">
              删除我的个人数据
            </button>
          </section>
        </div>
      </div>
    </template>

    <ConfirmDialog
      :open="deleteDialogOpen"
      title="删除个人数据"
      message="确认删除账号下的全部个人数据吗？包括收藏、偏好与会话记录，此操作不可撤销。"
      confirm-text="确认删除"
      danger
      :loading="deleting"
      @confirm="confirmDeleteData"
      @cancel="deleteDialogOpen = false"
    />
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

.na-me__form-card,
.na-me__danger {
  padding: var(--na-space-4, 16px);
}

.na-me__section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 var(--na-space-3, 12px);
  font-size: 15px;
  font-weight: 600;
}

.na-me__form {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-3, 12px);
  max-width: 420px;
}

.na-me__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.na-me__field label {
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-me__danger-desc {
  margin: 0 0 var(--na-space-3, 12px);
  font-size: 13px;
  line-height: 1.7;
  color: var(--na-color-text-muted, #6b7280);
}

.na-me__danger-btn {
  background: var(--na-color-danger, #ef4444);
  color: #ffffff;
}

@media (max-width: 860px) {
  .na-me__layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
