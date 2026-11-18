<script setup lang="ts">
import axios from 'axios'
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElDatePicker, ElInput, ElOption, ElSelect } from 'element-plus'
import { pointsApi } from '@/api'
import type { MemberPage } from '@/api/member'
import { ledgerExportError, pointsSources, type PointsLedgerFilters } from '@/api/points'
import DataState from '@/components/DataState.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { workspace } from '@/stores/workspace'
import MemberTable from '@/views/member/MemberTable.vue'
import { pointColumns, unwrap, useMemberLoad } from '@/views/member/shared'
import { errorMessage } from '@/utils/error-message'
import { exportWorkbook } from '@/utils/statistics'

function defaultDates(): [string, string] {
  const today = new Date(Date.now() + 8 * 3_600_000)
  const start = new Date(today.getTime() - 29 * 86_400_000)
  return [start.toISOString().slice(0, 10), today.toISOString().slice(0, 10)]
}

const dates = ref<[string, string] | null>(defaultDates())
const draft = ref<PointsLedgerFilters>({})
const applied = ref<PointsLedgerFilters>({ startDate: dates.value?.[0], endDate: dates.value?.[1] })
const page = ref(1), pageSize = ref(10)
const exporting = ref(false), exportError = ref('')
const { value: result, loading, error, load } = useMemberLoad<MemberPage>(
  () => ({ records: [], total: 0 }),
  async () => unwrap(await pointsApi.ledger({ ...applied.value, page: page.value, pageSize: pageSize.value })),
)
const columns = [pointColumns[0]!, { key: 'memberName', label: '会员姓名' }, { key: 'source', label: '来源', labels: pointsSources }, ...pointColumns.slice(1)]

function search() {
  exportError.value = ''
  applied.value = {
    ...draft.value,
    keyword: draft.value.keyword?.trim() || undefined,
    businessNo: draft.value.businessNo?.trim() || undefined,
    startDate: dates.value?.[0], endDate: dates.value?.[1],
  }
  page.value = 1
  void load()
}

function reset() {
  dates.value = defaultDates()
  draft.value = {}
  search()
}

async function download() {
  if (exporting.value || !workspace.canTenant('member:point:export')) return
  exportError.value = ledgerExportError(applied.value)
  if (exportError.value) return
  if (result.value.total > 10000) { exportError.value = '当前筛选超过 10,000 条，请缩小筛选范围'; return }
  exporting.value = true
  const revision = workspace.revision.value
  try {
    const blob = await exportWorkbook((await pointsApi.export(applied.value)).data)
    if (revision !== workspace.revision.value) return
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    try { anchor.href = url; anchor.download = '积分流水.xlsx'; document.body.appendChild(anchor); anchor.click() }
    finally { anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
  } catch (cause) {
    if (axios.isAxiosError(cause) && cause.response?.data instanceof Blob) {
      try { await exportWorkbook(cause.response.data) }
      catch (blobError) { exportError.value = errorMessage(blobError, '流水导出失败'); return }
    }
    exportError.value = errorMessage(cause, '流水导出失败')
  } finally { exporting.value = false }
}

onMounted(load)
</script>

<template>
  <section class="management-panel">
    <form
      class="management-filter points-filters"
      @submit.prevent="search"
    >
      <ElDatePicker
        v-model="dates"
        aria-label="流水日期"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
      />
      <ElInput
        v-model="draft.keyword"
        aria-label="流水会员关键词"
        placeholder="会员号 / 姓名 / 手机号"
        clearable
      />
      <ElInput
        v-model="draft.businessNo"
        aria-label="流水业务号"
        placeholder="业务号"
        clearable
      />
      <ElSelect
        v-model="draft.storeId"
        aria-label="流水门店"
        placeholder="全部门店"
        clearable
        :value-on-clear="undefined"
      >
        <ElOption
          v-for="store in workspace.stores.value"
          :key="store.id"
          :label="store.name"
          :value="store.id"
        />
      </ElSelect>
      <ElSelect
        v-model="draft.source"
        aria-label="积分来源"
        placeholder="全部来源"
        clearable
        :value-on-clear="undefined"
      >
        <ElOption
          v-for="(label, source) in pointsSources"
          :key="source"
          :label="label"
          :value="source"
        />
      </ElSelect>
      <ElSelect
        v-model="draft.assetType"
        aria-label="积分类型"
        placeholder="全部类型"
        clearable
        :value-on-clear="undefined"
      >
        <ElOption
          label="积分"
          value="POINTS"
        />
        <ElOption
          label="待扣积分"
          value="POINTS_DEBT"
        />
      </ElSelect>
      <ElButton
        native-type="submit"
        :loading="loading"
      >
        查询
      </ElButton>
      <ElButton @click="reset">
        重置
      </ElButton>
    </form>
    <div
      v-if="workspace.canTenant('member:point:export')"
      class="points-actions"
    >
      <ElButton
        :disabled="loading"
        :loading="exporting"
        @click="download"
      >
        导出流水
      </ElButton>
      <span class="points-note">按已查询的条件导出，单次最多 31 天、10,000 条。</span>
    </div>
    <ElAlert
      v-if="exportError"
      :title="exportError"
      type="error"
      :closable="false"
    />
    <DataState
      v-if="loading"
      kind="loading"
      title="正在加载积分流水"
    />
    <DataState
      v-else-if="error"
      kind="error"
      title="积分流水加载失败"
      :description="error"
    >
      <ElButton @click="load">
        重新加载
      </ElButton>
    </DataState>
    <MemberTable
      v-else-if="result.records.length"
      :rows="result.records"
      :columns="columns"
    />
    <DataState
      v-else
      title="暂无积分流水"
      description="可调整筛选条件后重新查询。"
    />
    <PaginationBar
      :page="page"
      :page-size="pageSize"
      :total="result.total"
      :loading="loading"
      @page-change="page = $event; load()"
      @size-change="pageSize = $event; page = 1; load()"
    />
  </section>
</template>
