<script setup>
/**
 * 应用根组件
 *
 * 只做三件事：
 *   1. 按当前路由的 `meta.layout` 选择外壳（用户侧 / 后台 / 无外壳）；
 *   2. 全局挂载轻提示容器（ToastHost）；
 *   3. 在样式兜底生效时给出一条可见提示，避免"设计令牌没加载"被静默忽略。
 *
 * 外壳形态用 `meta.layout` 判定而不是嵌套路由：嵌套路由下子路由的 `meta.title`
 * 无法直接驱动外层标题栏，用 layout 判定更直接。
 */
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'

import DefaultShell from './layouts/DefaultShell.vue'
import AdminShell from './layouts/AdminShell.vue'
import BlankShell from './layouts/BlankShell.vue'
import ToastHost from './components/ToastHost.vue'
import { isDesignStylesReady, missingDesignStyles, styleLoadErrors } from './styles/index.js'
import { useAuthStore } from './stores/auth.js'

const route = useRoute()
const authStore = useAuthStore()

/** 当前外壳组件 */
const shellComponent = computed(() => {
  const value = route.meta && route.meta.layout
  if (value === 'admin') return AdminShell
  if (value === 'blank') return BlankShell
  return DefaultShell
})

/** 设计令牌缺失时的提示文案 */
const designWarning = computed(() => {
  if (isDesignStylesReady) return ''
  const missing = missingDesignStyles.length > 0 ? missingDesignStyles.join('、') : 'design/tokens.css'
  const errors = styleLoadErrors.length > 0 ? `；加载异常：${styleLoadErrors.join('；')}` : ''
  return `未加载到设计令牌（${missing}），当前使用内置兜底样式${errors}`
})

/** 懒加载当前用户资料（本地有 token 才请求） */
function ensureProfile() {
  if (authStore.isLoggedIn && !authStore.profileLoaded) {
    authStore.fetchProfile().catch(() => {
      /* 失败已由 api 层统一提示，这里不打断页面 */
    })
  }
}

onMounted(ensureProfile)

watch(
  () => route.meta && route.meta.layout,
  () => ensureProfile(),
)
</script>

<template>
  <div class="na-app">
    <p v-if="designWarning" class="na-design-warning" role="status">{{ designWarning }}</p>

    <component :is="shellComponent">
      <RouterView v-slot="{ Component, route: matched }">
        <component :is="Component" :key="matched.fullPath" />
      </RouterView>
    </component>

    <ToastHost />
  </div>
</template>

<style scoped>
.na-app {
  min-height: 100vh;
}
</style>
