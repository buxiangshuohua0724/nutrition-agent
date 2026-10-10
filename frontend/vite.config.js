import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite 配置
 * - `@` 别名指向 `src`
 * - 开发服务器固定 5173 端口
 * - `/api` 反向代理到后端 8080（因此前端请求 `/api/v1/...` 与后端路径完全一致，无跨域）
 * - 不使用任何 UI 组件框架，无 Tailwind / Sass 插件
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // outDir 默认 dist；关闭 sourcemap 以保持产物精简
    outDir: 'dist',
    sourcemap: false,
    // src/styles/index.js 用顶层 await 装载设计样式（缺失时降级为兜底令牌），
    // 顶层 await 属于 ES2022，因此目标设为 esnext。
    target: 'esnext',
  },
})
