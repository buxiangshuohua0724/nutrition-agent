/**
 * 应用入口
 *
 * 样式加载顺序很重要：先加载兜底令牌（见 `styles/fallback-tokens.css`），
 * 再通过 `styles/index.js` 动态尝试加载 `design/tokens.css` 与 `design/base.css`。
 * 设计令牌可用时给 `<html>` 加上 `na-design-ready`，兜底样式自动失效。
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import './styles/index.js'
// 后台通用样式（自维护，不触碰 design/ 目录）
import './styles/admin.css'

import App from './App.vue'
import router from './router/index.js'
import { useAuthStore } from './stores/auth.js'
import { useToastStore } from './stores/toast.js'
import { configureApiClient } from './api/client.js'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 在 pinia 就绪之后才能取 store，把鉴权与提示能力注入 api 层
// （client.js 不直接 import store，避免循环依赖）
const authStore = useAuthStore(pinia)
const toastStore = useToastStore(pinia)

configureApiClient({
  /** 供 client.js 取当前令牌 */
  getToken: () => authStore.token,
  /** 401 / 40101：清理登录态并跳转登录页 */
  onUnauthorized: () => {
    authStore.clearAuth()
    const current = router.currentRoute.value
    if (current && current.name !== 'login') {
      router
        .push({
          name: 'login',
          query: { redirect: current.fullPath },
        })
        .catch(() => {})
    }
  },
  /** 统一错误提示 */
  notify: (payload) => toastStore.notify(payload),
})

app.mount('#app')
