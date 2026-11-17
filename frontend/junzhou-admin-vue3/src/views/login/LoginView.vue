<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElCheckbox, ElForm, ElFormItem, ElInput } from 'element-plus'
import { authApi } from '@/api'
import FormFeedback from '@/components/FormFeedback.vue'
import { useUserStore } from '@/stores'
import { validateLogin } from '@/utils/auth-validation'
import { errorMessage } from '@/utils/error-message'

const router = useRouter()
const user = useUserStore()
const form = reactive({ username: 'junzhou', password: '123456', captcha: '' })
const remember = ref(false)
const captchaImage = ref('')
const captchaLoading = ref(false)
const message = ref('')
const loading = ref(false)
const feedback = ref<InstanceType<typeof FormFeedback>>()
const year = new Date().getFullYear()
const key = 'junzhou.login.remember'
try {
  const saved = JSON.parse(localStorage.getItem(key) || 'null')
  if (saved && typeof saved.username === 'string' && typeof saved.password === 'string') {
    form.username = saved.username; form.password = saved.password; remember.value = true
  }
} catch { /* Storage may be unavailable in private browsing. */ }
function clearRemembered(value: unknown) {
  if (!value) { try { localStorage.removeItem(key) } catch { /* Optional storage. */ } }
}
async function refreshCaptcha() {
  if (captchaLoading.value) return
  captchaLoading.value = true
  captchaImage.value = ''; form.captcha = ''
  try {
    const { data } = await authApi.captcha()
    if (String(data.code) !== '1' || !data.data) throw new Error(data.msg || '验证码加载失败')
    captchaImage.value = data.data
  } catch (error) { message.value = errorMessage(error, '验证码加载失败，请点击重试') }
  finally { captchaLoading.value = false }
}
onMounted(refreshCaptcha)
async function submit() {
  if (loading.value) return
  message.value = Object.values(validateLogin(form))[0] || (!form.captcha.trim() ? '请输入图片验证码' : '')
  if (message.value) { await nextTick(); feedback.value?.focus(); return }
  loading.value = true
  try {
    await user.login(form)
    try {
      if (remember.value) localStorage.setItem(key, JSON.stringify({ username: form.username, password: form.password }))
      else localStorage.removeItem(key)
    } catch { /* A successful login does not depend on browser storage. */ }
    await router.push('/')
  } catch (error) {
    message.value = errorMessage(error, '登录失败，请检查网络后重试')
    await refreshCaptcha()
    await nextTick(); feedback.value?.focus()
  } finally { loading.value = false }
}
</script>

<template>
  <main class="login-page">
    <section
      class="login-brand"
      aria-label="均州餐饮点餐营销管理系统"
    >
      <div class="brand-picture">
        <svg
          class="food-art"
          viewBox="0 0 240 130"
          fill="none"
          stroke="currentColor"
          stroke-width="4"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M12 60C12 8 108 8 108 60Z M12 66Q5 76 14 86L106 86Q116 76 106 66Z M14 90V103Q14 114 26 114H94Q106 114 106 103V90 M14 76Q24 64 34 76T54 76T74 76T94 76T106 76" />
          <path d="M41 39h1m23-9h1m22 15h1M145 10C115 10 115 63 139 66L135 116Q145 128 153 116L149 66C173 60 173 10 145 10Z M190 10V47Q190 63 207 67L202 116Q211 128 219 116L214 67Q232 62 232 47V10 M204 10V44 M218 10V44" />
        </svg>
      </div>
      <footer class="brand-caption">
        <h2>均州餐饮点餐营销管理系统</h2>
        <p>欢迎使用均州餐饮点餐营销管理系统，您的点餐、门店运营与会员营销小管家！</p>
      </footer>
    </section>
    <section
      class="login-panel"
      aria-labelledby="login-title"
    >
      <div class="login-content">
        <h1 id="login-title">
          <span aria-hidden="true">🍴</span> 均州餐饮点餐营销管理系统
        </h1>
        <FormFeedback
          ref="feedback"
          :message="message"
        />
        <ElForm
          :model="form"
          size="large"
          @submit.prevent="submit"
        >
          <ElFormItem>
            <ElInput
              id="login-username"
              v-model="form.username"
              aria-label="账号"
              autocomplete="username"
              placeholder="请输入账号"
              :disabled="loading"
            />
          </ElFormItem>
          <ElFormItem>
            <ElInput
              id="login-password"
              v-model="form.password"
              aria-label="密码"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              show-password
              :disabled="loading"
            />
          </ElFormItem>
          <ElFormItem>
            <div class="captcha-row">
              <ElInput
                v-model="form.captcha"
                aria-label="图片验证码"
                placeholder="请输入验证码"
                autocomplete="off"
                :maxlength="4"
                :disabled="loading"
              />
              <button
                class="captcha-image"
                type="button"
                aria-label="刷新图片验证码"
                title="点击更换验证码"
                :disabled="captchaLoading || loading"
                @click="refreshCaptcha"
              >
                <img
                  v-if="captchaImage"
                  :src="captchaImage"
                  alt="图片验证码"
                />
                <span v-else>{{ captchaLoading ? '加载中…' : '点击重试' }}</span>
              </button>
            </div>
          </ElFormItem>
          <ElCheckbox
            v-model="remember"
            :disabled="loading"
            @change="clearRemembered"
          >
            记住密码
          </ElCheckbox>
          <ElButton
            native-type="submit"
            type="primary"
            class="login-submit"
            :loading="loading"
            :disabled="!captchaImage || captchaLoading"
          >
            {{ loading ? '登录中…' : '立即登录' }}
          </ElButton>
        </ElForm>
        <p class="login-footer">
          Copyright © 2022–{{ year }} <span>均州餐饮</span> All Rights Reserved.
        </p>
      </div>
    </section>
  </main>
