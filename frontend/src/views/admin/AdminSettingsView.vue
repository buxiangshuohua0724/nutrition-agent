<script setup>
/**
 * 后台 · 系统设置（仅 `SYS_ADMIN` 可见，菜单侧已按角色过滤）
 *
 * 接口（契约 4.7）：
 *   - `GET /admin/users`        用户列表
 *   - `GET /admin/audit-logs`   审计日志
 *
 * 安全说明：
 *   - 前端**不展示也不请求任何密钥**（API Key、数据库口令等一律不入前端）；
 *   - 页面上给出的配置项只是"需要运维在服务端配置"的清单说明，不读写服务端配置；
 *   - 真正的权限判定在后端：即便有人手工打开本页，接口仍会返回 40301。
 */
import { onMounted, reactive, ref } from 'vue'

import EmptyState from '../../components/EmptyState.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import NoticeBanner from '../../components/NoticeBanner.vue'
import { getAdminUsers, getAuditLogs } from '../../api/admin'
import { useAuthStore } from '../../stores/auth'
import { formatDateTime, textOrPlaceholder } from '../../utils/format'
import { roleLabels } from '../../utils/roles'
import { pageMetaOf } from '../../utils/dev'

const authStore = useAuthStore()

/** 当前分区：users | audit | about */
const activeSection = ref('users')

const SECTIONS = [
  { value: 'users', label: '用户管理' },
  { value: 'audit', label: '审计日志' },
  { value: 'about', label: '运行说明' },
]

/** 用户列表 */
const userFilters = reactive({ keyword: '', role: '' })
const users = ref([])
const usersPage = ref(1)
const usersPagination = ref({ page: 1, pageSize: 20, total: 0, totalPages: 0 })
const usersLoading = ref(false)
const usersFailed = ref(false)

/** 审计日志 */
const auditFilters = reactive({ action: '', operatorId: '' })
const logs = ref([])
const logsPage = ref(1)
const logsPagination = ref({ page: 1, pageSize: 20, total: 0, totalPages: 0 })
const logsLoading = ref(false)
const logsFailed = ref(false)

/** 加载用户列表 */
async function loadUsers() {
  usersLoading.value = true
  usersFailed.value = false
  const { data, error } = await getAdminUsers({
    keyword: userFilters.keyword.trim(),
    role: userFilters.role,
    page: usersPage.value,
    pageSize: 20,
  })
  usersLoading.value = false
  usersFailed.value = Boolean(error)

  if (error) {
    users.value = []
    return
  }
  users.value = data && Array.isArray(data.list) ? data.list : []
  usersPagination.value = pageMetaOf(data, { page: usersPage.value, pageSize: 20 })
}

/** 搜索用户 */
function searchUsers() {
  usersPage.value = 1
  loadUsers()
}

/**
 * 用户分页。
 * @param {number} next 目标页
 */
function changeUsersPage(next) {
  usersPage.value = next
  loadUsers()
}

/** 加载审计日志 */
async function loadLogs() {
  logsLoading.value = true
  logsFailed.value = false
  const { data, error } = await getAuditLogs({
    action: auditFilters.action.trim(),
    operatorId: auditFilters.operatorId === '' ? null : auditFilters.operatorId,
    page: logsPage.value,
    pageSize: 20,
  })
  logsLoading.value = false
  logsFailed.value = Boolean(error)

  if (error) {
    logs.value = []
    return
  }
  logs.value = data && Array.isArray(data.list) ? data.list : []
  logsPagination.value = pageMetaOf(data, { page: logsPage.value, pageSize: 20 })
}

/** 搜索日志 */
function searchLogs() {
  logsPage.value = 1
  loadLogs()
}

/**
 * 日志分页。
 * @param {number} next 目标页
 */
function changeLogsPage(next) {
  logsPage.value = next
  loadLogs()
}

/**
 * 切换分区。
 * @param {string} value 分区值
 */
function switchSection(value) {
  activeSection.value = value
  if (value === 'users' && users.value.length === 0 && !usersLoading.value) loadUsers()
  if (value === 'audit' && logs.value.length === 0 && !logsLoading.value) loadLogs()
}

