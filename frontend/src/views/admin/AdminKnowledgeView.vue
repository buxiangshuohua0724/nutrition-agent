<script setup>
/**
 * 后台 · 知识库管理
 *
 * 接口（契约 4.7）：
 *   - `GET  /admin/knowledge`          分页列表
 *   - `POST /admin/knowledge`          新增文档
 *   - `PUT  /admin/knowledge/{id}`     编辑文档
 *   - `POST /admin/knowledge/reindex`  触发重建索引
 *
 * 知识库是 AI 回答「参考来源」的数据基础，因此本页只做录入/编辑/重建索引，
 * 不在前端生成任何知识内容。
 */
import { onMounted, reactive, ref } from 'vue'

import EmptyState from '../../components/EmptyState.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import NoticeBanner from '../../components/NoticeBanner.vue'
import {
  createKnowledge,
  getAdminKnowledge,
  reindexKnowledge,
  updateKnowledge,
} from '../../api/admin'
import { useToastStore } from '../../stores/toast'
import { formatDateTime, textOrPlaceholder } from '../../utils/format'
import { pageMetaOf } from '../../utils/dev'

const toastStore = useToastStore()

const pageSize = 20

const filters = reactive({ keyword: '', category: '' })
const page = ref(1)
const list = ref([])
const pagination = ref({ page: 1, pageSize, total: 0, totalPages: 0 })
const loading = ref(false)
const loadFailed = ref(false)
const reindexing = ref(false)

/** 编辑器 */
const editorOpen = ref(false)
const editorMode = ref('create')
const saving = ref(false)
const editorError = ref('')
const editorForm = reactive({
  id: null,
  title: '',
  heading: '',
  category: '',
  content: '',
})

/** 知识分类选项（与契约 category 取值一致） */
const CATEGORY_OPTIONS = [
  { value: '', label: '全部分类' },
  { value: 'NUTRIENT', label: '营养素' },
  { value: 'FOOD', label: '食品' },
  { value: 'GUIDELINE', label: '膳食指南' },
  { value: 'HEALTH', label: '健康与疾病' },
  { value: 'OTHER', label: '其他' },
]

