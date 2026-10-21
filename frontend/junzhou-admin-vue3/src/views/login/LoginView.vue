<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import logoUrl from '@/assets/brand/junzhou-logo.png'
import FormFeedback from '@/components/FormFeedback.vue'
import { useUserStore } from '@/stores'
import { validateLogin } from '@/utils/auth-validation'
import type { FieldErrors } from '@/utils/auth-validation'
import type { LoginPayload } from '@/api/types'
import { errorMessage } from '@/utils/error-message'

const router = useRouter()
const user = useUserStore()
const form = reactive<LoginPayload>({ username: '', password: '' })
const errors = ref<FieldErrors<LoginPayload>>({})
const message = ref('')
const loading = ref(false)
const feedback = ref<InstanceType<typeof FormFeedback>>()
const summary = computed(() => Object.entries(errors.value).map(([key, value]) => ({ fieldId: `login-${key}`, message: value })))
function validateField(field: keyof LoginPayload) {
  const error = validateLogin(form)[field]
  if (error) errors.value[field] = error
  else delete errors.value[field]
}
async function submit() {
  if (loading.value) return
  errors.value = validateLogin(form)
  message.value = ''
  if (Object.keys(errors.value).length) { await nextTick(); feedback.value?.focus(); return }
  loading.value = true
  try {
    await user.login(form)
    await router.push('/')
  } catch (error) {
    message.value = errorMessage(error, '登录失败，请检查网络后重试')
    await nextTick(); feedback.value?.focus()
  } finally { loading.value = false }
}
</script>

<template>
  <main class="login-page">
    <section
      class="login-panel"
      aria-labelledby="login-title"
    >
      <div class="login-brand">
        <img
          :src="logoUrl"
          alt="均州鱼庄"
          width="240"
          height="240"
        />
        <p>门店运营管理</p>
      </div>
      <div class="login-content">
        <header class="login-heading">
          <h1 id="login-title">
            登录管理系统
          </h1>
          <p>使用员工账号登录均州鱼庄</p>
        </header>
        <FormFeedback
          ref="feedback"
          :message="message"
          :items="summary"
        />
        <ElForm
          :model="form"
          label-position="top"
          size="large"
          @submit.prevent="submit"
        >
          <ElFormItem
            label="账号"
            prop="username"
            for="login-username"
            :error="errors.username"
          >
            <ElInput
              id="login-username"
              v-model="form.username"
              autocomplete="username"
              placeholder="请输入账号"
              :disabled="loading"
              :aria-invalid="Boolean(errors.username)"
              @blur="validateField('username')"
            />
          </ElFormItem>
          <ElFormItem
            label="密码"
            prop="password"
            for="login-password"
            :error="errors.password"
          >
            <ElInput
              id="login-password"
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              show-password
              :disabled="loading"
              :aria-invalid="Boolean(errors.password)"
              @blur="validateField('password')"
            />
          </ElFormItem>
          <ElButton
            native-type="submit"
            type="primary"
            size="large"
            class="login-submit"
            :loading="loading"
          >
            {{ loading ? '登录中…' : '登录' }}
          </ElButton>
        </ElForm>
        <p class="login-footer">
          均州鱼庄管理系统
        </p>
      </div>
    </section>
  </main>
</template>

<style scoped lang="scss">
.login-page { display: grid; min-height: 100dvh; padding: var(--jz-space-8); place-items: center; }
.login-panel { display: grid; grid-template-columns: 42% 1fr; width: min(960px, 100%); min-height: 520px; overflow: hidden; background: var(--jz-surface); border: 1px solid var(--jz-border); border-radius: var(--jz-radius-dialog); }
.login-brand { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--jz-space-6); padding: var(--jz-space-8); background: var(--jz-nav-background); color: var(--jz-brand-gold); }
.login-brand img { display: block; width: min(240px, 100%); height: auto; aspect-ratio: 1; object-fit: contain; }
.login-brand p { margin: 0; font-size: var(--jz-font-section); }
.login-content { align-self: center; width: min(384px, 100%); padding: var(--jz-space-8); margin: auto; }
.login-heading { margin-bottom: var(--jz-space-8); }
h1 { margin: 0 0 var(--jz-space-2); font-size: var(--jz-font-title); font-weight: 600; }
.login-heading p, .login-footer { margin: 0; color: var(--jz-text-secondary); font-size: var(--jz-font-control); }
.login-submit { width: 100%; min-height: var(--jz-control-height-large); margin-top: var(--jz-space-2); }
.login-footer { margin-top: var(--jz-space-8); }
@media (max-width: 767px) {
  .login-page { padding: var(--jz-space-4); }
  .login-panel { grid-template-columns: 1fr; min-height: 0; }
  .login-brand { flex-direction: row; justify-content: flex-start; gap: var(--jz-space-4); padding: var(--jz-space-6); }
  .login-brand img { width: 72px; height: 72px; }
  .login-brand p { font-size: var(--jz-font-body); }
  .login-content { width: 100%; padding: var(--jz-space-6); }
  .login-heading { margin-bottom: var(--jz-space-6); }
}
</style>
