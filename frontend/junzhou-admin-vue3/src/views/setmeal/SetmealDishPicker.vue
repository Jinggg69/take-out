<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElButton, ElCheckbox, ElDialog, ElInput } from 'element-plus'
import DataState from '@/components/DataState.vue'
import { setmealApi } from '@/api'
import type { SetmealCategory, SetmealDish, SetmealId } from '@/api/setmeal'
import type { DishRecord } from '@/api/dish'
import { formatSetmealPrice, searchSetmealDishes, setmealResultMessage, toggleSetmealDish } from '@/utils/setmeal'
import { errorMessage } from '@/utils/error-message'

const model = defineModel<SetmealDish[]>({ required: true })
const emit = defineEmits<{ close: [] }>()
const api = setmealApi
const draft = ref(model.value.map(item => ({ ...item })))
const categories = ref<SetmealCategory[]>([])
const categoryId = ref<SetmealId>('')
const name = ref(''); const searching = ref(false)
const dishes = ref<DishRecord[]>([])
const loading = ref(false); const error = ref(''); const categoryError = ref('')
let active = true; let version = 0; let categoriesLoading = false
onBeforeUnmount(() => { active = false; version++ })
onMounted(() => { void loadCategories() })
async function loadCategories() {
  if (categoriesLoading) return
  categoriesLoading = true; categoryError.value = ''; loading.value = true
  try {
    const { data } = await api.getDishCategories()
    if (!active) return
    if (String(data.code) !== '1' || !Array.isArray(data.data)) throw new Error(setmealResultMessage(data, '菜品分类加载失败'))
    categories.value = data.data
    categoryId.value = data.data[0]?.id ?? ''
    await load()
  } catch (cause) { if (active) categoryError.value = errorMessage(cause, '菜品分类加载失败，请重试') }
  finally { categoriesLoading = false; if (active) loading.value = false }
}
async function getDishes(params: { categoryId?: SetmealId; name?: string }) {
  const { data } = await api.getDishes(params)
  if (String(data.code) !== '1' || !Array.isArray(data.data)) throw new Error(setmealResultMessage(data, '菜品列表加载失败'))
  return data.data
}
async function load() {
  const current = ++version
  loading.value = true; error.value = ''
  const keyword = name.value.trim(); searching.value = Boolean(keyword)
  try {
    let result: DishRecord[]
    if (keyword) result = await searchSetmealDishes(api, name.value, categories.value)
    else result = categoryId.value === '' ? [] : await getDishes({ categoryId: categoryId.value })
    if (active && current === version) dishes.value = result
  } catch (cause) { if (active && current === version) error.value = errorMessage(cause, '菜品列表加载失败，请重试') }
  finally { if (active && current === version) loading.value = false }
}
function changeCategory(id: SetmealId) { categoryId.value = id; name.value = ''; void load() }
function confirm() { model.value = draft.value.map(item => ({ ...item })); emit('close') }
</script>

