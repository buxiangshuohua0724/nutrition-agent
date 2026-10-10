<script setup>
/**
 * 后台 · 图片管理
 *
 * 接口（契约 4.7）：
 *   - `POST   /admin/foods/{id}/images`       上传（multipart 字段名 `file`）
 *   - `DELETE /admin/images/{imageId}`        删除图片
 *   - `GET    /admin/foods`                   选择目标食品
 *
 * 上传前只做前端预校验（格式 / 大小），后端仍会做完整校验（含文件内容校验）。
 */
import { computed, onMounted, reactive, ref } from 'vue'

import UploadDropzone from '../../components/UploadDropzone.vue'
import EmptyState from '../../components/EmptyState.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'
import NoticeBanner from '../../components/NoticeBanner.vue'
import FoodImage from '../../components/FoodImage.vue'
import { deleteImage, getAdminFoods, uploadFoodImage } from '../../api/admin'
import { useToastStore } from '../../stores/toast'
import { formatDateTime, textOrPlaceholder } from '../../utils/format'

const toastStore = useToastStore()

const foods = ref([])
const loadingFoods = ref(false)
const loadFailed = ref(false)

const filters = reactive({ keyword: '' })
const selectedFoodId = ref('')
const file = ref(null)
const uploading = ref(false)

/** 当前选中食品的图片列表（来自列表接口返回，若接口未返回则为空） */
const selectedFood = computed(() => foods.value.find((food) => String(food.id) === String(selectedFoodId.value)) || null)

/** 已上传图片（接口返回 images 时展示；未返回时给出说明，不伪造） */
const images = computed(() => {
  if (!selectedFood.value || !Array.isArray(selectedFood.value.images)) return []
  return selectedFood.value.images
})

const deleteDialogOpen = ref(false)
const deleting = ref(false)
const pendingDelete = ref(null)

/** 加载食品列表（用于选择目标食品） */
async function loadFoods() {
  loadingFoods.value = true
  loadFailed.value = false
  const { data, error } = await getAdminFoods({ keyword: filters.keyword.trim(), page: 1, pageSize: 50 })
  loadingFoods.value = false
  loadFailed.value = Boolean(error)
  foods.value = error || !data || !Array.isArray(data.list) ? [] : data.list
  if (foods.value.length > 0 && !selectedFoodId.value) {
    selectedFoodId.value = String(foods.value[0].id)
  }
}

/** 上传 */
async function handleUpload() {
  if (!file.value || !selectedFoodId.value || uploading.value) return
  uploading.value = true
  try {
    await uploadFoodImage(selectedFoodId.value, file.value)
    toastStore.success('图片已上传')
    file.value = null
    await loadFoods()
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    uploading.value = false
  }
}

/**
 * 请求删除图片。
 * @param {object} image 图片对象
 */
function requestDelete(image) {
  pendingDelete.value = image
  deleteDialogOpen.value = true
}

/** 确认删除图片 */
async function confirmDelete() {
  if (!pendingDelete.value) return
  deleting.value = true
  try {
    await deleteImage(pendingDelete.value.id)
    toastStore.success('图片已删除')
    deleteDialogOpen.value = false
    pendingDelete.value = null
    await loadFoods()
  } catch (error) {
    // 错误提示已由 api 层统一处理
  } finally {
    deleting.value = false
  }
}

/** 校验失败提示 */
function handleInvalid(payload) {
  toastStore.error(payload && payload.message ? payload.message : '图片不符合要求')
}

onMounted(loadFoods)
</script>

