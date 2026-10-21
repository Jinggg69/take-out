<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElInput, ElMessage, ElMessageBox, ElTable, ElTableColumn } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import DataState from '@/components/DataState.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { employeeApi } from '@/api'
import type { EmployeeId, EmployeeRecord } from '@/api/employee'
import { canManageEmployee, nextEmployeeStatus } from '@/utils/employee'
import { errorMessage } from '@/utils/error-message'
import { createEmployeeList } from './list-state'

const router = useRouter()
const list = createEmployeeList(employeeApi)
const { state } = list
const changingId = ref<EmployeeId | null>(null)
let active = true
onMounted(() => { void list.load() })
onBeforeUnmount(() => { active = false; list.dispose() })
function editEmployee(row: EmployeeRecord) {
  if (canManageEmployee(row)) void router.push({ path: '/employee/add', query: { id: String(row.id) } })
}
async function changeStatus(row: EmployeeRecord) {
  if (!canManageEmployee(row) || changingId.value !== null) return
  const status = nextEmployeeStatus(row.status)
  const action = status === 0 ? '禁用' : '启用'
  changingId.value = row.id
  try {
    await ElMessageBox.confirm(`确认${action}员工“${row.name}”的账号？`, '调整账号状态', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning',
    })
    if (!active) return
    const { data } = await employeeApi.setStatus(row.id, status)
    if (!active) return
    if (String(data.code) !== '1') throw new Error(data.msg || '账号状态更改失败，请重试')
    ElMessage.success('账号状态更改成功')
    await list.load()
  } catch (error) {
    if (active && error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error, '账号状态更改失败，请检查网络后重试'))
  } finally { changingId.value = null }
}
</script>

<template>
  <div class="employee-list-page">
    <PageHeader title="员工管理">
      <ElButton
        type="primary"
        @click="router.push('/employee/add')"
      >
        添加员工
      </ElButton>
    </PageHeader>
    <section
      class="management-panel"
      aria-label="员工查询与列表"
    >
      <form
        class="management-filter"
        @submit.prevent="list.search()"
      >
        <div class="management-filter-field">
          <label for="employee-search">员工姓名</label>
          <ElInput
            id="employee-search"
            v-model="state.name"
            placeholder="请输入员工姓名"
            clearable
            @clear="list.clearSearch"
            @keydown.enter.prevent="list.search(true)"
          />
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
        title="正在加载员工列表"
        description="请稍候"
      />
      <DataState
        v-else-if="state.error"
        kind="error"
        title="员工列表加载失败"
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
        aria-label="员工列表，可在表格内横向滚动"
        tabindex="0"
      >
        <ElTable
          :data="state.records"
          row-key="id"
          stripe
        >
          <ElTableColumn
            prop="name"
            label="员工姓名"
            min-width="140"
          />
          <ElTableColumn
            prop="username"
            label="账号"
            min-width="140"
          />
          <ElTableColumn
            prop="phone"
            label="手机号"
            min-width="150"
          />
          <!-- @vue-generic {EmployeeRecord} -->
          <ElTableColumn
            label="账号状态"
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
            label="最后操作时间"
            min-width="190"
          />
          <!-- @vue-generic {EmployeeRecord} -->
          <ElTableColumn
            label="操作"
            width="160"
          >
            <template #default="{ row }">
              <div class="management-row-actions">
                <ElButton
                  link
                  type="primary"
                  :disabled="!canManageEmployee(row)"
                  :aria-label="canManageEmployee(row) ? `修改${row.name}的员工信息` : '管理员账号不可修改'"
                  @click="editEmployee(row)"
                >
                  修改
                </ElButton>
                <ElButton
                  link
                  :type="String(row.status) === '1' ? 'danger' : 'primary'"
                  :disabled="!canManageEmployee(row) || changingId !== null"
                  :loading="changingId === row.id"
                  :aria-label="canManageEmployee(row) ? `${String(row.status) === '1' ? '禁用' : '启用'}${row.name}的账号` : '管理员账号不可调整状态'"
                  @click="changeStatus(row)"
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
        :title="state.isSearch ? '未找到匹配的员工' : '暂无员工数据'"
        :description="state.isSearch ? '请调整员工姓名，或清空搜索后重新查询。' : '可通过页面右上方的添加员工录入员工信息。'"
      >
        <ElButton
          v-if="state.isSearch"
          @click="list.clearSearch"
        >
          清空搜索
        </ElButton>
      </DataState>
      <PaginationBar
        :page="state.page"
        :page-size="state.pageSize"
        :total="state.total"
        :loading="state.loading"
        @page-change="list.setPage"
        @size-change="list.setPageSize"
      />
    </section>
  </div>
</template>
