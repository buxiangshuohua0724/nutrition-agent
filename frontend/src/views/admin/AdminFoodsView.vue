<script setup>
/**
 * 后台 · 食品管理（原型板块 7）
 *
 * 接口（契约 4.7）：
 *   - `GET    /admin/foods`          分页列表（keyword / categoryId / publishStatus / reviewStatus）
 *   - `POST   /admin/foods`          新增
 *   - `PUT    /admin/foods/{id}`     编辑
 *   - `DELETE /admin/foods/{id}`     删除（**页面必须二次确认**）
 *   - `POST   /admin/foods/{id}/review`   审核
 *   - `POST   /admin/foods/{id}/publish`  发布
 *   - `GET    /categories`           分类下拉数据
 *
 * 注意：这里的"营养成分(部分)"列只展示后端返回的**已填/缺失条数**（计数），
 * 不展示、也不推断任何营养数值。
 */
import { computed, onMounted, reactive, ref } from 'vue'

import ConfirmDialog from '../../components/ConfirmDialog.vue'
import EmptyState from '../../components/EmptyState.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import NoticeBanner from '../../components/NoticeBanner.vue'
import FoodImage from '../../components/FoodImage.vue'
import {
  createFood,
  deleteFood,
  getAdminFoods,
  publishFood,
  reviewFood,
  updateFood,
} from '../../api/admin'
import { getCategories } from '../../api/foods'
import { useToastStore } from '../../stores/toast'
import { formatDateTime, textOrPlaceholder } from '../../utils/format'
import { pageMetaOf } from '../../utils/dev'

const toastStore = useToastStore()

const pageSize = 20

const filters = reactive({ keyword: '', categoryId: '', publishStatus: '', reviewStatus: '' })
const page = ref(1)
const list = ref([])
const pagination = ref({ page: 1, pageSize, total: 0, totalPages: 0 })
const categories = ref([])
const loading = ref(false)
const loadFailed = ref(false)
const isDemoData = ref(false)

/** 删除确认 */
const deleteDialogOpen = ref(false)
const deleting = ref(false)
const pendingDelete = ref(null)

/** 编辑器（新增 / 编辑共用） */
const editorOpen = ref(false)
const editorMode = ref('create')
const saving = ref(false)
const editorError = ref('')
const editorForm = reactive({
  id: null,
  code: '',
  name: '',
  categoryId: '',
  intro: '',
})

/** 审核 / 发布过程中的行 id */
const actingId = ref(null)

/** 状态展示映射（纯展示标签） */
const PUBLISH_LABELS = { PUBLISHED: '已发布', DRAFT: '草稿', ARCHIVED: '已归档' }
const REVIEW_LABELS = { APPROVED: '已通过', PENDING: '待审核', REJECTED: '已驳回' }

/** 总数文案 */
const totalText = computed(() => (loading.value ? '加载中…' : `共 ${pagination.value.total} 条`))

/** 加载分类下拉 */
async function loadCategories() {
  const { data, error } = await getCategories({ silent: true })
  categories.value = error || !Array.isArray(data) ? [] : data
}

/** 加载列表 */
async function loadList() {
  loading.value = true
  loadFailed.value = false
  const { data, isDemoData: demo, error } = await getAdminFoods({
    keyword: filters.keyword.trim(),
    categoryId: filters.categoryId === '' ? null : filters.categoryId,
    publishStatus: filters.publishStatus,
    reviewStatus: filters.reviewStatus,
    page: page.value,
    pageSize,
  })
  loading.value = false
  isDemoData.value = demo
  loadFailed.value = Boolean(error)

  if (error) {
    list.value = []
    pagination.value = { page: page.value, pageSize, total: 0, totalPages: 0 }
    return
  }
  list.value = data && Array.isArray(data.list) ? data.list : []
  // 分页元信息统一走 pageMetaOf（只处理分页计数，营养数值严禁任何兜底）
  pagination.value = pageMetaOf(data, { page: page.value, pageSize })
}

/** 搜索（回到第一页） */
function handleSearch() {
  page.value = 1
  loadList()
}

