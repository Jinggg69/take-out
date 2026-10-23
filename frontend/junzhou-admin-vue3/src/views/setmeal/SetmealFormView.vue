<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import FormFeedback from '@/components/FormFeedback.vue'
import DataState from '@/components/DataState.vue'
import SetmealImageUpload from './SetmealImageUpload.vue'
import SetmealDishPicker from './SetmealDishPicker.vue'
import { ElInputNumber, ElTable, ElTableColumn } from 'element-plus'
import { setmealApi } from '@/api'
import type { SetmealCategory } from '@/api/setmeal'
import type { SetmealField } from '@/utils/setmeal'
import { createSetmealForm, setmealFormFromDetail, setmealPayload, setmealResultMessage, validateSetmeal } from '@/utils/setmeal'
import { errorMessage } from '@/utils/error-message'

const route = useRoute(); const router = useRouter()
const id = computed(() => { const value = route.query.id; return (Array.isArray(value) ? value[0] : value) || '' })
const isEdit = computed(() => Boolean(id.value))
const title = computed(() => isEdit.value ? '修改套餐' : '新建套餐')
const form = ref(createSetmealForm())
const formKey = ref(0)
const pickerOpen = ref(false)
const errors = ref<Partial<Record<SetmealField, string>>>({})
const message = ref(''); const loading = ref(false); const loadError = ref('')
const saving = ref(false); const uploading = ref(false); const continueAdding = ref(false)
const categories = ref<SetmealCategory[]>([]); const categoryLoading = ref(false); const categoryError = ref('')
const feedback = ref<InstanceType<typeof FormFeedback>>()
const summary = computed(() => Object.entries(errors.value).map(([key, value]) => ({ fieldId: `setmeal-${key}`, message: value })))
const busy = computed(() => saving.value || uploading.value)
let active = true; let version = 0
let saved = false
function canNavigate() {
  if (saving.value && !saved) { ElMessage.warning('套餐正在保存，请稍候'); return false }
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
    const { data } = await setmealApi.getCategories(true)
    if (!active) return
    if (String(data.code) !== '1' || !Array.isArray(data.data)) throw new Error(setmealResultMessage(data, '套餐分类加载失败'))
    categories.value = data.data
  } catch (error) { if (active) categoryError.value = errorMessage(error, '套餐分类加载失败，请重新加载') }
  finally { if (active) categoryLoading.value = false }
}
async function loadDetail() {
  const current = ++version
  form.value = createSetmealForm(); formKey.value++; pickerOpen.value = false; errors.value = {}; message.value = ''; loadError.value = ''; saving.value = false
  loading.value = isEdit.value
  if (!isEdit.value) return
  try {
    const { data } = await setmealApi.getById(id.value)
    if (!active || current !== version) return
    if (String(data.code) !== '1' || !data.data) throw new Error(setmealResultMessage(data, '套餐信息加载失败，请重试'))
    form.value = setmealFormFromDetail(data.data)
  } catch (error) { if (active && current === version) loadError.value = errorMessage(error, '套餐信息加载失败，请检查网络后重试') }
  finally { if (active && current === version) loading.value = false }
}
watch(id, () => { void loadDetail() }, { immediate: true })
function validateField(field: SetmealField) {
  const error = validateSetmeal(form.value)[field]
  if (error) errors.value[field] = error; else delete errors.value[field]
}
function cancel() { if (window.history.state?.back) router.back(); else void router.push('/setmeal') }
async function save(keepAdding = false) {
  if (busy.value || loading.value || loadError.value) return
  errors.value = validateSetmeal(form.value); message.value = ''
  if (Object.keys(errors.value).length) { await nextTick(); feedback.value?.focus(); return }
  saving.value = true; saved = false; continueAdding.value = keepAdding
  const current = version; const editing = isEdit.value
  try {
    const payload = setmealPayload(form.value, editing)
    const { data } = await (editing ? setmealApi.edit(payload) : setmealApi.add(payload))
    if (!active || current !== version) return
    if (String(data.code) !== '1') throw new Error(setmealResultMessage(data, '套餐保存失败，请重试'))
    ElMessage.success(editing ? '套餐修改成功' : '套餐添加成功')
    if (!editing && keepAdding) {
      form.value = createSetmealForm(); formKey.value++; errors.value = {}; saving.value = false
      await nextTick()
      if (active && current === version) document.getElementById('setmeal-name')?.focus()
    } else { saved = true; await router.push('/setmeal') }
  } catch (error) {
    if (active && current === version) { message.value = errorMessage(error, '套餐保存失败，请检查网络后重试'); await nextTick(); feedback.value?.focus() }
  } finally { if (active && current === version) saving.value = false }
}
</script>

