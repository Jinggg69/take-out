<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElImage, ElInput, ElMessage, ElMessageBox, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { setmealApi } from '@/api'
import type { SetmealCategory, SetmealId, SetmealRecord } from '@/api/setmeal'
import { setmealResultMessage, formatSetmealPrice, nextSetmealStatus } from '@/utils/setmeal'
import { errorMessage } from '@/utils/error-message'
import { createSetmealList } from './list-state'

const router = useRouter()
const list = createSetmealList(setmealApi)
const { state } = list
const categories = ref<SetmealCategory[]>([])
const categoryError = ref('')
const categoryLoading = ref(false)
const selected = ref<SetmealId[]>([])
const pending = ref('')
let active = true
watch(() => state.records, () => { selected.value = [] })
onMounted(() => { void list.load(); void loadCategories() })
onBeforeUnmount(() => { active = false; list.dispose() })
async function loadCategories() {
  if (categoryLoading.value) return
  categoryLoading.value = true; categoryError.value = ''
  try {
    const { data } = await setmealApi.getCategories()
    if (!active) return
    if (String(data.code) !== '1' || !Array.isArray(data.data)) throw new Error(setmealResultMessage(data, '套餐分类加载失败'))
    categories.value = data.data
  } catch (error) { if (active) categoryError.value = errorMessage(error, '套餐分类加载失败，请重试') }
  finally { if (active) categoryLoading.value = false }
}
async function action(kind: 'delete' | 'status', row?: SetmealRecord) {
  if (pending.value || state.loading || state.error) return
  const ids = row ? [row.id] : [...selected.value]
  if (!ids.length) { ElMessage.error('请选择删除对象'); return }
  const status = row ? nextSetmealStatus(row.status) : 0
  const label = kind === 'delete' ? '删除' : status === 0 ? '停售' : '启售'
  pending.value = `${kind}:${row?.id ?? 'batch'}`
  try {
    await ElMessageBox.confirm(kind === 'delete'
      ? row ? `确认删除套餐“${row.name}”？删除后无法恢复。` : `确认删除已选择的 ${ids.length} 个套餐？删除后无法恢复。`
      : row ? `确认${label}套餐“${row.name}”？` : `确认${label}已选择的 ${ids.length} 个套餐？`, kind === 'delete' ? '确定删除' : '调整售卖状态', { confirmButtonText: kind === 'delete' ? '删除' : '确定', cancelButtonText: '取消', type: 'warning' })
    if (!active) return
    const { data } = await (kind === 'delete' ? setmealApi.remove(ids.join(',')) : setmealApi.setStatus(row!.id, status))
    if (!active) return
    if (String(data.code) !== '1') throw new Error(data.msg || setmealResultMessage(data, `套餐${label}失败，请重试`))
    ElMessage.success(kind === 'delete' ? '套餐删除成功' : '套餐状态更改成功')
    await list.load()
  } catch (error) { if (active && error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error, `套餐${label}失败，请检查网络后重试`)) }
  finally { pending.value = '' }
}
function select(rows: SetmealRecord[]) { selected.value = rows.map(row => row.id) }
</script>

