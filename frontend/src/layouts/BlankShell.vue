<script setup>
/**
 * 无外壳版式（登录页等）：不带侧边栏与标题栏，内容自行铺满整屏。
 */
</script>

<template>
  <div class="na-shell na-shell--blank">
    <main class="na-shell__main">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.na-shell {
  min-height: 100vh;
  display: flex;
  background: var(--na-color-bg, #f8fafc);
}

.na-shell__main {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
</style>