<template>
  <div class="setmeal-form-page">
    <PageHeader :title="title">
      <ElButton
        :disabled="busy"
        @click="router.push('/setmeal')"
      >
        返回套餐列表
      </ElButton>
    </PageHeader>
    <section
      class="management-panel setmeal-form-panel"
      :aria-label="title"
    >
      <DataState
        v-if="loading"
        kind="loading"
        title="正在加载套餐信息"
        description="请稍候"
      />
      <DataState
        v-else-if="loadError"
        kind="error"
        title="套餐信息加载失败"
        :description="loadError"
      >
        <ElButton @click="loadDetail">
          重新加载
        </ElButton>
      </DataState>
      <div
        v-else
        class="setmeal-form"
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
          <div class="setmeal-basic-grid">
            <ElFormItem
              label="套餐名称"
              for="setmeal-name"
              required
              :error="errors.name"
            >
              <ElInput
                id="setmeal-name"
                v-model="form.name"
                placeholder="请填写套餐名称"
                maxlength="20"
                :aria-invalid="Boolean(errors.name)"
                aria-describedby="setmeal-name-hint"
                @blur="validateField('name')"
              />
              <p
                id="setmeal-name-hint"
                class="management-form-hint"
              >
                2至20位中文、字母或数字
              </p>
            </ElFormItem>
            <ElFormItem
              label="套餐分类"
              for="setmeal-categoryId"
              required
              :error="errors.categoryId"
            >
              <ElSelect
                id="setmeal-categoryId"
                v-model="form.categoryId"
                placeholder="请选择套餐分类"
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
                  :label="String(form.metadata.categoryName || '原套餐分类')"
                  :value="form.categoryId"
                />
              </ElSelect>
              <p
                v-if="categoryError"
                class="setmeal-category-error"
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
                暂无可选套餐分类，请先在分类管理中录入。
              </p>
            </ElFormItem>
            <ElFormItem
              label="套餐价格（元）"
              for="setmeal-price"
              required
              :error="errors.price"
            >
              <ElInput
                id="setmeal-price"
                v-model="form.price"
                inputmode="decimal"
                placeholder="请设置套餐价格"
                :aria-invalid="Boolean(errors.price)"
                aria-describedby="setmeal-price-hint"
                @blur="validateField('price')"
              />
              <p
                id="setmeal-price-hint"
                class="management-form-hint"
              >
                大于零，最多六位整数、两位小数
              </p>
            </ElFormItem>
          </div>
          <ElFormItem
            label="套餐菜品"
            required
            :error="errors.dishes"
          >
            <div
              id="setmeal-dishes"
              class="setmeal-dishes"
              tabindex="-1"
            >
              <ElButton
                :disabled="busy"
                @click="pickerOpen = true"
              >
                添加菜品
              </ElButton>
              <p
                v-if="!form.dishes.length"
                class="management-form-hint"
              >
                请添加至少一道菜品，每道菜可设置1至99份。
              </p>
              <div
                v-else
                class="management-table-region"
                role="region"
                aria-label="套餐菜品，可横向滚动"
                tabindex="0"
              >
                <ElTable
                  :data="form.dishes"
                  row-key="dishId"
                >
                  <ElTableColumn
                    prop="name"
                    label="名称"
                    min-width="140"
                  />
                  <ElTableColumn
                    label="原价（元）"
                    min-width="110"
                    align="right"
                  >
                    <template #default="{ row }">
                      {{ Number(row.price).toFixed(2) }}
                    </template>
                  </ElTableColumn>
                  <ElTableColumn
                    label="份数"
                    min-width="175"
                    align="right"
                  >
                    <template #default="{ row }">
                      <ElInputNumber
                        v-model="row.copies"
                        :min="1"
                        :max="99"
                        :precision="0"
                        :disabled="busy"
                        :aria-label="`${row.name}份数`"
                      />
                    </template>
                  </ElTableColumn>
                  <ElTableColumn
                    label="操作"
                    width="90"
                  >
                    <template #default="{ $index, row }">
                      <ElButton
                        link
                        type="danger"
                        :disabled="busy"
                        :aria-label="`删除套餐菜品${row.name}`"
                        @click="form.dishes.splice($index, 1)"
                      >
                        删除
                      </ElButton>
                    </template>
                  </ElTableColumn>
                </ElTable>
              </div>
            </div>
          </ElFormItem>
          <ElFormItem
            label="套餐图片"
            for="setmeal-image"
            required
            :error="errors.image"
          >
            <SetmealImageUpload
              :key="formKey"
              v-model="form.image"
              :disabled="saving"
              :invalid="Boolean(errors.image)"
              @busy="uploading = $event"
              @changed="validateField('image')"
            />
          </ElFormItem>
          <ElFormItem
            label="套餐描述"
            for="setmeal-description"
          >
            <ElInput
              id="setmeal-description"
              v-model="form.description"
              type="textarea"
              :rows="4"
              maxlength="200"
              show-word-limit
              placeholder="套餐描述，最长200字"
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
    <SetmealDishPicker
      v-if="pickerOpen"
      v-model="form.dishes"
      @close="pickerOpen = false; if (errors.dishes) validateField('dishes')"
    />
  </div>
</template>

<style scoped>
.setmeal-dishes { width: 100%; }
.setmeal-dishes > .el-button { margin-bottom: var(--jz-space-3); }
.setmeal-form-panel { max-width: 1040px; }
.setmeal-form { max-width: 880px; }
.setmeal-basic-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 0 var(--jz-space-6); }
.setmeal-form :deep(.el-form-item) { margin-bottom: var(--jz-space-6); }
.setmeal-form :deep(.el-form-item__error) { position: static; width: 100%; padding-top: var(--jz-space-2); line-height: 1.5; }
.setmeal-category-error { margin: var(--jz-space-2) 0 0; color: var(--jz-danger); font-size: var(--jz-font-control); }
@media (max-width: 767px) { .setmeal-basic-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
