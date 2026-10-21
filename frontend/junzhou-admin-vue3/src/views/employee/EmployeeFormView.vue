<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage, ElRadio, ElRadioGroup } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import FormFeedback from '@/components/FormFeedback.vue'
import DataState from '@/components/DataState.vue'
import { employeeApi } from '@/api'
import type { EmployeeFields } from '@/api/employee'
import type { FieldErrors } from '@/utils/auth-validation'
import { createEmployeeForm, employeeFormFromDetail, employeePayload, validateEmployee } from '@/utils/employee'
import { errorMessage } from '@/utils/error-message'

const route = useRoute()
const router = useRouter()
const id = computed(() => { const value = route.query.id; return (Array.isArray(value) ? value[0] : value) || '' })
const isEdit = computed(() => Boolean(id.value))
const title = computed(() => isEdit.value ? '修改员工信息' : '添加员工')
const form = ref(createEmployeeForm())
const errors = ref<FieldErrors<EmployeeFields>>({})
const message = ref('')
const loadError = ref('')
const loading = ref(false)
const saving = ref(false)
const continueAdding = ref(false)
const feedback = ref<InstanceType<typeof FormFeedback>>()
const summary = computed(() => Object.entries(errors.value).map(([key, value]) => ({ fieldId: `employee-${key}`, message: value })))
let active = true
let version = 0
onBeforeUnmount(() => { active = false; version += 1 })
async function loadDetail() {
  const current = ++version
  errors.value = {}; message.value = ''; loadError.value = ''; saving.value = false
  form.value = createEmployeeForm()
  loading.value = isEdit.value
  if (!isEdit.value) return
  try {
    const { data } = await employeeApi.getById(id.value)
    if (!active || current !== version) return
    if (String(data.code) !== '1' || !data.data) throw new Error(data.msg || '员工信息加载失败，请重试')
    form.value = employeeFormFromDetail(data.data)
  } catch (error) {
    if (active && current === version) loadError.value = errorMessage(error, '员工信息加载失败，请检查网络后重试')
  } finally { if (active && current === version) loading.value = false }
}
watch(id, () => { void loadDetail() }, { immediate: true })
function validateField(field: keyof EmployeeFields) {
  const error = validateEmployee(form.value)[field]
  if (error) errors.value[field] = error
  else delete errors.value[field]
}
async function save(keepAdding = false) {
  if (saving.value || loading.value || loadError.value) return
  errors.value = validateEmployee(form.value); message.value = ''
  if (Object.keys(errors.value).length) { await nextTick(); feedback.value?.focus(); return }
  const current = version
  const editing = isEdit.value
  saving.value = true; continueAdding.value = keepAdding
  try {
    const payload = employeePayload(form.value)
    const { data } = await (editing ? employeeApi.edit(payload) : employeeApi.add(payload))
    if (!active || current !== version) return
    if (String(data.code) !== '1') throw new Error(data.msg || '员工信息保存失败，请重试')
    ElMessage.success(editing ? '员工信息修改成功' : '员工添加成功')
    if (!editing && keepAdding) {
      form.value = createEmployeeForm(); errors.value = {}; saving.value = false
      await nextTick()
      if (active && current === version) document.getElementById('employee-username')?.focus()
    } else await router.push('/employee')
  } catch (error) {
    if (active && current === version) {
      message.value = errorMessage(error, '员工信息保存失败，请检查网络后重试')
      await nextTick(); feedback.value?.focus()
    }
  } finally { if (active && current === version) saving.value = false }
}
</script>

<template>
  <div class="employee-form-page">
    <PageHeader :title="title">
      <ElButton
        :disabled="saving"
        @click="router.push('/employee')"
      >
        返回员工列表
      </ElButton>
    </PageHeader>
    <section
      class="management-panel management-form-panel"
      :aria-label="title"
    >
      <DataState
        v-if="loading"
        kind="loading"
        title="正在加载员工信息"
        description="请稍候"
      />
      <DataState
        v-else-if="loadError"
        kind="error"
        title="员工信息加载失败"
        :description="loadError"
      >
        <ElButton @click="loadDetail">
          重新加载
        </ElButton>
      </DataState>
      <div
        v-else
        class="management-form"
      >
        <FormFeedback
          ref="feedback"
          :message="message"
          :items="summary"
        />
        <ElForm
          :model="form"
          :disabled="saving"
          label-position="top"
          size="large"
          @submit.prevent="save()"
        >
          <ElFormItem
            label="账号"
            for="employee-username"
            required
            :error="errors.username"
          >
            <ElInput
              id="employee-username"
              v-model="form.username"
              autocomplete="off"
              placeholder="请输入账号"
              maxlength="20"
              :aria-invalid="Boolean(errors.username)"
              aria-describedby="employee-username-hint"
              @blur="validateField('username')"
            />
            <p
              id="employee-username-hint"
              class="management-form-hint"
            >
              3至20位小写字母或数字
            </p>
          </ElFormItem>
          <ElFormItem
            label="员工姓名"
            for="employee-name"
            required
            :error="errors.name"
          >
            <ElInput
              id="employee-name"
              v-model="form.name"
              placeholder="请输入员工姓名"
              maxlength="12"
              :aria-invalid="Boolean(errors.name)"
              @blur="validateField('name')"
            />
          </ElFormItem>
          <ElFormItem
            label="手机号"
            for="employee-phone"
            required
            :error="errors.phone"
          >
            <ElInput
              id="employee-phone"
              v-model="form.phone"
              type="tel"
              inputmode="tel"
              placeholder="请输入手机号"
              maxlength="11"
              :aria-invalid="Boolean(errors.phone)"
              @blur="validateField('phone')"
            />
          </ElFormItem>
          <ElFormItem label="性别">
            <ElRadioGroup
              v-model="form.sex"
              aria-label="性别"
            >
              <ElRadio value="男">
                男
              </ElRadio>
              <ElRadio value="女">
                女
              </ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem
            label="身份证号"
            for="employee-idNumber"
            required
            :error="errors.idNumber"
          >
            <ElInput
              id="employee-idNumber"
              v-model="form.idNumber"
              placeholder="请输入身份证号"
              maxlength="20"
              :aria-invalid="Boolean(errors.idNumber)"
              @blur="validateField('idNumber')"
            />
          </ElFormItem>
          <div class="management-form-actions">
            <ElButton
              :disabled="saving"
              @click="router.push('/employee')"
            >
              取消
            </ElButton>
            <ElButton
              native-type="submit"
              type="primary"
              :loading="saving && !continueAdding"
              :disabled="saving"
            >
              保存
            </ElButton>
            <ElButton
              v-if="!isEdit"
              :loading="saving && continueAdding"
              :disabled="saving"
              @click="save(true)"
            >
              保存并继续添加
            </ElButton>
          </div>
        </ElForm>
      </div>
    </section>
  </div>
</template>