/**
 * 角色数组转展示文本。
 * @param {object} row 用户行
 * @returns {string}
 */
function roleText(row) {
  const list = row && Array.isArray(row.roles) ? row.roles : []
  if (list.length === 0) return '—'
  return roleLabels(list)
}

onMounted(loadUsers)
</script>

<template>
  <div class="na-admin-settings">
    <header class="na-admin-page__head">
      <div>
        <h2 class="na-admin-page__title">系统设置</h2>
        <p class="na-admin-page__desc">
          当前账号：{{ authStore.displayName }}（{{ authStore.isSysAdmin ? '系统管理员' : '非系统管理员' }}）
        </p>
      </div>
    </header>

    <NoticeBanner
      v-if="!authStore.isSysAdmin"
      variant="warning"
      title="权限不足"
      message="系统设置仅对系统管理员（SYS_ADMIN）开放。接口仍会做最终校验，若你无权访问将返回 40301。"
    />

    <div class="na-admin-settings__tabs na-tabs" role="tablist">
      <button
        v-for="section in SECTIONS"
        :key="section.value"
        type="button"
        role="tab"
        class="na-tab"
        :class="{ 'is-active': activeSection === section.value }"
        :aria-selected="activeSection === section.value"
        @click="switchSection(section.value)"
      >
        {{ section.label }}
      </button>
    </div>

    <!-- 用户管理 -->
    <template v-if="activeSection === 'users'">
      <form class="na-card na-admin-settings__filters" @submit.prevent="searchUsers">
        <input v-model="userFilters.keyword" class="na-input" type="search" placeholder="用户名 / 昵称 / 邮箱" />
        <label class="na-sr-only" for="na-admin-user-role">角色</label>
        <select id="na-admin-user-role" v-model="userFilters.role" class="na-input">
          <option value="">全部角色</option>
          <option value="USER">普通用户</option>
          <option value="CONTENT_ADMIN">内容管理员</option>
          <option value="SYS_ADMIN">系统管理员</option>
        </select>
        <button type="submit" class="na-btn na-btn--primary">搜索</button>
      </form>

      <SkeletonBlock v-if="usersLoading" :rows="6" height="20px" />

      <div v-else-if="users.length > 0" class="na-card na-admin-settings__table-wrap">
        <table class="na-table">
          <thead>
            <tr>
              <th scope="col">ID</th>
              <th scope="col">用户名</th>
              <th scope="col">昵称</th>
              <th scope="col">邮箱</th>
              <th scope="col">角色</th>
              <th scope="col">注册时间</th>
              <th scope="col">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in users" :key="row.id">
              <td>{{ textOrPlaceholder(row.id) }}</td>
              <td>{{ textOrPlaceholder(row.username) }}</td>
              <td>{{ textOrPlaceholder(row.nickname) }}</td>
              <td>{{ textOrPlaceholder(row.email) }}</td>
              <td>{{ roleText(row) }}</td>
              <td>{{ formatDateTime(row.createdAt) }}</td>
              <td>{{ textOrPlaceholder(row.status) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <EmptyState
        v-else
        icon="user"
        :title="usersFailed ? '暂时无法获取用户列表' : '没有匹配的用户'"
        :description="
          usersFailed
            ? '请确认后端服务已启动（默认 8080 端口），且当前账号具备系统管理员权限。'
            : '可以调整搜索条件后重试。'
        "
      />

      <PaginationBar
        v-if="!usersLoading && users.length > 0"
        :page="usersPagination.page"
        :page-size="usersPagination.pageSize"
        :total="usersPagination.total"
        :total-pages="usersPagination.totalPages"
        @update:page="changeUsersPage"
      />
    </template>

    <!-- 审计日志 -->
    <template v-else-if="activeSection === 'audit'">
      <form class="na-card na-admin-settings__filters" @submit.prevent="searchLogs">
        <input v-model="auditFilters.action" class="na-input" type="search" placeholder="操作类型，例如 FOOD_PUBLISH" />
        <input v-model="auditFilters.operatorId" class="na-input" type="number" placeholder="操作人 ID" />
        <button type="submit" class="na-btn na-btn--primary">搜索</button>
      </form>

      <SkeletonBlock v-if="logsLoading" :rows="6" height="20px" />

      <div v-else-if="logs.length > 0" class="na-card na-admin-settings__table-wrap">
        <table class="na-table">
          <thead>
            <tr>
              <th scope="col">时间</th>
              <th scope="col">操作人</th>
              <th scope="col">操作类型</th>
              <th scope="col">目标</th>
              <th scope="col">结果</th>
              <th scope="col">traceId</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in logs" :key="textOrPlaceholder(row.id)">
              <td>{{ formatDateTime(row.createdAt || row.time) }}</td>
              <td>
                {{ textOrPlaceholder(row.operatorName || row.operatorId) }}
              </td>
              <td>{{ textOrPlaceholder(row.action) }}</td>
              <td>{{ textOrPlaceholder(row.targetType) }} {{ textOrPlaceholder(row.targetId) }}</td>
              <td>{{ textOrPlaceholder(row.result || row.status) }}</td>
              <td class="na-admin-settings__trace">{{ textOrPlaceholder(row.traceId) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <EmptyState
        v-else
        icon="clock"
        :title="logsFailed ? '暂时无法获取审计日志' : '没有匹配的审计日志'"
        :description="
          logsFailed
            ? '请确认后端服务已启动（默认 8080 端口），且当前账号具备系统管理员权限。'
            : '可以调整筛选条件后重试。'
        "
      />

      <PaginationBar
        v-if="!logsLoading && logs.length > 0"
        :page="logsPagination.page"
        :page-size="logsPagination.pageSize"
        :total="logsPagination.total"
        :total-pages="logsPagination.totalPages"
        @update:page="changeLogsPage"
      />
    </template>

    <!-- 运行说明 -->
    <section v-else class="na-card na-admin-settings__about">
      <h3 class="na-admin-settings__about-title">运行说明</h3>
      <p class="na-admin-settings__about-text">
        以下内容需要在<strong>服务端</strong>配置，前端不读取、不展示任何密钥。
      </p>
      <ul class="na-admin-settings__list">
        <li>AI 模型：需在服务端配置模型服务地址与密钥；未配置时后端返回 50305 / 50302，前端会提示「AI 能力不可用」。</li>
        <li>视觉识别：需在服务端配置视觉服务；不可用时后端返回 50304，前端会引导用户手动搜索食品。</li>
        <li>限流阈值由后端控制：问答（登录 30 次/分、游客 5 次/天）、图片识别（登录 20 次/小时、游客 3 次/天）、其他读接口 120 次/分。</li>
        <li>图片校验：仅接受 JPEG / PNG / WebP，单张不超过 10MB；文件内容校验必须由后端完成。</li>
        <li>营养数据来源：每条营养数据都必须带来源标识，否则后端以 40001 拒绝写入。</li>
      </ul>
      <p class="na-admin-settings__about-text">
        前端环境变量只有一项：<code>VITE_API_BASE</code>（默认 <code>/api/v1</code>），
        开发环境通过 Vite 代理把 <code>/api</code> 转发到 <code>http://localhost:8080</code>。
      </p>
    </section>
  </div>
</template>

<style scoped>
.na-admin-settings {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-admin-settings__filters {
  display: flex;
  gap: var(--na-space-2, 8px);
  flex-wrap: wrap;
  padding: var(--na-space-3, 12px);
}

.na-admin-settings__filters .na-input {
  flex: 1;
  min-width: 160px;
}

.na-admin-settings__table-wrap {
  overflow-x: auto;
  padding: var(--na-space-2, 8px);
}

.na-admin-settings__trace {
  font-family: var(--na-font-mono, monospace);
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-admin-settings__about {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-2, 8px);
}

.na-admin-settings__about-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.na-admin-settings__about-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--na-color-text-secondary, #4b5563);
}

.na-admin-settings__list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--na-color-text-secondary, #4b5563);
}

.na-admin-settings__about code {
  padding: 1px 5px;
  border-radius: var(--na-radius-xs, 4px);
  background: var(--na-color-neutral-100, #f3f4f6);
  font-family: var(--na-font-mono, monospace);
  font-size: 12px;
}
</style>