/** 重置筛选 */
function resetFilters() {
  filters.keyword = ''
  filters.categoryId = ''
  filters.publishStatus = ''
  filters.reviewStatus = ''
  page.value = 1
  loadList()
}

/** 翻页 */
function handlePageChange(next) {
  page.value = next
  loadList()
}

/** 打开新增编辑器 */
function openCreate() {
  editorMode.value = 'create'
  editorForm.id = null
  editorForm.code = ''
  editorForm.name = ''
  editorForm.categoryId = categories.value.length > 0 ? categories.value[0].id : ''
  editorForm.intro = ''
  editorError.value = ''
  editorOpen.value = true
}

/**
 * 打开编辑编辑器。
 * @param {object} row 列表行
 */
function openEdit(row) {
  editorMode.value = 'edit'
  editorForm.id = row.id
  editorForm.code = row.code || ''
  editorForm.name = row.name || ''
  editorForm.categoryId = row.categoryId === undefined || row.categoryId === null ? '' : row.categoryId
  editorForm.intro = row.intro || ''
  editorError.value = ''
  editorOpen.value = true
}

/** 保存（新增或编辑） */
async function saveEditor() {
  if (saving.value) return
  if (!editorForm.name.trim()) {
    editorError.value = '请填写食品名称'
    return
  }
  if (!editorForm.code.trim()) {
    editorError.value = '请填写食品编码'
    return
  }
  if (editorForm.categoryId === '' || editorForm.categoryId === null) {
    editorError.value = '请选择分类'
    return
  }

  saving.value = true
  editorError.value = ''
  const payload = {
    code: editorForm.code.trim(),
    name: editorForm.name.trim(),
    categoryId: Number(editorForm.categoryId),
    intro: editorForm.intro.trim(),
  }
  try {
    if (editorMode.value === 'create') {
      await createFood(payload)
      toastStore.success('食品已创建')
    } else {
      await updateFood(editorForm.id, payload)
      toastStore.success('食品已更新')
    }
    editorOpen.value = false
    await loadList()
  } catch (error) {
    editorError.value = error && error.message ? error.message : '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

/**
 * 请求删除（打开二次确认）。
 * @param {object} row 列表行
 */
function requestDelete(row) {
  pendingDelete.value = row
  deleteDialogOpen.value = true
}

/** 确认删除 */
async function confirmDelete() {
  if (!pendingDelete.value) return
  deleting.value = true
  try {
    await deleteFood(pendingDelete.value.id)
    toastStore.success('食品已删除')
    deleteDialogOpen.value = false
    pendingDelete.value = null
    await loadList()
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    deleting.value = false
  }
}

/**
 * 审核通过 / 驳回。
 * @param {object} row 列表行
 * @param {boolean} approved 是否通过
 */
async function handleReview(row, approved) {
  actingId.value = row.id
  try {
    await reviewFood(row.id, { approved, comment: approved ? '后台审核通过' : '后台审核驳回' })
    toastStore.success(approved ? '已审核通过' : '已驳回')
    await loadList()
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    actingId.value = null
  }
}

/**
 * 发布。
 * @param {object} row 列表行
 */
async function handlePublish(row) {
  actingId.value = row.id
  try {
    await publishFood(row.id)
    toastStore.success('已发布')
    await loadList()
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    actingId.value = null
  }
}

onMounted(async () => {
  await loadCategories()
  await loadList()
})
</script>

<template>
  <div class="na-admin-foods">
    <header class="na-admin-page__head">
      <div>
        <h2 class="na-admin-page__title">食品管理</h2>
        <p class="na-admin-page__desc">{{ totalText }}</p>
      </div>
      <button type="button" class="na-btn na-btn--primary" @click="openCreate">新增食品</button>
    </header>

    <NoticeBanner
      v-if="isDemoData"
      variant="info"
      title="接口不可用"
      message="后台接口当前不可用，列表为空。请确认后端服务已启动（默认 8080 端口）且当前账号具备管理权限。"
    />

    <!-- 筛选栏 -->
    <form class="na-card na-admin-foods__filters" @submit.prevent="handleSearch">
      <input v-model="filters.keyword" class="na-input" type="search" placeholder="食品名称 / 编码" />
      <label class="na-sr-only" for="na-admin-category">分类</label>
      <select id="na-admin-category" v-model="filters.categoryId" class="na-input">
        <option value="">全部分类</option>
        <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
      </select>
      <label class="na-sr-only" for="na-admin-publish">发布状态</label>
      <select id="na-admin-publish" v-model="filters.publishStatus" class="na-input">
        <option value="">全部发布状态</option>
        <option value="PUBLISHED">已发布</option>
        <option value="DRAFT">草稿</option>
        <option value="ARCHIVED">已归档</option>
      </select>
      <label class="na-sr-only" for="na-admin-review">审核状态</label>
      <select id="na-admin-review" v-model="filters.reviewStatus" class="na-input">
        <option value="">全部审核状态</option>
        <option value="APPROVED">已通过</option>
        <option value="PENDING">待审核</option>
        <option value="REJECTED">已驳回</option>
      </select>
      <button type="submit" class="na-btn na-btn--primary">搜索</button>
      <button type="button" class="na-btn na-btn--ghost" @click="resetFilters">重置</button>
    </form>

    <SkeletonBlock v-if="loading" :rows="8" height="20px" />

    <div v-else-if="list.length > 0" class="na-card na-admin-foods__table-wrap">
      <table class="na-table">
        <thead>
          <tr>
            <th scope="col">ID</th>
            <th scope="col">食品名称</th>
            <th scope="col">分类</th>
            <th scope="col">图片</th>
            <th scope="col">营养成分（已填 / 缺失）</th>
            <th scope="col">状态</th>
            <th scope="col">更新时间</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td>{{ row.id }}</td>
            <td>
              <div class="na-admin-foods__name-cell">
                <span class="na-admin-foods__name">{{ textOrPlaceholder(row.name) }}</span>
                <span class="na-admin-foods__code">{{ textOrPlaceholder(row.code) }}</span>
              </div>
            </td>
            <td>{{ textOrPlaceholder(row.categoryName) }}</td>
            <td>
              <FoodImage
                :code="row.code"
                :name="row.name"
                :cover-image-url="row.coverImageUrl"
                width="56px"
                height="42px"
              />
            </td>
            <td>
              <!-- 只展示后端给出的计数，不展示任何营养数值 -->
              <span class="na-admin-foods__count">
                已填 {{ textOrPlaceholder(row.nutrientFilledCount) }}
              </span>
              <span class="na-admin-foods__count na-admin-foods__count--missing">
                缺失 {{ textOrPlaceholder(row.nutrientMissingCount) }}
              </span>
            </td>
            <td>
              <span class="na-badge">{{ PUBLISH_LABELS[row.publishStatus] || textOrPlaceholder(row.publishStatus) }}</span>
              <span class="na-badge">{{ REVIEW_LABELS[row.reviewStatus] || textOrPlaceholder(row.reviewStatus) }}</span>
            </td>
            <td>{{ formatDateTime(row.updatedAt) }}</td>
            <td>
              <div class="na-admin-foods__row-actions">
                <button type="button" class="na-admin-foods__link" @click="openEdit(row)">编辑</button>
                <button
                  v-if="row.reviewStatus !== 'APPROVED'"
                  type="button"
                  class="na-admin-foods__link"
                  :disabled="actingId === row.id"
                  @click="handleReview(row, true)"
                >
                  审核通过
                </button>
                <button
                  v-if="row.reviewStatus !== 'REJECTED'"
                  type="button"
                  class="na-admin-foods__link na-admin-foods__link--danger"
                  :disabled="actingId === row.id"
                  @click="handleReview(row, false)"
                >
                  驳回
                </button>
                <button
                  v-if="row.publishStatus !== 'PUBLISHED'"
                  type="button"
                  class="na-admin-foods__link"
                  :disabled="actingId === row.id"
                  @click="handlePublish(row)"
                >
                  发布
                </button>
                <button type="button" class="na-admin-foods__link na-admin-foods__link--danger" @click="requestDelete(row)">
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <EmptyState
      v-else
      icon="category"
      :title="loadFailed ? '暂时无法获取食品列表' : '没有符合条件的食品'"
      :description="
        loadFailed
          ? '请确认后端服务已启动（默认 8080 端口），且当前账号具备管理权限。'
          : '可以调整筛选条件，或点击右上角「新增食品」录入。'
      "
    />

    <PaginationBar
      v-if="!loading && list.length > 0"
      :page="pagination.page"
      :page-size="pagination.pageSize"
      :total="pagination.total"
      :total-pages="pagination.totalPages"
      @update:page="handlePageChange"
    />

    <!-- 新增 / 编辑弹窗 -->
    <div v-if="editorOpen" class="na-admin-modal" @click.self="editorOpen = false">
      <div class="na-card na-admin-modal__panel" role="dialog" aria-modal="true">
        <h3 class="na-admin-modal__title">{{ editorMode === 'create' ? '新增食品' : '编辑食品' }}</h3>
        <p v-if="editorError" class="na-admin-modal__error" role="alert">{{ editorError }}</p>
        <form class="na-admin-modal__form" @submit.prevent="saveEditor">
          <div class="na-admin-modal__field">
            <label for="na-admin-food-name">食品名称</label>
            <input id="na-admin-food-name" v-model="editorForm.name" class="na-input" type="text" maxlength="64" />
          </div>
          <div class="na-admin-modal__field">
            <label for="na-admin-food-code">食品编码</label>
            <input id="na-admin-food-code" v-model="editorForm.code" class="na-input" type="text" maxlength="64" placeholder="例如 VEG_TOMATO" />
          </div>
          <div class="na-admin-modal__field">
            <label for="na-admin-food-category">分类</label>
            <select id="na-admin-food-category" v-model="editorForm.categoryId" class="na-input">
              <option value="">请选择分类</option>
              <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
            </select>
          </div>
          <div class="na-admin-modal__field">
            <label for="na-admin-food-intro">简介</label>
            <textarea id="na-admin-food-intro" v-model="editorForm.intro" class="na-input" rows="3" maxlength="500"></textarea>
          </div>
          <div class="na-admin-modal__actions">
            <button type="button" class="na-btn na-btn--ghost" @click="editorOpen = false">取消</button>
            <button type="submit" class="na-btn na-btn--primary" :disabled="saving">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
          <p class="na-admin-modal__note">
            营养数值不在本表单中录入。营养数据请到「营养数据管理」按形态逐项维护，
            且每一项都必须填写来源（缺少来源会被后端以 40001 拒绝）。
          </p>
        </form>
      </div>
    </div>

    <ConfirmDialog
      :open="deleteDialogOpen"
      title="删除食品"
      :message="pendingDelete ? `确认删除「${pendingDelete.name}」吗？该操作不可撤销。` : ''"
      confirm-text="确认删除"
      danger
      :loading="deleting"
      @confirm="confirmDelete"
      @cancel="deleteDialogOpen = false"
    />
  </div>
</template>

<style scoped>
.na-admin-foods {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-admin-foods__filters {
  display: flex;
  gap: var(--na-space-2, 8px);
  flex-wrap: wrap;
  padding: var(--na-space-3, 12px);
}

.na-admin-foods__filters .na-input {
  flex: 1;
  min-width: 140px;
}

.na-admin-foods__table-wrap {
  overflow-x: auto;
  padding: var(--na-space-2, 8px);
}

.na-admin-foods__name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.na-admin-foods__name {
  font-weight: 500;
}

.na-admin-foods__code {
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-admin-foods__count {
  display: block;
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-admin-foods__count--missing {
  color: var(--na-color-warning, #f59e0b);
}

.na-admin-foods__row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--na-space-2, 8px);
}

.na-admin-foods__link {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  color: var(--na-color-primary-600, #2563eb);
  cursor: pointer;
}

.na-admin-foods__link:disabled {
  color: var(--na-color-text-muted, #9ca3af);
  cursor: not-allowed;
}

.na-admin-foods__link--danger {
  color: var(--na-color-danger, #ef4444);
}
</style>
