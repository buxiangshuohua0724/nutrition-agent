<script setup>
/**
 * 404 页面
 *
 * 不使用用户侧/后台侧外壳逻辑里的任何数据，只提供返回入口。
 */
import { useRouter } from 'vue-router'

const router = useRouter()

/** 返回上一页；没有历史记录时回首页 */
function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/')
}
</script>

<template>
  <div class="na-not-found">
    <p class="na-not-found__code">404</p>
    <h1 class="na-not-found__title">页面不存在</h1>
    <p class="na-not-found__desc">
      你访问的地址可能已经变更或被删除。可以回到首页，或直接搜索食品。
    </p>
    <div class="na-not-found__actions">
      <RouterLink class="na-btn na-btn--primary" to="/">返回首页</RouterLink>
      <RouterLink class="na-btn na-btn--ghost" to="/search">去食品搜索</RouterLink>
      <button type="button" class="na-btn na-btn--ghost" @click="goBack">返回上一页</button>
    </div>
  </div>
</template>

<style scoped>
.na-not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--na-space-3, 12px);
  padding: var(--na-space-7, 64px) var(--na-space-4, 16px);
  text-align: center;
}

.na-not-found__code {
  margin: 0;
  font-size: 56px;
  font-weight: 700;
  line-height: 1;
  color: var(--na-color-primary-500, #3b82f6);
}

.na-not-found__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.na-not-found__desc {
  margin: 0;
  max-width: 460px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--na-color-text-muted, #6b7280);
}

.na-not-found__actions {
  display: flex;
  gap: var(--na-space-2, 8px);
  flex-wrap: wrap;
  justify-content: center;
  margin-top: var(--na-space-2, 8px);
}
</style>