</template>

<style scoped lang="scss">
.login-page { --el-color-primary: #982d34; --el-color-primary-light-3: #b04b51; --el-color-primary-dark-2: #79232a; display: grid; grid-template-columns: 75.7% 24.3%; min-height: 100dvh; background: #fff; color: #606266; }
.login-brand { display: flex; flex-direction: column; min-height: 100dvh; background: #982d34; color: #fff; }
.brand-picture { flex: 1; display: grid; place-items: center; }
.food-art { width: 12%; min-width: 140px; transform: translateY(9vh); }
.brand-caption { min-height: 14.3vh; box-sizing: border-box; padding: 3.5vh 2.4vw 3vh; background: #3b0d12; }
.brand-caption h2 { margin: 0 0 12px; font-size: clamp(22px, 1.5vw, 38px); font-weight: 700; color: #fff; }
.brand-caption p { margin: 0; font-size: clamp(12px, .72vw, 18px); line-height: 1.7; }
.login-content { width: 76.5%; margin: 9.6vh auto 0; padding-bottom: 32px; }
h1 { display: flex; align-items: center; white-space: nowrap; margin: 0 0 3.8vh; font-size: clamp(16px, 1.13vw, 29px); font-weight: 400; color: #707070; }
h1 span { color: #982d34; }
:deep(.el-form-item) { margin-bottom: 1.7vh; }
:deep(.el-input__wrapper) { min-height: clamp(42px, 4.1vh, 56px); border-radius: 4px; padding: 0 13px; }
.captcha-row { display: flex; width: 100%; }
.captcha-row :deep(.el-input) { flex: 1; min-width: 0; }
.captcha-image { width: 28%; min-width: 82px; padding: 0; border: 1px solid #dcdfe6; background: #faf4f4; cursor: pointer; color: #982d34; }
.captcha-image img { display: block; width: 100%; height: 100%; object-fit: fill; }
.captcha-image:focus-visible { outline: 2px solid #982d34; outline-offset: 3px; }
:deep(.el-checkbox) { margin: .3vh 0 2.1vh; }
.login-submit { width: 100%; min-height: clamp(44px, 4.2vh, 58px); font-size: 18px; }
.login-footer { margin: 20px 0 0; text-align: center; color: #858b95; font-size: 12px; line-height: 1.7; }
.login-footer span { color: #982d34; }
@media (max-width: 1100px) { .login-page { grid-template-columns: 60% 40%; } .login-content { width: 84%; } }
@media (max-width: 650px) { .login-page { grid-template-columns: 1fr; } .login-brand { min-height: 230px; } .brand-picture { min-height: 140px; } .food-art { width: 120px; min-width: 0; transform: none; } .brand-caption { min-height: 14.3vh; box-sizing: border-box; padding: 20px 24px; } .brand-caption h2 { font-size: 22px; } .login-content { width: min(440px, 86%); margin-top: 36px; } h1 { font-size: clamp(17px, 4.5vw, 24px); margin-bottom: 28px; } :deep(.el-form-item) { margin-bottom: 20px; } }
</style>
