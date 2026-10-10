/**
 * 前端预校验工具
 *
 * 依据 `docs/api-contract.md` 4.5：
 *   - 允许 `image/jpeg`、`image/png`、`image/webp`
 *   - 大小 ≤ 10MB
 *   - 超限分别返回 `41501` / `41301`
 *
 * 说明：前端校验只为体验（尽早反馈），**不能替代后端校验**。
 * 后端还必须校验文件头魔数——前端不做、也不应假装做了这件事。
 */

/** 允许的图片 MIME 类型 */
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

/** 允许的扩展名（MIME 缺失时用于降级判断，最终仍以后端魔数校验为准） */
export const ALLOWED_IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp']

/** 图片大小上限：10MB */
export const MAX_IMAGE_SIZE = 10 * 1024 * 1024

/** 图片 size 上限的可读文案 */
export const MAX_IMAGE_SIZE_TEXT = '10MB'

/**
 * 取文件扩展名（小写，不含点）。
 * @param {string} fileName
 * @returns {string}
 */
export function getExtension(fileName) {
  if (!fileName) return ''
  const parts = String(fileName).split('.')
  return parts.length > 1 ? parts.pop().toLowerCase() : ''
}

/**
 * 校验单个图片文件。
 *
 * @param {File|{type?: string, size?: number, name?: string}|null|undefined} file
 * @returns {{valid: boolean, code: number|null, message: string|null}}
 *   `code` 为契约错误码：41501 格式不支持 / 41301 文件过大。
 */
export function validateImageFile(file) {
  if (!file) {
    return { valid: false, code: 40001, message: '请先选择要上传的图片' }
  }

  const mime = (file.type || '').toLowerCase()
  const extension = getExtension(file.name)
  const mimeAllowed = ALLOWED_IMAGE_TYPES.includes(mime)
  const extensionAllowed = ALLOWED_IMAGE_EXTENSIONS.includes(extension)

  // MIME 与扩展名都不在允许范围内才判为格式不支持；
  // 部分浏览器对 webp/heic 的 MIME 上报不可靠，因此允许扩展名兜底。
  if (!mimeAllowed && !extAllowedFallback(mime, extensionAllowed)) {
    return {
      valid: false,
      code: 41501,
      message: `图片格式不支持，仅支持 JPEG / PNG / WebP（当前：${mime || extension || '未知格式'}）`,
    }
  }

  const size = Number(file.size)
  if (Number.isFinite(size) && size > MAX_IMAGE_SIZE) {
    return {
      valid: false,
      code: 41301,
      message: `图片文件过大，请上传不超过 ${MAX_IMAGE_SIZE_TEXT} 的图片`,
    }
  }

  if (Number.isFinite(size) && size === 0) {
    return { valid: false, code: 40001, message: '该图片文件为空，请重新选择' }
  }

  return { valid: true, code: null, message: null }
}

/**
 * MIME 判定兜底：MIME 为空（部分系统取不到）时才信任扩展名。
 * @param {string} mime
 * @param {boolean} extensionAllowed
 * @returns {boolean}
 */
function extAllowedFallback(mime, extensionAllowed) {
  return (mime === '' || mime === 'application/octet-stream') && extensionAllowed
}

/**
 * 批量校验（多图上传场景），返回第一个错误。
 * @param {Array<File>} files
 * @returns {{valid: boolean, code: number|null, message: string|null, invalidFile: File|null}}
 */
export function validateImageFiles(files) {
  const list = Array.isArray(files) ? files : []
  for (const file of list) {
    const result = validateImageFile(file)
    if (!result.valid) {
      return { ...result, invalidFile: file }
    }
  }
  return { valid: true, code: null, message: null, invalidFile: null }
}

/**
 * 生成便于展示的文件提示（格式 + 大小）。
 * @param {File|null|undefined} file
 * @returns {{name: string, sizeText: string, typeText: string}|null}
 */
export function describeFile(file) {
  if (!file) return null
  const size = Number(file.size)
  const sizeText = !Number.isFinite(size)
    ? '大小未知'
    : size < 1024 * 1024
      ? `${(size / 1024).toFixed(1)} KB`
      : `${(size / (1024 * 1024)).toFixed(2)} MB`
  return {
    name: file.name || '未命名文件',
    sizeText,
    typeText: file.type || '未知类型',
  }
}

/**
 * 校验营养计算的重量输入（契约 4.2：必须 > 0 且 ≤ 100000）。
 * **不返回任何营养数值**，只做输入合法性判断。
 * @param {string|number|null|undefined} weight 用户输入
 * @returns {{valid: boolean, code: number|null, message: string|null, value: number|null}}
 */
export function validateWeight(weight) {
  if (weight === null || weight === undefined || weight === '') {
    return { valid: false, code: 40001, message: '请填写食用重量', value: null }
  }
  const value = Number(weight)
  if (!Number.isFinite(value)) {
    return { valid: false, code: 40002, message: '重量必须是数字', value: null }
  }
  if (value <= 0) {
    return { valid: false, code: 40002, message: '食用重量必须大于 0', value: null }
  }
  if (value > 100000) {
    return { valid: false, code: 40002, message: '食用重量不能超过 100000', value: null }
  }
  return { valid: true, code: null, message: null, value }
}

/**
 * 校验登录/注册表单。
 * @param {{username?: string, password?: string, confirmPassword?: string}} form
 * @param {boolean} [isRegister=false] 是否为注册场景
 * @returns {{valid: boolean, errors: Record<string, string>}}
 */
export function validateCredentialForm(form = {}, isRegister = false) {
  const errors = {}
  const username = (form.username || '').trim()
  const password = form.password || ''

  if (!username) {
    errors.username = '请输入用户名'
  } else if (username.length < 3 || username.length > 32) {
    errors.username = '用户名长度需为 3~32 个字符'
  }

  if (!password) {
    errors.password = '请输入密码'
  } else if (password.length < 6 || password.length > 64) {
    errors.password = '密码长度需为 6~64 个字符'
  }

  if (isRegister) {
    if (form.confirmPassword !== password) {
      errors.confirmPassword = '两次输入的密码不一致'
    }
  }

  return { valid: Object.keys(errors).length === 0, errors }
}
