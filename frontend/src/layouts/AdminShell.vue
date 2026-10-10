<script setup>
/**
 * 管理后台外壳：后台菜单 + 顶部「管理员」栏 + 主内容区
 * 与用户侧外壳完全分离，避免后台页面误用用户导航。
 */
import AdminSidebar from '../components/AdminSidebar.vue'
import AppHeader from '../components/AppHeader.vue'
</script>

<template>
  <div class="na-shell na-shell--admin">
    <AdminSidebar />
    <div class="na-shell__body">
      <AppHeader variant="admin" />
      <main class="na-shell__main">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.na-shell {
  min-height: 100vh;
  display: flex;
  align-items: stretch;
  background: var(--na-color-bg, #f8fafc);
}

.na-shell__body {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.na-shell__main {
  flex: 1 1 auto;
  min-width: 0;
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: var(--na-space-5, 20px) var(--na-space-6, 24px) var(--na-space-8, 32px);
}
</style>
