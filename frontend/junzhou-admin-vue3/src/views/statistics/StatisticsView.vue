<script setup lang="ts">
import { workspace } from '@/stores/workspace'
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElButton, ElMessage, ElSelect, ElOption } from 'element-plus'
import { statisticsApi } from '@/api'
import { chartRows, exportWorkbook, presetRange, rankingRows, type StatisticsPreset } from '@/utils/statistics'
import { createStatisticsState } from './state'
import ReportChart from './ReportChart.vue'
const presets: { key: StatisticsPreset; label: string }[] = [{ key: 'yesterday', label: '昨日' }, { key: '7', label: '近7日' }, { key: '30', label: '近30日' }, { key: 'week', label: '本周' }, { key: 'month', label: '本月' }]
const selected = ref<StatisticsPreset>('7')
const range = ref(presetRange('7'))
const reports = createStatisticsState(statisticsApi)
const { state } = reports
const exporting = ref(false)
const selectedStores = ref<string[]>(workspace.storeId.value ? [workspace.storeId.value] : [])
const reportStores = computed(() => workspace.stores.value.filter(s => s.permissions.includes('report:read')))
const canExport = computed(() => selectedStores.value.length > 0 && selectedStores.value.every(id => reportStores.value.find(s => s.id === id)?.permissions.includes('report:export')))
function load() { if (selectedStores.value.length) void reports.load({ ...range.value, storeIds: selectedStores.value.join(',') }); else reports.clear() }
let mounted = true
const turnoverRows = computed(() => chartRows(state.turnover.data?.dateList, [state.turnover.data?.turnoverList, state.turnover.data?.refundList, state.turnover.data?.netList]))
const userRows = computed(() => chartRows(state.users.data?.dateList, [state.users.data?.totalUserList, state.users.data?.newUserList]))
const orderRows = computed(() => chartRows(state.orders.data?.dateList, [state.orders.data?.orderCountList, state.orders.data?.paidOrderCountList, state.orders.data?.validOrderCountList, state.orders.data?.cancelledOrderCountList]))
const topRows = computed(() => rankingRows(state.top10.data?.nameList, state.top10.data?.numberList))
const sections = computed(() => [
  { key: 'turnover' as const, title: '收款与退款', report: state.turnover, rows: turnoverRows.value, series: ['实收', '确认退款', '净收款'], unit: '元', money: true, bars: false },
  { key: 'users' as const, title: '下单顾客统计', report: state.users, rows: userRows.value, series: ['累计下单顾客', '首次下单顾客'], unit: '个', money: false, bars: false },
  { key: 'orders' as const, title: '订单统计', report: state.orders, rows: orderRows.value, series: ['下单', '支付', '完成', '取消'], unit: '个', money: false, bars: false },
  { key: 'top10' as const, title: '销量排名TOP10', report: state.top10, rows: topRows.value, series: ['销量'], unit: '份', money: false, bars: true },
])
function choose(key: StatisticsPreset) { selected.value = key; range.value = presetRange(key); load() }
function number(value: unknown) { return value !== null && value !== undefined && Number.isFinite(Number(value)) ? String(value) : '—' }
function rate(value: unknown) { return value !== null && value !== undefined && Number.isFinite(Number(value)) ? `${(Number(value) * 100).toFixed(1)}%` : '—' }
async function exportData() {
  if (exporting.value || !canExport.value) return
  exporting.value = true
  try {
    const selection = { ...range.value, storeIds: selectedStores.value.join(',') }
    const response = await statisticsApi.export(selection)
    const blob = await exportWorkbook(response.data)
    if (!mounted) return
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    try { anchor.href = url; anchor.download = '运营数据统计报表.xlsx'; document.body.appendChild(anchor); anchor.click() }
    finally { anchor.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000) }
    ElMessage.success('运营数据统计报表已导出')
  } catch (error) { if (mounted && error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '数据导出失败，请重试') }
  finally { exporting.value = false }
}
load()
onBeforeUnmount(() => { mounted = false; reports.dispose() })
</script>

