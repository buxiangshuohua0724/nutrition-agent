<script setup>
/**
 * 用户侧外壳：左侧固定侧边栏 + 顶部标题栏 + 主内容区
 */
import AppSidebar from '../components/AppSidebar.vue'
import AppHeader from '../components/AppHeader.vue'
</script>

<template>
  <div class="na-shell na-shell--default">
    <AppSidebar />
    <div class="na-shell__body">
      <AppHeader variant="user" />
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
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--na-space-5, 20px) var(--na-space-6, 24px) var(--na-space-8, 32px);
}
</style>