<template>
  <div class="na-admin-images">
    <header class="na-admin-page__head">
      <div>
        <h2 class="na-admin-page__title">图片管理</h2>
        <p class="na-admin-page__desc">为食品上传或删除展示图片，支持 JPG / PNG / WebP，单张不超过 10MB。</p>
      </div>
    </header>

    <NoticeBanner
      v-if="loadFailed"
      variant="warning"
      title="接口不可用"
      message="无法获取食品列表，请确认后端服务已启动（默认 8080 端口）且当前账号具备管理权限。"
    />

    <div class="na-admin-images__layout">
      <!-- 左：上传区 -->
      <section class="na-card na-admin-images__upload">
        <h3 class="na-admin-images__section-title">上传图片</h3>

        <form class="na-admin-images__form" @submit.prevent="handleUpload">
          <div class="na-admin-images__field">
            <label for="na-admin-image-keyword">搜索食品</label>
            <div class="na-admin-images__search-row">
              <input id="na-admin-image-keyword" v-model="filters.keyword" class="na-input" type="search" placeholder="食品名称 / 编码" />
              <button type="button" class="na-btn na-btn--ghost" @click="loadFoods">搜索</button>
            </div>
          </div>

          <div class="na-admin-images__field">
            <label for="na-admin-image-food">选择食品</label>
            <select id="na-admin-image-food" v-model="selectedFoodId" class="na-input">
              <option value="">请选择食品</option>
              <option v-for="food in foods" :key="food.id" :value="String(food.id)">
                {{ food.name }}（{{ textOrPlaceholder(food.code) }}）
              </option>
            </select>
          </div>

          <UploadDropzone v-model:file="file" :disabled="uploading || !selectedFoodId" @invalid="handleInvalid" />

          <button type="submit" class="na-btn na-btn--primary" :disabled="!file || !selectedFoodId || uploading">
            {{ uploading ? '上传中…' : '上传图片' }}
          </button>
        </form>

        <p class="na-admin-images__note">
          上传的图片会关联到所选食品；前端只做格式与大小预校验，真实性与安全性校验在后端完成。
        </p>
      </section>

      <!-- 右：图片列表 -->
      <section class="na-card na-admin-images__list-card">
        <h3 class="na-admin-images__section-title">
          图片列表
          <template v-if="selectedFood">（{{ selectedFood.name }}）</template>
        </h3>

        <SkeletonBlock v-if="loadingFoods" :rows="4" height="20px" />

        <template v-else>
          <div v-if="images.length > 0" class="na-admin-images__grid">
            <div v-for="image in images" :key="image.id" class="na-admin-images__item">
              <FoodImage
                :code="selectedFood ? selectedFood.code : ''"
                :name="selectedFood ? selectedFood.name : ''"
                :cover-image-url="image.url || image.thumbUrl"
                height="96px"
              />
              <div class="na-admin-images__item-meta">
                <span class="na-admin-images__caption">{{ textOrPlaceholder(image.caption) }}</span>
                <span class="na-admin-images__sub">{{ textOrPlaceholder(image.imageType) }}</span>
                <span class="na-admin-images__sub">{{ formatDateTime(image.createdAt) }}</span>
              </div>
              <button type="button" class="na-admin-images__link" @click="requestDelete(image)">删除</button>
            </div>
          </div>

          <EmptyState
            v-else
            icon="image"
            :title="selectedFood ? '该食品还没有图片记录' : '请先选择食品'"
            :description="
              selectedFood
                ? '可以先把封面图资源放到约定目录，再用上面的表单上传；后端未返回图片列表时这里也会显示为空。'
                : '在左侧搜索并选择食品后，这里会显示该食品的图片列表。'
            "
            compact
          />
        </template>
      </section>
    </div>

    <ConfirmDialog
      :open="deleteDialogOpen"
      title="删除图片"
      message="确认删除这张图片吗？该操作不可撤销。"
      confirm-text="确认删除"
      danger
      :loading="deleting"
      @confirm="confirmDelete"
      @cancel="deleteDialogOpen = false"
    />
  </div>
</template>

<style scoped>
.na-admin-images {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-admin-images__layout {
  display: grid;
  grid-template-columns: minmax(0, 380px) minmax(0, 1fr);
  gap: var(--na-space-4, 16px);
  align-items: start;
}

.na-admin-images__upload,
.na-admin-images__list-card {
  padding: var(--na-space-4, 16px);
  display: flex;
  flex-direction: column;
  gap: var(--na-space-3, 12px);
}

.na-admin-images__section-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.na-admin-images__form {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-3, 12px);
}

.na-admin-images__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.na-admin-images__field label {
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-admin-images__search-row {
  display: flex;
  gap: var(--na-space-2, 8px);
}

.na-admin-images__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--na-space-3, 12px);
}

.na-admin-images__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--na-space-2, 8px);
  border: 1px solid var(--na-color-border, #e5e7eb);
  border-radius: var(--na-radius-md, 8px);
}

.na-admin-images__item-meta {
  display: flex;
  flex-direction: column;
}

.na-admin-images__caption {
  font-size: 13px;
}

.na-admin-images__sub {
  font-size: 12px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-admin-images__link {
  border: none;
  background: none;
  padding: 0;
  align-self: flex-start;
  font-size: 13px;
  color: var(--na-color-danger, #ef4444);
  cursor: pointer;
}

.na-admin-images__note {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--na-color-text-muted, #9ca3af);
}

@media (max-width: 900px) {
  .na-admin-images__layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