<template>
  <ElDialog
    :model-value="true"
    title="添加菜品"
    width="min(880px, calc(100vw - 24px))"
    class="setmeal-dish-dialog"
    @close="emit('close')"
  >
    <form
      class="picker-search"
      @submit.prevent="load"
    >
      <label for="setmeal-dish-search">菜品名称</label>
      <ElInput
        id="setmeal-dish-search"
        v-model="name"
        placeholder="请输入菜品名称进行搜索"
        clearable
        @clear="load"
      />
      <ElButton
        native-type="submit"
        :loading="loading"
      >
        搜索
      </ElButton>
    </form>
    <DataState
      v-if="categoryError"
      kind="error"
      title="菜品分类加载失败"
      :description="categoryError"
    >
      <ElButton @click="loadCategories">
        重新加载
      </ElButton>
    </DataState>
    <div
      v-else
      class="picker-columns"
    >
      <div class="picker-available">
        <div
          v-if="!searching"
          class="picker-categories"
          aria-label="菜品分类"
        >
          <ElButton
            v-for="item in categories"
            :key="item.id"
            :type="String(categoryId) === String(item.id) ? 'primary' : 'default'"
            :aria-pressed="String(categoryId) === String(item.id)"
            @click="changeCategory(item.id)"
          >
            {{ item.name }}
          </ElButton>
        </div>
        <DataState
          v-if="loading"
          kind="loading"
          title="正在加载菜品"
        />
        <DataState
          v-else-if="error"
          kind="error"
          title="菜品加载失败"
          :description="error"
        >
          <ElButton @click="load">
            重新加载
          </ElButton>
        </DataState>
        <div
          v-else-if="dishes.length"
          class="picker-options"
          role="group"
          aria-label="可选菜品"
        >
          <div
            v-for="dish in dishes"
            :key="dish.id"
            class="picker-option"
          >
            <ElCheckbox
              :model-value="draft.some(item => String(item.dishId) === String(dish.id))"
              :aria-label="`选择${dish.name}`"
              @change="draft = toggleSetmealDish(draft, dish, Boolean($event))"
            >
              {{ dish.name }}
            </ElCheckbox>
            <span>{{ String(dish.status) === '0' ? '停售' : '在售' }}</span><span class="picker-price">￥{{ formatSetmealPrice(dish.price) }}</span>
          </div>
        </div>
        <DataState
          v-else
          :title="searching ? '未找到匹配的菜品' : '暂无可选菜品'"
          description="请更换分类或调整搜索名称。"
        />
      </div>
      <section
        class="picker-selected"
        aria-label="已选菜品"
      >
        <h3 aria-live="polite">
          已选菜品（{{ draft.length }}）
        </h3>
        <p
          v-if="!draft.length"
          class="management-form-hint"
        >
          从左侧选择套餐内的菜品。
        </p>
        <div
          v-for="item in draft"
          :key="item.dishId"
          class="picker-selected-item"
        >
          <span>{{ item.name }}<small>{{ item.copies }}份 · ￥{{ formatSetmealPrice(item.price) }}</small></span>
          <ElButton
            link
            type="danger"
            :aria-label="`移除${item.name}`"
            @click="draft = draft.filter(dish => String(dish.dishId) !== String(item.dishId))"
          >
            移除
          </ElButton>
        </div>
      </section>
    </div>
    <template #footer>
      <ElButton @click="emit('close')">
        取消
      </ElButton><ElButton
        type="primary"
        @click="confirm"
      >
        添加
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.picker-search { display: flex; align-items: center; gap: var(--jz-space-3); margin-bottom: var(--jz-space-4); }
.picker-search label { flex-shrink: 0; }
.picker-search .el-input { max-width: 360px; }
.picker-columns { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: var(--jz-space-6); }
.picker-categories { display: flex; flex-wrap: wrap; gap: var(--jz-space-2); margin-bottom: var(--jz-space-3); }
.picker-categories .el-button { margin-left: 0; }
.picker-options, .picker-selected { max-height: 400px; overflow: auto; }
.picker-option { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: var(--jz-space-3); border-bottom: 1px solid var(--jz-border); min-height: 48px; }
.picker-option :deep(.el-checkbox__label) { white-space: normal; overflow-wrap: anywhere; }
.picker-option > span { color: var(--jz-text-secondary); font-size: var(--jz-font-caption); }
.picker-price { font-variant-numeric: tabular-nums; text-align: right; }
.picker-selected { border-left: 1px solid var(--jz-border); padding-left: var(--jz-space-4); }
.picker-selected h3 { margin: 0 0 var(--jz-space-3); font-size: var(--jz-font-body); }
.picker-selected-item { display: flex; justify-content: space-between; gap: var(--jz-space-3); align-items: center; min-height: 54px; border-bottom: 1px solid var(--jz-border); }
.picker-selected-item > span { overflow-wrap: anywhere; }
.picker-selected small { display: block; margin-top: var(--jz-space-1); color: var(--jz-text-secondary); }
@media (max-width: 600px) { .picker-search { flex-wrap: wrap; } .picker-search .el-input { flex: 1; min-width: 160px; } .picker-columns { grid-template-columns: minmax(0, 1fr); } .picker-selected { border-left: 0; padding-left: 0; border-top: 1px solid var(--jz-border); padding-top: var(--jz-space-4); } .picker-options, .picker-selected { max-height: 240px; } }
</style>
