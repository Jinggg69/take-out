<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElButton, ElInput, ElMessage, ElMessageBox, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { categoryApi } from '@/api'
import type { CategoryEditor, CategoryId, CategoryRecord } from '@/api/category'
import { categoryResultMessage, categoryTypeLabel, nextCategoryStatus } from '@/utils/category'
import { errorMessage } from '@/utils/error-message'
import { createCategoryList } from './list-state'
import CategoryDialog from './CategoryDialog.vue'

const list = createCategoryList(categoryApi)
const { state } = list
const dialogVisible = ref(false)
const editor = ref<CategoryEditor | null>(null)
const pendingId = ref<CategoryId | null>(null)
const pendingKind = ref<'delete' | 'status' | null>(null)
let active = true
onMounted(() => { void list.load() })
onBeforeUnmount(() => { active = false; list.dispose() })
function addCategory(type: '1' | '2') { editor.value = { mode: 'add', type }; dialogVisible.value = true }
function editCategory(row: CategoryRecord) { editor.value = { mode: 'edit', record: { ...row } }; dialogVisible.value = true }
async function rowAction(row: CategoryRecord, kind: 'delete' | 'status') {
  if (pendingId.value !== null) return
  const status = nextCategoryStatus(row.status)
  const action = kind === 'delete' ? '删除' : status === 0 ? '禁用' : '启用'
  pendingId.value = row.id; pendingKind.value = kind
  try {
    await ElMessageBox.confirm(kind === 'delete'
      ? `此操作将永久删除分类“${row.name}”，是否继续？`
      : `确认${action}分类“${row.name}”？`, kind === 'delete' ? '确定删除' : '调整分类状态', {
      confirmButtonText: kind === 'delete' ? '删除' : '确定', cancelButtonText: '取消', type: 'warning',
    })
    if (!active) return
    const { data } = await (kind === 'delete' ? categoryApi.remove(row.id) : categoryApi.setStatus(row.id, status))
    if (!active) return
    if (String(data.code) !== '1') throw new Error(data.msg || categoryResultMessage(data, `分类${action}失败，请重试`))
    ElMessage.success(kind === 'delete' ? '分类删除成功' : '分类状态更改成功')
    await list.load()
  } catch (error) {
    if (active && error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error, `分类${action}失败，请检查网络后重试`))
  } finally { pendingId.value = null; pendingKind.value = null }
}
</script>

<template>
  <div class="category-page">
    <PageHeader
      title="分类管理"
      class="category-page-header"
    >
      <div class="category-create-actions">
        <ElButton
          type="primary"
          :disabled="pendingId !== null"
          @click="addCategory('1')"
        >
          新增菜品分类
        </ElButton>
        <ElButton
          :disabled="pendingId !== null"
          @click="addCategory('2')"
        >
          新增套餐分类
        </ElButton>
      </div>
    </PageHeader>
    <section
      class="management-panel"
      aria-label="分类查询与列表"
    >
      <form
        class="management-filter"
        @submit.prevent="list.search"
      >
        <div class="management-filter-field">
          <label for="category-search-name">分类名称</label>
          <ElInput
            id="category-search-name"
            v-model="state.name"
            placeholder="请填写分类名称"
            clearable
            @clear="list.clearName"
            @keydown.enter.prevent="list.search"
          />
        </div>
        <div class="management-filter-field category-type-filter">
          <label for="category-search-type">分类类型</label>
          <ElSelect
            id="category-search-type"
            v-model="state.type"
            placeholder="请选择"
            clearable
            @clear="list.clearType"
          >
            <ElOption
              label="菜品分类"
              :value="1"
            />
            <ElOption
              label="套餐分类"
              :value="2"
            />
          </ElSelect>
        </div>
        <ElButton
          native-type="submit"
          :loading="state.loading"
        >
          查询
        </ElButton>
      </form>
      <DataState
        v-if="state.loading"
        kind="loading"
        title="正在加载分类列表"
        description="请稍候"
      />
      <DataState
        v-else-if="state.error"
        kind="error"
        title="分类列表加载失败"
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
        aria-label="分类列表，可在表格内横向滚动"
        tabindex="0"
      >
        <ElTable
          :data="state.records"
          row-key="id"
          stripe
        >
          <ElTableColumn
            prop="name"
            label="分类名称"
            min-width="180"
          />
          <!-- @vue-generic {CategoryRecord} -->
          <ElTableColumn
            label="分类类型"
            min-width="130"
          >
            <template #default="{ row }">
              {{ categoryTypeLabel(row.type) }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="sort"
            label="排序"
            min-width="90"
            align="right"
            class-name="category-sort-column"
          />
          <ElTableColumn
            label="状态"
            min-width="110"
          >
            <template #default="{ row }">
              <span
                class="management-status"
                :data-enabled="String(row.status) !== '0'"
              >{{ String(row.status) === '0' ? '禁用' : '启用' }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="updateTime"
            label="操作时间"
            min-width="190"
          />
          <!-- @vue-generic {CategoryRecord} -->
          <ElTableColumn
            label="操作"
            width="220"
          >
            <template #default="{ row }">
              <div class="management-row-actions">
                <ElButton
                  link
                  type="primary"
                  :disabled="pendingId !== null"
                  :aria-label="`修改${row.name}分类`"
                  @click="editCategory(row)"
                >
                  修改
                </ElButton>
                <ElButton
                  link
                  type="danger"
                  :disabled="pendingId !== null"
                  :loading="pendingId === row.id && pendingKind === 'delete'"
                  :aria-label="`删除${row.name}分类`"
                  @click="rowAction(row, 'delete')"
                >
                  删除
                </ElButton>
                <ElButton
                  link
                  :type="String(row.status) === '1' ? 'danger' : 'primary'"
                  :disabled="pendingId !== null"
                  :loading="pendingId === row.id && pendingKind === 'status'"
                  :aria-label="`${String(row.status) === '1' ? '禁用' : '启用'}${row.name}分类`"
                  @click="rowAction(row, 'status')"
                >
                  {{ String(row.status) === '1' ? '禁用' : '启用' }}
                </ElButton>
              </div>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
      <DataState
        v-else
        :title="state.isSearch ? '未找到匹配的分类' : '暂无分类数据'"
        :description="state.isSearch ? '请调整分类名称或类型，或清空搜索后重新查询。' : '可通过页面上方的新增按钮录入菜品分类或套餐分类。'"
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
        :loading="state.loading"
        @page-change="list.setPage"
        @size-change="list.setPageSize"
      />
    </section>
    <CategoryDialog
      v-model="dialogVisible"
      :editor="editor"
      @saved="list.load"
    />
  </div>
</template>

<style scoped>
.category-page-header { flex-wrap: wrap; align-items: flex-start; }
.category-create-actions { display: flex; flex-wrap: wrap; gap: var(--jz-space-3); }
.category-create-actions .el-button { margin-left: 0; }
.category-type-filter { width: min(220px, 100%); }
:deep(.category-sort-column) { font-variant-numeric: tabular-nums; }
@media (max-width: 767px) {
  .category-create-actions .el-button { min-height: var(--jz-control-height-large); }
  .category-type-filter { width: 100%; }
  .category-type-filter :deep(.el-select__wrapper) { min-height: var(--jz-control-height-large); }
}
</style>