<template>
  <main
    class="statistics-page"
    data-testid="statistics-page"
  >
    <header class="statistics-heading">
      <h1>数据统计</h1><el-button
        v-if="canExport"
        data-testid="statistics-export"
        :loading="exporting"
        @click="exportData"
      >
        数据导出
      </el-button>
    </header>
    <section
      class="statistics-filters"
      aria-label="统计时间"
    >
      <ElSelect
        v-model="selectedStores"
        multiple
        aria-label="统计门店"
        placeholder="请选择统计门店"
        :disabled="exporting"
        @change="load"
      >
        <ElOption
          v-for="store in reportStores"
          :key="store.id"
          :label="store.name"
          :value="store.id"
        />
      </ElSelect>
      <div
        class="preset-list"
        role="group"
        aria-label="日期范围"
      >
        <el-button
          v-for="preset in presets"
          :key="preset.key"
          :data-testid="`statistics-preset-${preset.key}`"
          :disabled="exporting"
          :type="selected === preset.key ? 'primary' : 'default'"
          :aria-pressed="selected === preset.key"
          @click="choose(preset.key)"
        >
          {{ preset.label }}
        </el-button>
      </div>
      <p data-testid="statistics-range">
        已选时间：{{ range.begin }} 至 {{ range.end }}
      </p>
      <p class="export-note">
        上海自然日；实收按支付成功日，退款按本地首次确认成功日，净收款为两者之差。导出与所选门店、日期一致。销量扣除截至所选结束日已确认的全额退款；顾客按所选门店首次下单统计。
      </p>
    </section>
    <p
      v-if="!selectedStores.length"
      role="status"
    >
      请选择至少一家统计门店。
    </p>
    <div
      v-else
      class="statistics-grid"
    >
      <section
        v-for="section in sections"
        :key="section.key"
        class="statistics-section"
        :data-testid="`statistics-${section.key}`"
        :aria-labelledby="`statistics-title-${section.key}`"
        :aria-busy="section.report.loading"
      >
        <h2 :id="`statistics-title-${section.key}`">
          {{ section.title }}
        </h2>
        <p
          v-if="section.report.loading"
          class="report-message"
          role="status"
        >
          正在加载{{ section.title }}…
        </p>
        <div
          v-else-if="section.report.error"
          class="report-message report-error"
          role="alert"
        >
          <p>{{ section.report.error }}</p><el-button
            :data-testid="`statistics-retry-${section.key}`"
            @click="reports.retry(section.key)"
          >
            重试
          </el-button>
        </div>
        <template v-else>
          <dl
            v-if="section.key === 'orders' && state.orders.data"
            class="order-summary"
            data-testid="statistics-order-summary"
          >
            <div><dt>所选期间下单的当前完成率</dt><dd>{{ rate(state.orders.data.orderCompletionRate) }}</dd></div><div><dt>完成事件</dt><dd>{{ number(state.orders.data.validOrderCount) }} 个</dd></div><div><dt>下单数</dt><dd>{{ number(state.orders.data.totalOrderCount) }} 个</dd></div>
          </dl>
          <ReportChart
            :title="section.title"
            :rows="section.rows"
            :series="section.series"
            :unit="section.unit"
            :money="section.money"
            :bars="section.bars"
          />
        </template>
      </section>
    </div>
  </main>
</template>

<style scoped lang="scss">
.statistics-page { min-width: 0; }
.statistics-heading { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: var(--jz-space-4); margin-bottom: var(--jz-space-6); }
h1 { font-size: var(--jz-font-title); margin: 0; }
h2 { font-size: var(--jz-font-section); margin: 0 0 var(--jz-space-4); }
.statistics-filters { background: var(--jz-surface); padding: var(--jz-space-4); margin-bottom: var(--jz-space-6); border: 1px solid var(--jz-border); border-radius: var(--jz-radius-panel); }
.preset-list { display: flex; flex-wrap: wrap; gap: var(--jz-space-2); }
.preset-list .el-button { margin-left: 0; }
.statistics-filters p { margin: var(--jz-space-3) 0 0; font-size: var(--jz-font-control); }
.export-note { color: var(--jz-text-secondary); }
.statistics-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--jz-space-6); }
.statistics-section { min-width: 0; padding: var(--jz-space-6); background: var(--jz-surface); border: 1px solid var(--jz-border); border-radius: var(--jz-radius-panel); }
.report-message { padding: var(--jz-space-8) var(--jz-space-4); text-align: center; color: var(--jz-text-secondary); }
.report-error p { color: var(--jz-danger); overflow-wrap: anywhere; }
.order-summary { display: flex; flex-wrap: wrap; gap: var(--jz-space-6); margin: 0 0 var(--jz-space-4); font-size: var(--jz-font-control); }
.order-summary dt { color: var(--jz-text-secondary); }
.order-summary dd { margin: var(--jz-space-2) 0 0; font-size: var(--jz-font-section); font-variant-numeric: tabular-nums; }
@media (max-width: 1199px) { .statistics-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 767px) { .statistics-section { padding: var(--jz-space-4); } .statistics-grid { gap: var(--jz-space-4); } }
</style>

