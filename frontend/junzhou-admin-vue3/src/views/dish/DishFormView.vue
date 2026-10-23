<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import FormFeedback from '@/components/FormFeedback.vue'
import DataState from '@/components/DataState.vue'
import DishImageUpload from '@/components/DishImageUpload.vue'
import FlavorEditor from './FlavorEditor.vue'
import { dishApi } from '@/api'
import type { DishCategory } from '@/api/dish'
import type { DishField } from '@/utils/dish'
import { createDishForm, dishFormFromDetail, dishPayload, dishResultMessage, validateDish } from '@/utils/dish'
import { errorMessage } from '@/utils/error-message'

const route = useRoute(); const router = useRouter()
const id = computed(() => { const value = route.query.id; return (Array.isArray(value) ? value[0] : value) || '' })
const isEdit = computed(() => Boolean(id.value))
const title = computed(() => isEdit.value ? '修改菜品' : '新建菜品')
const form = ref(createDishForm())
const formKey = ref(0)
const errors = ref<Partial<Record<DishField, string>>>({})
const message = ref(''); const loading = ref(false); const loadError = ref('')
const saving = ref(false); const uploading = ref(false); const continueAdding = ref(false)
const categories = ref<DishCategory[]>([]); const categoryLoading = ref(false); const categoryError = ref('')
const feedback = ref<InstanceType<typeof FormFeedback>>()
const summary = computed(() => Object.entries(errors.value).map(([key, value]) => ({ fieldId: `dish-${key}`, message: value })))
const busy = computed(() => saving.value || uploading.value)
let active = true; let version = 0
let saved = false
function canNavigate() {
  if (saving.value && !saved) { ElMessage.warning('菜品正在保存，请稍候'); return false }
  return true
}
onBeforeRouteLeave(canNavigate)
onBeforeRouteUpdate(canNavigate)
onBeforeUnmount(() => { active = false; version++ })
onMounted(() => { void loadCategories() })
async function loadCategories() {
  if (categoryLoading.value) return
  categoryLoading.value = true; categoryError.value = ''
  try {
    const { data } = await dishApi.getCategories()
    if (!active) return
    if (String(data.code) !== '1' || !Array.isArray(data.data)) throw new Error(dishResultMessage(data, '菜品分类加载失败'))
    categories.value = data.data
  } catch (error) { if (active) categoryError.value = errorMessage(error, '菜品分类加载失败，请重新加载') }
  finally { if (active) categoryLoading.value = false }
}
async function loadDetail() {
  const current = ++version
  form.value = createDishForm(); formKey.value++; errors.value = {}; message.value = ''; loadError.value = ''; saving.value = false
  loading.value = isEdit.value
  if (!isEdit.value) return
  try {
    const { data } = await dishApi.getById(id.value)
    if (!active || current !== version) return
    if (String(data.code) !== '1' || !data.data) throw new Error(dishResultMessage(data, '菜品信息加载失败，请重试'))
    form.value = dishFormFromDetail(data.data)
  } catch (error) { if (active && current === version) loadError.value = errorMessage(error, '菜品信息加载失败，请检查网络后重试') }
  finally { if (active && current === version) loading.value = false }
}
watch(id, () => { void loadDetail() }, { immediate: true })
function validateField(field: DishField) {
  const error = validateDish(form.value)[field]
  if (error) errors.value[field] = error; else delete errors.value[field]
}
function cancel() { if (window.history.state?.back) router.back(); else void router.push('/dish') }
async function save(keepAdding = false) {
  if (busy.value || loading.value || loadError.value) return
  errors.value = validateDish(form.value); message.value = ''
  if (Object.keys(errors.value).length) { await nextTick(); feedback.value?.focus(); return }
  saving.value = true; saved = false; continueAdding.value = keepAdding
  const current = version; const editing = isEdit.value
  try {
    const payload = dishPayload(form.value, editing)
    const { data } = await (editing ? dishApi.edit(payload) : dishApi.add(payload))
    if (!active || current !== version) return
    if (String(data.code) !== '1') throw new Error(dishResultMessage(data, '菜品保存失败，请重试'))
    ElMessage.success(editing ? '菜品修改成功' : '菜品添加成功')
    if (!editing && keepAdding) {
      form.value = createDishForm(); formKey.value++; errors.value = {}; saving.value = false
      await nextTick()
      if (active && current === version) document.getElementById('dish-name')?.focus()
    } else { saved = true; await router.push('/dish') }
  } catch (error) {
    if (active && current === version) { message.value = errorMessage(error, '菜品保存失败，请检查网络后重试'); await nextTick(); feedback.value?.focus() }
  } finally { if (active && current === version) saving.value = false }
}
</script>