<template>
  <div class="setmeal-page">
    <PageHeader title="套餐管理">
      <ElButton
        type="primary"
        :disabled="Boolean(pending)"
        @click="router.push('/setmeal/add')"
      >
        新建套餐
      </ElButton>
    </PageHeader>
    <section
      class="management-panel"
      aria-label="套餐查询与列表"
    >
      <form
        class="management-filter setmeal-filter"
        @submit.prevent="list.search"
      >
        <div class="management-filter-field">
          <label for="setmeal-search-name">套餐名称</label>
          <ElInput
            id="setmeal-search-name"
            v-model="state.name"
            placeholder="请填写套餐名称"
            clearable
            :disabled="Boolean(pending)"
            @clear="list.clearName"
            @keydown.enter.prevent="list.search"
          />
        </div>
        <div class="management-filter-field">
          <label for="setmeal-search-category">套餐分类</label>
          <ElSelect
            id="setmeal-search-category"
            v-model="state.categoryId"
            placeholder="请选择"
            clearable
            :loading="categoryLoading"
            :disabled="Boolean(pending)"
            @clear="list.clearCategory"
          >
            <ElOption
              v-for="item in categories"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </ElSelect>
        </div>
        <div class="management-filter-field setmeal-status-filter">
          <label for="setmeal-search-status">售卖状态</label>
          <ElSelect
            id="setmeal-search-status"
            v-model="state.status"
            placeholder="请选择"
            clearable
            :disabled="Boolean(pending)"
            @clear="list.clearStatus"
          >
            <ElOption
              label="停售"
              :value="0"
            />
            <ElOption
              label="启售"
              :value="1"
            />
          </ElSelect>
        </div>
        <ElButton
          native-type="submit"
          :loading="state.loading"
          :disabled="Boolean(pending)"
        >
          查询
        </ElButton>
      </form>
      <p
        v-if="categoryError"
        class="setmeal-category-error"
        role="alert"
      >
        {{ categoryError }} <ElButton
          link
          type="primary"
          @click="loadCategories"
        >
          重新加载分类
        </ElButton>
      </p>
      <div class="setmeal-batch-actions">
        <ElButton
          type="danger"
          plain
          :disabled="state.loading || Boolean(state.error) || Boolean(pending)"
          :loading="pending === 'delete:batch'"
          @click="action('delete')"
        >
          批量删除
        </ElButton>
        <span aria-live="polite">已选择 {{ selected.length }} 项</span>
      </div>
      <DataState
        v-if="state.loading"
        kind="loading"
        title="正在加载套餐列表"
        description="请稍候"
      />
      <DataState
        v-else-if="state.error"
        kind="error"
        title="套餐列表加载失败"
        :description="state.error"
      >
        <ElButton @click="list.load">
          重新加载
        </ElButton>
      </DataState>
      <div
        v-else-if="state.records.length"
        class="management-table-region"
        role="region"
        aria-label="套餐列表，可在表格内横向滚动"
        tabindex="0"
      >
        <ElTable
          :data="state.records"
          row-key="id"
          stripe
          @selection-change="select"
        >
          <ElTableColumn
            type="selection"
            width="48"
            :selectable="() => !pending"
          />
          <ElTableColumn
            prop="name"
            label="套餐名称"
            min-width="160"
          />
          <!-- @vue-generic {SetmealRecord} -->
          <ElTableColumn
            label="图片"
            width="112"
          >
            <template #default="{ row }">
              <ElImage
                class="setmeal-thumbnail"
                :src="row.image"
                :alt="`${row.name}图片`"
                fit="cover"
                loading="lazy"
              >
                <template #error>
                  <span class="setmeal-image-empty">暂无图片</span>
                </template>
              </ElImage>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="categoryName"
            label="套餐分类"
            min-width="140"
          />
          <!-- @vue-generic {SetmealRecord} -->
          <ElTableColumn
            label="售价"
            min-width="110"
            align="right"
          >
            <template #default="{ row }">
              <span class="setmeal-price">{{ formatSetmealPrice(row.price) === '—' ? '—' : `￥${formatSetmealPrice(row.price)}` }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn
            label="售卖状态"
            min-width="110"
          >
            <template #default="{ row }">
              <span
                class="management-status"
                :data-enabled="String(row.status) !== '0'"
              >{{ String(row.status) === '0' ? '停售' : '启售' }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="updateTime"
            label="最后操作时间"
            min-width="190"
          />
          <!-- @vue-generic {SetmealRecord} -->
          <ElTableColumn
            label="操作"
            width="220"
          >
            <template #default="{ row }">
              <div class="management-row-actions">
                <ElButton
                  link
                  type="primary"
                  :disabled="Boolean(pending)"
                  :aria-label="`修改${row.name}`"
                  @click="router.push({ path: '/setmeal/add', query: { id: row.id } })"
                >
                  修改
                </ElButton>
                <ElButton
                  link
                  type="danger"
                  :disabled="Boolean(pending)"
                  :loading="pending === `delete:${row.id}`"
                  :aria-label="`删除${row.name}`"
                  @click="action('delete', row)"
                >
                  删除
                </ElButton>
                <ElButton
                  link
                  :type="String(row.status) === '0' ? 'primary' : 'danger'"
                  :disabled="Boolean(pending)"
                  :loading="pending === `status:${row.id}`"
                  :aria-label="`${String(row.status) === '0' ? '启售' : '停售'}${row.name}`"
                  @click="action('status', row)"
                >
                  {{ String(row.status) === '0' ? '启售' : '停售' }}
                </ElButton>
              </div>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
      <DataState
        v-else
        :title="state.isSearch ? '未找到匹配的套餐' : '暂无套餐数据'"
        :description="state.isSearch ? '请调整套餐名称、分类或售卖状态后重新查询。' : '可通过页面上方的新建套餐按钮录入套餐。'"
      >
        <ElButton
          v-if="state.isSearch"
          @click="list.clearFilters"
        >
          清空搜索
        </ElButton>
      </DataState>
      <PaginationBar
        v-if="state.total > 10"
        :page="state.page"
        :page-size="state.pageSize"
        :total="state.total"
        :loading="state.loading || Boolean(pending)"
        @page-change="list.setPage"
        @size-change="list.setPageSize"
      />
    </section>
  </div>
</template>

<style scoped>
.setmeal-filter .management-filter-field { width: min(220px, 100%); }
.setmeal-filter .setmeal-status-filter { width: min(160px, 100%); }
.setmeal-batch-actions { display: flex; align-items: center; flex-wrap: wrap; gap: var(--jz-space-3); padding-bottom: var(--jz-space-4); }
.setmeal-batch-actions span { color: var(--jz-text-secondary); font-size: var(--jz-font-control); }
.setmeal-category-error { display: flex; flex-wrap: wrap; align-items: center; gap: var(--jz-space-2); color: var(--jz-danger); font-size: var(--jz-font-control); }
.setmeal-thumbnail { display: block; width: 80px; height: 48px; border-radius: var(--jz-radius-control); background: var(--jz-page-background); }
.setmeal-image-empty { display: grid; place-items: center; height: 100%; color: var(--jz-text-secondary); font-size: var(--jz-font-caption); }
.setmeal-price { font-variant-numeric: tabular-nums; }
@media (max-width: 767px) { .setmeal-filter .management-filter-field { width: 100%; } .setmeal-filter :deep(.el-select__wrapper) { min-height: var(--jz-control-height-large); } }
</style>