/** 加载列表 */
async function loadList() {
  loading.value = true
  loadFailed.value = false
  const { data, error } = await getAdminKnowledge({
    keyword: filters.keyword.trim(),
    category: filters.category,
    page: page.value,
    pageSize,
  })
  loading.value = false
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

/** 搜索 */
function handleSearch() {
  page.value = 1
  loadList()
}

/** 重置 */
function resetFilters() {
  filters.keyword = ''
  filters.category = ''
  page.value = 1
  loadList()
}

/**
 * 翻页。
 * @param {number} next 目标页
 */
function handlePageChange(next) {
  page.value = next
  loadList()
}

/** 打开新增 */
function openCreate() {
  editorMode.value = 'create'
  editorForm.id = null
  editorForm.title = ''
  editorForm.heading = ''
  editorForm.category = 'NUTRIENT'
  editorForm.content = ''
  editorError.value = ''
  editorOpen.value = true
}

/**
 * 打开编辑。
 * @param {object} row 列表行
 */
function openEdit(row) {
  editorMode.value = 'edit'
  editorForm.id = row.docId !== undefined ? row.docId : row.id
  editorForm.title = row.title || ''
  editorForm.heading = row.heading || ''
  editorForm.category = row.category || 'NUTRIENT'
  editorForm.content = row.content || row.snippet || ''
  editorError.value = ''
  editorOpen.value = true
}

/** 保存 */
async function saveEditor() {
  if (saving.value) return
  if (!editorForm.title.trim()) {
    editorError.value = '请填写文档标题'
    return
  }
  if (!editorForm.content.trim()) {
    editorError.value = '请填写文档内容'
    return
  }

  saving.value = true
  editorError.value = ''
  const payload = {
    title: editorForm.title.trim(),
    heading: editorForm.heading.trim(),
    category: editorForm.category,
    content: editorForm.content.trim(),
  }
  try {
    if (editorMode.value === 'create') {
      await createKnowledge(payload)
      toastStore.success('知识文档已创建')
    } else {
      await updateKnowledge(editorForm.id, payload)
      toastStore.success('知识文档已更新')
    }
    editorOpen.value = false
    await loadList()
  } catch (error) {
    editorError.value = error && error.message ? error.message : '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

/** 重建索引 */
async function handleReindex() {
  if (reindexing.value) return
  reindexing.value = true
  try {
    await reindexKnowledge()
    toastStore.success('已触发索引重建，请稍后刷新查看')
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    reindexing.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <div class="na-admin-knowledge">
    <header class="na-admin-page__head">
      <div>
        <h2 class="na-admin-page__title">知识库管理</h2>
        <p class="na-admin-page__desc">
          {{ loading ? '加载中…' : `共 ${pagination.total} 条知识片段` }}
        </p>
      </div>
      <div class="na-admin-knowledge__head-actions">
        <button type="button" class="na-btn na-btn--ghost" :disabled="reindexing" @click="handleReindex">
          {{ reindexing ? '重建中…' : '重建索引' }}
        </button>
        <button type="button" class="na-btn na-btn--primary" @click="openCreate">新增文档</button>
      </div>
    </header>

    <NoticeBanner
      v-if="loadFailed"
      variant="warning"
      title="接口不可用"
      message="无法获取知识库列表，请确认后端服务已启动（默认 8080 端口）且当前账号具备管理权限。"
    />

    <form class="na-card na-admin-knowledge__filters" @submit.prevent="handleSearch">
      <input v-model="filters.keyword" class="na-input" type="search" placeholder="标题 / 内容关键词" />
      <label class="na-sr-only" for="na-admin-knowledge-category">知识分类</label>
      <select id="na-admin-knowledge-category" v-model="filters.category" class="na-input">
        <option v-for="option in CATEGORY_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
      <button type="submit" class="na-btn na-btn--primary">搜索</button>
      <button type="button" class="na-btn na-btn--ghost" @click="resetFilters">重置</button>
    </form>

    <SkeletonBlock v-if="loading" :rows="6" height="20px" />

    <div v-else-if="list.length > 0" class="na-card na-admin-knowledge__table-wrap">
      <table class="na-table">
        <thead>
          <tr>
            <th scope="col">文档 ID</th>
            <th scope="col">标题</th>
            <th scope="col">章节</th>
            <th scope="col">分类</th>
            <th scope="col">内容摘要</th>
            <th scope="col">来源</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="`${row.docId}-${row.chunkId}`">
            <td>{{ textOrPlaceholder(row.docId) }}</td>
            <td>{{ textOrPlaceholder(row.title) }}</td>
            <td>{{ textOrPlaceholder(row.heading) }}</td>
            <td>{{ textOrPlaceholder(row.category) }}</td>
            <td class="na-admin-knowledge__snippet">{{ textOrPlaceholder(row.snippet) }}</td>
            <td>
              <span v-if="row.source && (row.source.name || row.source.publisher)">
                {{ textOrPlaceholder(row.source.name || row.source.publisher) }}
              </span>
              <span v-else>—</span>
            </td>
            <td>
              <button type="button" class="na-admin-knowledge__link" @click="openEdit(row)">编辑</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <EmptyState
      v-else
      icon="leaf"
      :title="loadFailed ? '暂时无法获取知识库' : '知识库暂无内容'"
      :description="
        loadFailed
          ? '请确认后端服务已启动（默认 8080 端口），且当前账号具备管理权限。'
          : '新增文档后记得触发一次「重建索引」，AI 回答才能检索到新内容。'
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

    <!-- 编辑弹窗 -->
    <div v-if="editorOpen" class="na-admin-modal" @click.self="editorOpen = false">
      <div class="na-card na-admin-modal__panel" role="dialog" aria-modal="true">
        <h3 class="na-admin-modal__title">{{ editorMode === 'create' ? '新增知识文档' : '编辑知识文档' }}</h3>
        <p v-if="editorError" class="na-admin-modal__error" role="alert">{{ editorError }}</p>
        <form class="na-admin-modal__form" @submit.prevent="saveEditor">
          <div class="na-admin-modal__field">
            <label for="na-admin-knowledge-title">标题</label>
            <input id="na-admin-knowledge-title" v-model="editorForm.title" class="na-input" type="text" maxlength="200" />
          </div>
          <div class="na-admin-modal__field">
            <label for="na-admin-knowledge-heading">章节</label>
            <input id="na-admin-knowledge-heading" v-model="editorForm.heading" class="na-input" type="text" maxlength="200" />
          </div>
          <div class="na-admin-modal__field">
            <label for="na-admin-knowledge-cat">分类</label>
            <select id="na-admin-knowledge-cat" v-model="editorForm.category" class="na-input">
              <option v-for="option in CATEGORY_OPTIONS.filter((item) => item.value !== '')" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>
          <div class="na-admin-modal__field">
            <label for="na-admin-knowledge-content">内容</label>
            <textarea id="na-admin-knowledge-content" v-model="editorForm.content" class="na-input" rows="8"></textarea>
          </div>
          <div class="na-admin-modal__actions">
            <button type="button" class="na-btn na-btn--ghost" @click="editorOpen = false">取消</button>
            <button type="submit" class="na-btn na-btn--primary" :disabled="saving">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
          <p class="na-admin-modal__note">
            知识库内容会被检索并作为 AI 回答的「参考来源」展示，请确保内容准确、来源可追溯。
          </p>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.na-admin-knowledge {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-admin-knowledge__head-actions {
  display: flex;
  gap: var(--na-space-2, 8px);
}

.na-admin-knowledge__filters {
  display: flex;
  gap: var(--na-space-2, 8px);
  flex-wrap: wrap;
  padding: var(--na-space-3, 12px);
}

.na-admin-knowledge__filters .na-input {
  flex: 1;
  min-width: 160px;
}

.na-admin-knowledge__table-wrap {
  overflow-x: auto;
  padding: var(--na-space-2, 8px);
}

.na-admin-knowledge__snippet {
  max-width: 340px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-admin-knowledge__link {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  color: var(--na-color-primary-600, #2563eb);
  cursor: pointer;
}
</style>
