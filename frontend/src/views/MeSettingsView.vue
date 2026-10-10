<script setup>
/**
 * 设置（账号与个人数据）
 *
 * 与「我的健康」分开：我的健康聚焦收藏与浏览历史，设置聚焦账号本身。
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
import { primaryRoleLabel } from '../utils/roles'

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

/** 角色文案（统一走 utils/roles，区分 CONTENT_ADMIN 与 SYS_ADMIN） */
const roleText = computed(() => primaryRoleLabel(authStore.user))

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
  <div class="na-settings">
    <header class="na-settings__head">
      <h2 class="na-settings__title">设置</h2>
      <p class="na-settings__desc">管理账号资料、登录状态与个人数据。</p>
    </header>

    <EmptyState
      v-if="!authStore.isLoggedIn"
      icon="user"
      title="还没有登录"
      description="登录后可以修改资料、同步收藏与偏好；游客也可以正常查询营养数据。"
      action-text="登录 / 注册"
      @action="router.push('/login')"
    />

    <template v-else>
      <NoticeBanner v-if="loadError" variant="danger" title="加载失败" :message="loadError" />

      <section class="na-card na-settings__card">
        <h3 class="na-settings__section-title">账号信息</h3>
        <dl class="na-settings__meta">
          <div>
            <dt>账号</dt>
            <dd>{{ textOrPlaceholder(hasProfile ? authStore.user.username : '') }}</dd>
          </div>
          <div>
            <dt>角色</dt>
            <dd>{{ roleText }}</dd>
          </div>
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
      </section>

      <section class="na-card na-settings__card">
        <h3 class="na-settings__section-title">编辑资料</h3>
        <form class="na-settings__form" @submit.prevent="saveProfile">
          <div class="na-settings__field">
            <label for="na-settings-nickname">昵称</label>
            <input
              id="na-settings-nickname"
              v-model="form.nickname"
              class="na-input"
              type="text"
              maxlength="32"
              placeholder="请输入昵称"
            />
          </div>
          <div class="na-settings__field">
            <label for="na-settings-email">邮箱</label>
            <input
              id="na-settings-email"
              v-model="form.email"
              class="na-input"
              type="email"
              maxlength="64"
              placeholder="请输入邮箱（可选）"
            />
          </div>
          <button type="submit" class="na-btn na-btn--primary" :disabled="saving">
            {{ saving ? '保存中…' : '保存资料' }}
          </button>
        </form>
      </section>

      <section class="na-card na-settings__card">
        <h3 class="na-settings__section-title">登录状态</h3>
        <p class="na-settings__hint">
          退出登录后本机将不再保留登录凭证；收藏与浏览历史仍保存在本机浏览器中。
        </p>
        <button type="button" class="na-btn na-btn--ghost" @click="handleLogout">退出登录</button>
      </section>

      <section class="na-card na-settings__danger">
        <h3 class="na-settings__section-title">
          <NavIcon name="warning" :size="18" />
          危险操作
        </h3>
        <p class="na-settings__danger-desc">
          删除个人数据会清除账号下的收藏、偏好与问答会话记录，该操作不可撤销。
        </p>
        <button type="button" class="na-btn na-settings__danger-btn" @click="deleteDialogOpen = true">
          删除我的个人数据
        </button>
      </section>
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
.na-settings {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
  max-width: 720px;
}

.na-settings__title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.na-settings__desc {
  margin: 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-settings__card,
.na-settings__danger {
  padding: var(--na-space-4, 16px);
}

.na-settings__section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 var(--na-space-3, 12px);
  font-size: 15px;
  font-weight: 600;
}

.na-settings__meta {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}

.na-settings__meta > div {
  display: flex;
  justify-content: space-between;
  gap: var(--na-space-3, 12px);
}

.na-settings__meta dt {
  color: var(--na-color-text-muted, #6b7280);
}

.na-settings__meta dd {
  margin: 0;
  text-align: right;
}

.na-settings__form {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-3, 12px);
  max-width: 420px;
}

.na-settings__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.na-settings__field label {
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-settings__hint,
.na-settings__danger-desc {
  margin: 0 0 var(--na-space-3, 12px);
  font-size: 13px;
  line-height: 1.7;
  color: var(--na-color-text-muted, #6b7280);
}

.na-settings__danger-btn {
  background: var(--na-color-danger, #ef4444);
  color: #ffffff;
}
</style>