<template>
  <div class="dish-form-page">
    <PageHeader :title="title">
      <ElButton
        :disabled="busy"
        @click="router.push('/dish')"
      >
        返回菜品列表
      </ElButton>
    </PageHeader>
    <section
      class="management-panel dish-form-panel"
      :aria-label="title"
    >
      <DataState
        v-if="loading"
        kind="loading"
        title="正在加载菜品信息"
        description="请稍候"
      />
      <DataState
        v-else-if="loadError"
        kind="error"
        title="菜品信息加载失败"
        :description="loadError"
      >
        <ElButton @click="loadDetail">
          重新加载
        </ElButton>
      </DataState>
      <div
        v-else
        class="dish-form"
      >
        <FormFeedback
          ref="feedback"
          :message="message"
          :items="summary"
        />
        <ElForm
          :model="form"
          :disabled="busy"
          label-position="top"
          size="large"
          @submit.prevent="save()"
        >
          <div class="dish-basic-grid">
            <ElFormItem
              label="菜品名称"
              for="dish-name"
              required
              :error="errors.name"
            >
              <ElInput
                id="dish-name"
                v-model="form.name"
                placeholder="请填写菜品名称"
                maxlength="20"
                :aria-invalid="Boolean(errors.name)"
                aria-describedby="dish-name-hint"
                @blur="validateField('name')"
              />
              <p
                id="dish-name-hint"
                class="management-form-hint"
              >
                2至20位中文、字母或数字
              </p>
            </ElFormItem>
            <ElFormItem
              label="菜品分类"
              for="dish-categoryId"
              required
              :error="errors.categoryId"
            >
              <ElSelect
                id="dish-categoryId"
                v-model="form.categoryId"
                placeholder="请选择菜品分类"
                :loading="categoryLoading"
                :aria-invalid="Boolean(errors.categoryId)"
                @change="validateField('categoryId')"
              >
                <ElOption
                  v-for="item in categories"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
                <ElOption
                  v-if="isEdit && form.categoryId !== '' && !categories.some(item => String(item.id) === String(form.categoryId))"
                  :label="String(form.metadata.categoryName || '原菜品分类')"
                  :value="form.categoryId"
                />
              </ElSelect>
              <p
                v-if="categoryError"
                class="dish-category-error"
                role="alert"
              >
                {{ categoryError }} <ElButton
                  link
                  type="primary"
                  :disabled="busy"
                  @click="loadCategories"
                >
                  重新加载分类
                </ElButton>
              </p>
              <p
                v-else-if="!categoryLoading && !categories.length"
                class="management-form-hint"
              >
                暂无可选菜品分类，请先在分类管理中录入。
              </p>
            </ElFormItem>
            <ElFormItem
              label="菜品价格（元）"
              for="dish-price"
              required
              :error="errors.price"
            >
              <ElInput
                id="dish-price"
                v-model="form.price"
                inputmode="decimal"
                placeholder="请设置菜品价格"
                :aria-invalid="Boolean(errors.price)"
                aria-describedby="dish-price-hint"
                @blur="validateField('price')"
              />
              <p
                id="dish-price-hint"
                class="management-form-hint"
              >
                大于零，最多六位整数、两位小数
              </p>
            </ElFormItem>
          </div>
          <FlavorEditor
            v-model="form.flavors"
            :disabled="busy"
          />
          <ElFormItem
            label="菜品图片"
            for="dish-image"
            required
            :error="errors.image"
          >
            <DishImageUpload
              :key="formKey"
              v-model="form.image"
              :disabled="saving"
              :invalid="Boolean(errors.image)"
              @busy="uploading = $event"
              @changed="validateField('image')"
            />
          </ElFormItem>
          <ElFormItem
            label="菜品描述"
            for="dish-description"
          >
            <ElInput
              id="dish-description"
              v-model="form.description"
              type="textarea"
              :rows="4"
              maxlength="200"
              show-word-limit
              placeholder="菜品描述，最长200字"
            />
          </ElFormItem>
          <div class="management-form-actions">
            <ElButton
              :disabled="busy"
              @click="cancel"
            >
              取消
            </ElButton>
            <ElButton
              native-type="submit"
              type="primary"
              :loading="saving && !continueAdding"
              :disabled="busy"
            >
              保存
            </ElButton>
            <ElButton
              v-if="!isEdit"
              :loading="saving && continueAdding"
              :disabled="busy"
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

<style scoped>
.dish-form-panel { max-width: 1040px; }
.dish-form { max-width: 880px; }
.dish-basic-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 0 var(--jz-space-6); }
.dish-form :deep(.el-form-item) { margin-bottom: var(--jz-space-6); }
.dish-form :deep(.el-form-item__error) { position: static; width: 100%; padding-top: var(--jz-space-2); line-height: 1.5; }
.dish-category-error { margin: var(--jz-space-2) 0 0; color: var(--jz-danger); font-size: var(--jz-font-control); }
@media (max-width: 767px) { .dish-basic-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
