<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import FormFeedback from '@/components/FormFeedback.vue'
import { authApi } from '@/api'
import { validatePasswordChange } from '@/utils/auth-validation'
import type { FieldErrors, PasswordForm } from '@/utils/auth-validation'
import { errorMessage } from '@/utils/error-message'

const visible = defineModel<boolean>({ required: true })
const form = reactive<PasswordForm>({ oldPassword: '', newPassword: '', affirmPassword: '' })
const errors = ref<FieldErrors<PasswordForm>>({})
const saving = ref(false)
const message = ref('')
const feedback = ref<InstanceType<typeof FormFeedback>>()
const summary = computed(() => Object.entries(errors.value).map(([key, value]) => ({ fieldId: `password-${key}`, message: value })))
watch(visible, open => {
  if (open) {
    Object.assign(form, { oldPassword: '', newPassword: '', affirmPassword: '' })
    errors.value = {}; message.value = ''
  }
})
function validateField(field: keyof PasswordForm) {
  const error = validatePasswordChange(form)[field]
  if (error) errors.value[field] = error
  else delete errors.value[field]
}
async function save() {
  if (saving.value) return
  message.value = ''; errors.value = validatePasswordChange(form)
  if (Object.keys(errors.value).length) { await nextTick(); feedback.value?.focus(); return }
  saving.value = true
  try {
    const { data } = await authApi.editPassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    if (String(data.code) !== '1') throw new Error(data.msg || '密码修改失败，请重试')
    ElMessage.success('密码修改成功')
    visible.value = false
  } catch (error) {
    message.value = errorMessage(error, '密码修改失败，请检查网络后重试')
    await nextTick(); feedback.value?.focus()
  } finally { saving.value = false }
}
</script>

<template>
  <ElDialog
    v-model="visible"
    title="修改密码"
    width="min(560px, calc(100vw - 32px))"
    class="jz-dialog"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    destroy-on-close
  >
    <FormFeedback
      ref="feedback"
      :message="message"
      :items="summary"
    />
    <ElForm
      id="password-form"
      :model="form"
      label-position="top"
      size="large"
      @submit.prevent="save"
    >
      <ElFormItem
        label="原始密码"
        for="password-oldPassword"
        :error="errors.oldPassword"
      >
        <ElInput
          id="password-oldPassword"
          v-model="form.oldPassword"
          type="password"
          autocomplete="current-password"
          show-password
          :disabled="saving"
          :aria-invalid="Boolean(errors.oldPassword)"
          @blur="validateField('oldPassword')"
        />
      </ElFormItem>
      <ElFormItem
        label="新密码"
        for="password-newPassword"
        :error="errors.newPassword"
      >
        <ElInput
          id="password-newPassword"
          v-model="form.newPassword"
          type="password"
          autocomplete="new-password"
          placeholder="6至20位数字或字母，区分大小写"
          show-password
          :disabled="saving"
          :aria-invalid="Boolean(errors.newPassword)"
          @blur="validateField('newPassword')"
        />
      </ElFormItem>
      <ElFormItem
        label="确认密码"
        for="password-affirmPassword"
        :error="errors.affirmPassword"
      >
        <ElInput
          id="password-affirmPassword"
          v-model="form.affirmPassword"
          type="password"
          autocomplete="new-password"
          show-password
          :disabled="saving"
          :aria-invalid="Boolean(errors.affirmPassword)"
          @blur="validateField('affirmPassword')"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton
        :disabled="saving"
        @click="visible = false"
      >
        取消
      </ElButton>
      <ElButton
        native-type="submit"
        form="password-form"
        type="primary"
        :loading="saving"
      >
        保存
      </ElButton>
    </template>
  </ElDialog>
</template>
