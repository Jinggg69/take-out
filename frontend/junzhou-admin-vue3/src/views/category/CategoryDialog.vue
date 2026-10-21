<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import FormFeedback from '@/components/FormFeedback.vue'
import { categoryApi } from '@/api'
import type { CategoryEditor, CategoryForm } from '@/api/category'
import type { FieldErrors } from '@/utils/auth-validation'
import { categoryCreatePayload, categoryEditPayload, categoryFormFromRow, categoryResultMessage, createCategoryForm, validateCategory } from '@/utils/category'
import { errorMessage } from '@/utils/error-message'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ editor: CategoryEditor | null }>()
const emit = defineEmits<{ saved: [] }>()
const title = computed(() => props.editor?.mode === 'edit' ? '修改分类' : props.editor?.type === '2' ? '新增套餐分类' : '新增菜品分类')
const form = ref(createCategoryForm())
const errors = ref<FieldErrors<CategoryForm>>({})
const message = ref('')
const saving = ref(false)
const continuing = ref(false)
const feedback = ref<InstanceType<typeof FormFeedback>>()
const summary = computed(() => Object.entries(errors.value).map(([key, value]) => ({ fieldId: `category-${key}`, message: value })))
let active = true
let version = 0
onBeforeUnmount(() => { active = false; version += 1 })
watch([visible, () => props.editor], ([open]) => {
  version += 1
  if (open && props.editor) {
    form.value = props.editor.mode === 'edit' ? categoryFormFromRow(props.editor.record) : createCategoryForm()
    errors.value = {}; message.value = ''; saving.value = false; continuing.value = false
  }
})
function focusName() {
  if (!saving.value && !message.value && !Object.keys(errors.value).length) document.getElementById('category-name')?.focus()
}
function validateField(field: keyof CategoryForm) {
  const error = validateCategory(form.value, title.value)[field]
  if (error) errors.value[field] = error
  else delete errors.value[field]
}
async function save(keepAdding = false) {
  if (saving.value || !props.editor) return
  errors.value = validateCategory(form.value, title.value); message.value = ''
  if (Object.keys(errors.value).length) { await nextTick(); feedback.value?.focus(); return }
  const current = version
  const editor = props.editor
  saving.value = true; continuing.value = keepAdding
  try {
    const { data } = await (editor.mode === 'add'
      ? categoryApi.add(categoryCreatePayload(form.value, editor.type))
      : categoryApi.edit(categoryEditPayload(form.value, editor.record.id)))
    if (!active || current !== version) return
    if (String(data.code) !== '1') throw new Error(categoryResultMessage(data, '分类保存失败，请重试'))
    ElMessage.success(editor.mode === 'add' ? '分类添加成功' : '分类修改成功')
    emit('saved')
    if (editor.mode === 'add' && keepAdding) {
      form.value = createCategoryForm(); errors.value = {}; saving.value = false
      await nextTick()
      if (active && current === version) focusName()
    } else visible.value = false
  } catch (error) {
    if (active && current === version) {
      message.value = errorMessage(error, '分类保存失败，请检查网络后重试')
      await nextTick(); feedback.value?.focus()
    }
  } finally { if (active && current === version) saving.value = false }
}
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="title"
    width="min(560px, calc(100vw - 32px))"
    class="jz-dialog category-dialog"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    destroy-on-close
    @opened="focusName"
  >
    <FormFeedback
      ref="feedback"
      :message="message"
      :items="summary"
    />
    <ElForm
      id="category-form"
      :model="form"
      :disabled="saving"
      label-position="top"
      size="large"
      class="category-dialog-form"
      @submit.prevent="save()"
    >
      <ElFormItem
        label="分类名称"
        for="category-name"
        required
        :error="errors.name"
      >
        <ElInput
          id="category-name"
          v-model="form.name"
          placeholder="请输入分类名称"
          maxlength="20"
          :aria-invalid="Boolean(errors.name)"
          aria-describedby="category-name-hint"
          @blur="validateField('name')"
        />
        <p
          id="category-name-hint"
          class="management-form-hint"
        >
          2至20个中文或英文字母
        </p>
      </ElFormItem>
      <ElFormItem
        label="排序"
        for="category-sort"
        required
        :error="errors.sort"
      >
        <ElInput
          id="category-sort"
          v-model="form.sort"
          inputmode="numeric"
          placeholder="请输入排序"
          :aria-invalid="Boolean(errors.sort)"
          aria-describedby="category-sort-hint"
          @blur="validateField('sort')"
        />
        <p
          id="category-sort-hint"
          class="management-form-hint"
        >
          请输入0至99的整数
        </p>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <div class="category-dialog-actions">
        <ElButton
          :disabled="saving"
          @click="visible = false"
        >
          取消
        </ElButton>
        <ElButton
          native-type="submit"
          form="category-form"
          type="primary"
          :loading="saving && !continuing"
          :disabled="saving"
        >
          确定
        </ElButton>
        <ElButton
          v-if="editor?.mode === 'add'"
          :loading="saving && continuing"
          :disabled="saving"
          @click="save(true)"
        >
          保存并继续添加
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.category-dialog-form .el-form-item { margin-bottom: var(--jz-space-6); }
.category-dialog-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: var(--jz-space-3); }
.category-dialog-actions .el-button { margin-left: 0; }
</style>
