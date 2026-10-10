<script setup>
/**
 * 登录 / 注册页
 *
 * 契约依据（4.6，注意路径是 `/auth/**` 与 `/users/me/**`）：
 *   - 登录 `POST /auth/login`     → `{ token | accessToken, user }`
 *   - 注册 `POST /auth/register`  → 创建账号（是否自动登录由后端决定）
 *
 * 后端未就绪时页面不会白屏：表单仍在，提交失败会显示明确的中文错误提示
 * （网络错误、参数校验失败、账号密码错误等由 api 层映射）。
 */
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import NavIcon from '../components/NavIcon.vue'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { validateCredentialForm } from '../utils/validators'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

/** `login` | `register` */
const mode = ref('login')
const submitting = ref(false)
const formError = ref('')
const fieldErrors = reactive({ username: '', password: '', confirmPassword: '' })

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})

const isRegister = computed(() => mode.value === 'register')

/** 登录成功后的跳转目标（来自 `?redirect=`，只接受站内相对路径） */
const redirectTarget = computed(() => {
  const raw = route.query.redirect
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')) return value
  return '/'
})

/** 清空字段级错误 */
function clearErrors() {
  fieldErrors.username = ''
  fieldErrors.password = ''
  fieldErrors.confirmPassword = ''
  formError.value = ''
}

/** 切换登录/注册 */
function switchMode(next) {
  mode.value = next
  clearErrors()
  form.password = ''
  form.confirmPassword = ''
}

/** 提交 */
async function submit() {
  if (submitting.value) return
  clearErrors()

  const validation = validateCredentialForm(form, isRegister.value)
  if (!validation.valid) {
    Object.assign(fieldErrors, validation.errors)
    return
  }

  submitting.value = true
  try {
    if (isRegister.value) {
      await authStore.register({
        username: form.username.trim(),
        password: form.password,
      })
      toastStore.success('注册成功，请使用新账号登录')
      mode.value = 'login'
      form.password = ''
      form.confirmPassword = ''
      return
    }

    await authStore.login({ username: form.username.trim(), password: form.password })
    toastStore.success('登录成功')
    router.push(redirectTarget.value)
  } catch (error) {
    // api 层已弹出全局提示；这里额外在表单上方给一条可读的说明，避免用户只看到一闪而过的 toast
    formError.value = error && error.message ? error.message : '操作失败，请稍后重试'
    if (error && error.isNetworkError) {
      formError.value = '无法连接后端服务，请确认后端已启动（默认 8080 端口）后重试。'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="na-login">
    <div class="na-login__card na-card">
      <div class="na-login__brand">
        <span class="na-login__logo">AI</span>
        <div>
          <h1 class="na-login__title">AI 食品营养师</h1>
          <p class="na-login__subtitle">
            {{ isRegister ? '创建账号后可以同步收藏与偏好' : '登录后可以同步收藏、会话与偏好设置' }}
          </p>
        </div>
      </div>

      <!-- 登录 / 注册 切换 -->
      <div class="na-login__tabs" role="tablist">
        <button
          type="button"
          role="tab"
          class="na-login__tab"
          :class="{ 'is-active': !isRegister }"
          :aria-selected="!isRegister"
          @click="switchMode('login')"
        >
          登录
        </button>
        <button
          type="button"
          role="tab"
          class="na-login__tab"
          :class="{ 'is-active': isRegister }"
          :aria-selected="isRegister"
          @click="switchMode('register')"
        >
          注册
        </button>
      </div>

      <p v-if="formError" class="na-login__error" role="alert">
        <NavIcon name="warning" :size="16" />
        {{ formError }}
      </p>

      <form class="na-login__form" @submit.prevent="submit">
        <div class="na-login__field">
          <label for="na-login-username">用户名</label>
          <input
            id="na-login-username"
            v-model="form.username"
            class="na-input"
            type="text"
            autocomplete="username"
            maxlength="32"
            placeholder="3~32 个字符"
          />
          <span v-if="fieldErrors.username" class="na-login__field-error">{{ fieldErrors.username }}</span>
        </div>

        <div class="na-login__field">
          <label for="na-login-password">密码</label>
          <input
            id="na-login-password"
            v-model="form.password"
            class="na-input"
            type="password"
            :autocomplete="isRegister ? 'new-password' : 'current-password'"
            maxlength="64"
            placeholder="6~64 个字符"
          />
          <span v-if="fieldErrors.password" class="na-login__field-error">{{ fieldErrors.password }}</span>
        </div>

        <div v-if="isRegister" class="na-login__field">
          <label for="na-login-confirm">确认密码</label>
          <input
            id="na-login-confirm"
            v-model="form.confirmPassword"
            class="na-input"
            type="password"
            autocomplete="new-password"
            maxlength="64"
            placeholder="请再次输入密码"
          />
          <span v-if="fieldErrors.confirmPassword" class="na-login__field-error">{{ fieldErrors.confirmPassword }}</span>
        </div>

        <button type="submit" class="na-btn na-btn--primary na-login__submit" :disabled="submitting">
          {{ submitting ? '提交中…' : isRegister ? '注册' : '登录' }}
        </button>
      </form>

      <p class="na-login__hint">
        未登录也可以查询营养数据、试用 AI 问答与图片识别（游客额度有限）。
      </p>
      <RouterLink class="na-login__back" to="/">← 返回首页</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.na-login {
  display: flex;
  justify-content: center;
  padding: var(--na-space-6, 32px) var(--na-space-4, 16px);
}

.na-login__card {
  width: 100%;
  max-width: 420px;
  padding: var(--na-space-6, 28px);
  display: flex;
  flex-direction: column;
  gap: var(--na-space-4, 16px);
}

.na-login__brand {
  display: flex;
  align-items: center;
  gap: var(--na-space-3, 12px);
}

.na-login__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--na-radius-md, 10px);
  background: var(--na-color-primary-500, #3b82f6);
  color: #ffffff;
  font-weight: 700;
}

.na-login__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.na-login__subtitle {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-login__tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  border-radius: var(--na-radius-md, 8px);
  background: var(--na-color-surface-muted, #f3f4f6);
}

.na-login__tab {
  flex: 1;
  border: none;
  background: none;
  padding: 8px 0;
  border-radius: var(--na-radius-sm, 6px);
  font-size: 14px;
  color: var(--na-color-text-muted, #6b7280);
  cursor: pointer;
}

.na-login__tab.is-active {
  background: var(--na-color-surface, #ffffff);
  color: var(--na-color-text, #111827);
  font-weight: 600;
  box-shadow: var(--na-shadow-sm, 0 1px 2px rgba(0, 0, 0, 0.06));
}

.na-login__error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0;
  padding: var(--na-space-2, 8px) var(--na-space-3, 12px);
  border-radius: var(--na-radius-sm, 6px);
  background: #fef2f2;
  color: var(--na-color-danger, #ef4444);
  font-size: 13px;
  line-height: 1.6;
}

.na-login__form {
  display: flex;
  flex-direction: column;
  gap: var(--na-space-3, 12px);
}

.na-login__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.na-login__field label {
  font-size: 13px;
  color: var(--na-color-text-muted, #6b7280);
}

.na-login__field-error {
  font-size: 12px;
  color: var(--na-color-danger, #ef4444);
}

.na-login__submit {
  height: var(--na-control-height-md, 36px);
}

.na-login__hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--na-color-text-muted, #9ca3af);
}

.na-login__back {
  font-size: 13px;
  color: var(--na-color-primary-600, #2563eb);
}
</style>
