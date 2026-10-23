<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElButton, ElCheckbox, ElDatePicker, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElSelect, ElTable, ElTableColumn, ElTag } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import DataState from '@/components/DataState.vue'
import FormFeedback from '@/components/FormFeedback.vue'
import { orderApi as api } from '@/api'
import type { OrderAction, OrderId, OrderRecord, OrderStatistics } from '@/api/order'
import { actionLabels, cancellationReasons, orderActions, orderMoney, orderReason, orderStatusLabel, orderTabs, rejectionReasons, shouldOpenNextOrder } from '@/utils/order'
import { errorMessage } from '@/utils/error-message'
import { createOrderList } from './list-state'

const list = createOrderList(api)
const { state } = list
const route = useRoute(); const router = useRouter()
const statistics = ref<OrderStatistics | null>(null)
const statisticsError = ref('')
const detailVisible = ref(false); const detail = ref<OrderRecord | null>(null)
const detailLoading = ref(false); const detailError = ref(''); const detailId = ref<OrderId>('')
const autoNext = ref(true); const busy = ref(false)
const actionError = ref(''); const actionFeedback = ref<InstanceType<typeof FormFeedback>>(); const listFeedback = ref<InstanceType<typeof FormFeedback>>()
const reasonVisible = ref(false); const reasonKind = ref<'reject' | 'cancel'>('reject')
const reasonRow = ref<OrderRecord | null>(null); const reasonFromDetail = ref(false)
const selectedReason = ref(''); const customReason = ref(''); const reasonError = ref('')
const reasonFeedback = ref<InstanceType<typeof FormFeedback>>()
const reasonTitle = computed(() => reasonKind.value === 'cancel' ? '取消' : '拒绝')
const reasons = computed(() => reasonKind.value === 'cancel' ? cancellationReasons : rejectionReasons)
let alive = true; let detailSerial = 0; let statisticsSerial = 0

function count(status: number) {
  const key = ({ 2: 'toBeConfirmed', 3: 'confirmed', 4: 'deliveryInProgress' } as const)[status as 2 | 3 | 4]
  return key && statistics.value ? Number(statistics.value[key]) || 0 : 0
}
async function loadStatistics() {
  const serial = ++statisticsSerial
  try {
    const { data } = await api.getStatistics()
    if (!alive || serial !== statisticsSerial) return
    if (String(data.code) !== '1' || !data.data) throw new Error(data.msg || '订单数量读取失败')
    statistics.value = data.data; statisticsError.value = ''
  } catch (error) { if (alive && serial === statisticsSerial) statisticsError.value = errorMessage(error, '订单数量读取失败') }
}
async function refresh() { await Promise.all([list.load(), loadStatistics()]) }
async function changeTab(status: number) {
  if (busy.value || state.status === status) return
  await Promise.all([list.setStatus(status), loadStatistics()])
  if (route.query.status || route.query.orderId) await router.replace({ path: '/order' })
}
function closeDetail() {
  if (busy.value) return
  detailSerial++; detailVisible.value = false; detailLoading.value = false; actionError.value = ''
}
async function openDetail(id: OrderId) {
  if (busy.value) return
  const serial = ++detailSerial
  detailId.value = id; detailVisible.value = true; detailLoading.value = true
  detail.value = null; detailError.value = ''; actionError.value = ''
  try {
    const { data } = await api.getDetail(id)
    if (!alive || serial !== detailSerial) return
    if (String(data.code) !== '1' || !data.data || data.data.id == null) throw new Error(data.msg || '订单详情数据异常')
    detail.value = data.data
  } catch (error) { if (alive && serial === detailSerial) detailError.value = errorMessage(error, '订单详情读取失败，请重试') }
  finally { if (alive && serial === detailSerial) detailLoading.value = false }
}
function openReason(row: OrderRecord, kind: 'reject' | 'cancel', fromDetail: boolean) {
  reasonKind.value = kind; reasonRow.value = row; reasonFromDetail.value = fromDetail
  selectedReason.value = ''; customReason.value = ''; reasonError.value = ''; reasonVisible.value = true
}
async function perform(row: OrderRecord, action: OrderAction, fromDetail = false, reason?: string) {
  if (busy.value || !orderActions(row.status).includes(action)) return
  if ((action === 'reject' || action === 'cancel') && reason === undefined) { openReason(row, action, fromDetail); return }
  busy.value = true; actionError.value = ''; reasonError.value = ''
  try {
    const { data } = await (action === 'reject' ? api.reject({ id: row.id, rejectionReason: reason! }) : action === 'cancel' ? api.cancel({ id: row.id, cancelReason: reason! }) : api[action](row.id))
    if (!alive) return
    if (String(data.code) !== '1') throw new Error(data.msg || `${actionLabels[action]}失败，请重试`)
    ElMessage.success('操作成功'); reasonVisible.value = false
    if (fromDetail) { detailVisible.value = false; detailSerial++ }
    await refresh()
    if (!alive) return
    busy.value = false
    if (!state.error && shouldOpenNextOrder(autoNext.value, fromDetail, state.status, Number(row.status), state.records.length)) {
      const next = state.records.find(record => String(record.id) !== String(row.id))
      if (next) await openDetail(next.id)
    }
  } catch (error) {
    if (!alive) return
    const message = errorMessage(error, `${actionLabels[action]}失败，请检查网络后重试`)
    if (reasonVisible.value) { reasonError.value = message; await nextTick(); reasonFeedback.value?.focus() }
    else { actionError.value = message; await nextTick(); (detailVisible.value ? actionFeedback.value : listFeedback.value)?.focus() }
  } finally { if (alive) busy.value = false }
}
async function confirmReason() {
  if (!reasonRow.value || busy.value) return
  try { const reason = orderReason(selectedReason.value, customReason.value, reasonTitle.value); await perform(reasonRow.value, reasonKind.value, reasonFromDetail.value, reason) }
  catch (error) { reasonError.value = errorMessage(error, '请选择原因'); await nextTick(); reasonFeedback.value?.focus() }
}
watch(() => route.query.status, value => {
  const status = Number(Array.isArray(value) ? value[0] : value)
  if (value != null) void list.setStatus([0, 1, 2, 3, 4, 5, 6].includes(status) ? status : 0)
}, { immediate: true })
watch(() => [route.query.orderId, busy.value] as const, ([value, isBusy]) => {
  if (isBusy) return
  const id = Array.isArray(value) ? value[0] : value
  if (id && id !== 'undefined') {
    void openDetail(id)
    void router.replace({ path: '/order', query: { ...route.query, orderId: undefined } })
  }
}, { immediate: true })
if (route.query.status == null) void list.load()
void loadStatistics()
onBeforeRouteLeave(() => !busy.value)
onBeforeUnmount(() => { alive = false; detailSerial++; statisticsSerial++; list.dispose() })
</script>

<template>
  <div class="order-page">
    <PageHeader title="订单管理">
      <ElButton
        :disabled="busy"
        @click="refresh"
      >
        刷新订单
      </ElButton>
    </PageHeader>
    <nav
      class="order-tabs"
      aria-label="订单状态"
    >
      <button
        v-for="tab in orderTabs"
        :key="tab.value"
        type="button"
        :aria-pressed="state.status === tab.value"
        :disabled="busy"
        @click="changeTab(tab.value)"
      >
        {{ tab.label }}<span
          v-if="[2, 3, 4].includes(tab.value) && count(tab.value)"
          class="order-tab-count"
        >{{ count(tab.value) > 99 ? '99+' : count(tab.value) }}</span>
      </button>
    </nav>
    <div
      v-if="statisticsError"
      class="order-statistics-error"
      role="alert"
    >
      {{ statisticsError }} <ElButton
        link
        type="primary"
        @click="loadStatistics"
      >
        重试数量统计
      </ElButton>
    </div>
    <section
      class="management-panel"
      aria-label="订单查询与列表"
    >
      <form
        class="management-filter order-filter"
        @submit.prevent="list.load"
      >
        <div class="management-filter-field">
          <label for="order-search-number">订单号</label>
          <ElInput
            id="order-search-number"
            v-model="state.number"
            clearable
            placeholder="请填写订单号"
            :disabled="busy"
            @clear="list.load"
            @keydown.enter.prevent="list.search"
          />
        </div>
        <div class="management-filter-field">
          <label for="order-search-phone">手机号</label>
          <ElInput
            id="order-search-phone"
            v-model="state.phone"
            clearable
            placeholder="请填写手机号"
            :disabled="busy"
            @clear="list.load"
            @keydown.enter.prevent="list.search"
          />
        </div>
        <div class="management-filter-field order-date-filter">
          <label for="order-search-dates">下单时间</label>
          <ElDatePicker
            id="order-search-dates"
            v-model="state.dates"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :disabled="busy"
            clearable
            @clear="list.load"
          />
        </div>
        <ElButton
          native-type="submit"
          :loading="state.loading"
          :disabled="busy"
          @click="state.isSearch = true"
        >
          查询
        </ElButton>
      </form>
      <FormFeedback
        ref="listFeedback"
        :message="!detailVisible ? actionError : ''"
      />
      <DataState
        v-if="state.loading"
        kind="loading"
        title="正在加载订单列表"
        description="请稍候"
      />
      <DataState
        v-else-if="state.error"
        kind="error"
        title="订单列表加载失败"
        :description="state.error"
      >
        <ElButton @click="refresh">
          重新加载
        </ElButton>
      </DataState>
      <div
        v-else-if="state.records.length"
        class="management-table-region"
        role="region"
        aria-label="订单列表，可在表格内横向滚动"
        tabindex="0"
      >
        <ElTable
          :data="state.records"
          row-key="id"
          stripe
        >
          <ElTableColumn
            prop="number"
            label="订单号"
            min-width="190"
          />
          <ElTableColumn
            v-if="[2, 3, 4].includes(state.status)"
            prop="orderDishes"
            label="订单菜品"
            min-width="190"
          />
          <ElTableColumn
            v-if="state.status === 0"
            label="订单状态"
            min-width="100"
          >
            <template #default="{ row }">
              <ElTag :type="Number(row.status) === 6 ? 'danger' : Number(row.status) === 5 ? 'success' : 'warning'">
                {{ orderStatusLabel(row.status) }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn
            v-if="[0, 5, 6].includes(state.status)"
            prop="consignee"
            label="用户名"
            min-width="100"
            show-overflow-tooltip
          />
          <ElTableColumn
            v-if="[0, 5, 6].includes(state.status)"
            prop="phone"
            label="手机号"
            min-width="140"
          />
          <ElTableColumn
            prop="address"
            label="地址"
            min-width="220"
          />
          <ElTableColumn
            v-if="[0, 6].includes(state.status)"
            prop="orderTime"
            label="下单时间"
            min-width="180"
          />
          <ElTableColumn
            v-if="state.status === 6"
            prop="cancelTime"
            label="取消时间"
            min-width="180"
          />
          <ElTableColumn
            v-if="state.status === 6"
            label="取消原因"
            min-width="180"
          >
            <template #default="{ row }">
              {{ row.cancelReason || row.rejectionReason || '—' }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            v-if="state.status === 5"
            prop="deliveryTime"
            label="送达时间"
            min-width="180"
          />
          <ElTableColumn
            v-if="[2, 3, 4].includes(state.status)"
            prop="estimatedDeliveryTime"
            label="预计送达时间"
            min-width="180"
          />
          <ElTableColumn
            v-if="[0, 2, 5].includes(state.status)"
            label="实收金额"
            min-width="110"
            align="right"
            class-name="order-numeric"
          >
            <template #default="{ row }">
              ￥{{ orderMoney(row.amount) }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            v-if="[2, 3, 4, 5].includes(state.status)"
            prop="remark"
            label="备注"
            min-width="160"
          />
          <ElTableColumn
            v-if="[2, 3, 4].includes(state.status)"
            prop="tablewareNumber"
            label="餐具数量"
            min-width="100"
            align="right"
          />
          <!-- @vue-generic {OrderRecord} -->
          <ElTableColumn
            label="操作"
            width="210"
          >
            <template #default="{ row }">
              <div class="management-row-actions">
                <ElButton
                  v-for="action in orderActions(row.status)"
                  :key="action"
                  link
                  :type="['cancel', 'reject'].includes(action) ? 'danger' : 'primary'"
                  :disabled="busy"
                  :aria-label="`${actionLabels[action]}订单${row.number}`"
                  @click="perform(row, action)"
                >
                  {{ actionLabels[action] }}
                </ElButton>
                <ElButton
                  link
                  type="primary"
                  :disabled="busy"
                  :aria-label="`查看订单${row.number}`"
                  @click="openDetail(row.id)"
                >
                  查看
                </ElButton>
              </div>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
      <DataState
        v-else
        :title="state.isSearch ? '未找到匹配的订单' : '暂无订单'"
        :description="state.isSearch ? '请调整订单号、手机号或下单时间后重试。' : '新订单会显示在这里，可切换状态查看其他订单。'"
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
        :loading="state.loading || busy"
        @page-change="list.setPage"
        @size-change="list.setPageSize"
      />
    </section>

    <ElDialog
      :model-value="detailVisible"
      title="订单信息"
      width="800px"
      class="order-detail-dialog"
      :close-on-click-modal="!busy"
      :close-on-press-escape="!busy"
      :show-close="!busy"
      @update:model-value="closeDetail"
    >
      <DataState
        v-if="detailLoading"
        kind="loading"
        title="正在读取订单详情"
      />
      <DataState
        v-else-if="detailError"
        kind="error"
        title="订单详情读取失败"
        :description="detailError"
      >
        <ElButton @click="openDetail(detailId)">
          重新加载
        </ElButton>
      </DataState>
      <template v-else-if="detail">
        <FormFeedback
          ref="actionFeedback"
          :message="actionError"
        />
        <div class="order-detail-heading">
          <strong>订单号：{{ detail.number }}</strong><ElTag>{{ orderStatusLabel(detail.status) }}</ElTag>
        </div>
        <dl class="order-detail-grid">
          <dt>下单时间</dt><dd>{{ detail.orderTime || '—' }}</dd>
          <dt>用户名</dt><dd>{{ detail.consignee || detail.userName || '—' }}</dd>
          <dt>手机号</dt><dd>{{ detail.phone || '—' }}</dd>
          <dt>地址</dt><dd>{{ detail.address || '—' }}</dd>
          <template v-if="[2, 3, 4, 5].includes(Number(detail.status))">
            <dt>{{ Number(detail.status) === 5 ? '送达时间' : '预计送达时间' }}</dt><dd>{{ (Number(detail.status) === 5 ? detail.deliveryTime : detail.estimatedDeliveryTime) || '—' }}</dd>
          </template>
          <template v-if="Number(detail.status) === 6">
            <dt>取消时间</dt><dd>{{ detail.cancelTime || '—' }}</dd><dt>取消原因</dt><dd>{{ detail.cancelReason || detail.rejectionReason || '—' }}</dd>
          </template>
          <dt>备注</dt><dd>{{ detail.remark || '无' }}</dd>
          <dt>餐具数量</dt><dd>{{ detail.tablewareStatus === 1 ? '按餐量提供' : detail.tablewareNumber ?? '—' }}</dd>
        </dl>
        <h3>订单菜品</h3>
        <ElTable
          :data="detail.orderDetailList || []"
          class="order-detail-items"
        >
          <ElTableColumn
            label="菜品"
            min-width="180"
          >
            <template #default="{ row }">
              {{ row.name }}<small v-if="row.dishFlavor">（{{ row.dishFlavor }}）</small>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="number"
            label="数量"
            width="70"
            align="right"
          />
          <ElTableColumn
            label="金额"
            width="110"
            align="right"
          >
            <template #default="{ row }">
              ￥{{ orderMoney(row.amount) }}
            </template>
          </ElTableColumn>
        </ElTable>
        <h3>费用</h3>
        <dl class="order-detail-grid order-bill">
          <dt>菜品小计</dt><dd>￥{{ orderMoney(Number(detail.amount) - 6 - Number(detail.packAmount || 0)) }}</dd>
          <dt>派送费</dt><dd>￥6.00</dd>
          <dt>打包费</dt><dd>￥{{ orderMoney(detail.packAmount) }}</dd>
          <dt>合计</dt><dd><strong>￥{{ orderMoney(detail.amount) }}</strong></dd>
          <dt>支付渠道</dt><dd>{{ detail.payMethod === 1 ? '微信支付' : '支付宝支付' }}</dd>
          <dt>支付时间</dt><dd>{{ detail.checkoutTime || '—' }}</dd>
        </dl>
      </template>
      <template #footer>
        <div class="order-dialog-footer">
          <ElCheckbox
            v-if="detail && Number(detail.status) === 2 && state.status === 2"
            v-model="autoNext"
            :disabled="busy"
          >
            处理完自动跳转下一条
          </ElCheckbox>
          <div class="order-dialog-actions">
            <ElButton
              :disabled="busy"
              @click="closeDetail"
            >
              返回
            </ElButton>
            <template v-if="detail && !detailLoading && !detailError">
              <ElButton
                v-for="action in orderActions(detail.status).filter(action => Number(detail?.status) === 1 || action !== 'cancel')"
                :key="action"
                :type="['cancel', 'reject'].includes(action) ? 'danger' : 'primary'"
                :disabled="busy"
                :loading="busy"
                @click="perform(detail, action, true)"
              >
                {{ actionLabels[action] }}
              </ElButton>
            </template>
          </div>
        </div>
      </template>
    </ElDialog>
    <ElDialog
      v-model="reasonVisible"
      :title="`${reasonTitle}原因`"
      width="520px"
      class="order-reason-dialog"
      :close-on-click-modal="!busy"
      :close-on-press-escape="!busy"
      :show-close="!busy"
      :before-close="done => { if (!busy) done() }"
    >
      <FormFeedback
        ref="reasonFeedback"
        :message="reasonError"
      />
      <p class="order-reason-number">
        订单号：{{ reasonRow?.number }}
      </p>
      <ElForm
        label-position="top"
        @submit.prevent="confirmReason"
      >
        <ElFormItem
          :label="`${reasonTitle}原因`"
          required
        >
          <ElSelect
            id="order-reason-select"
            v-model="selectedReason"
            :aria-label="`${reasonTitle}原因`"
            :placeholder="`请选择${reasonTitle}原因`"
            :disabled="busy"
          >
            <ElOption
              v-for="reason in reasons"
              :key="reason"
              :value="reason"
              :label="reason"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem
          v-if="selectedReason === '自定义原因'"
          label="原因"
          required
        >
          <ElInput
            id="order-reason-custom"
            v-model.trim="customReason"
            aria-label="自定义原因"
            type="textarea"
            maxlength="20"
            show-word-limit
            :rows="4"
            :placeholder="`请填写您${reasonTitle}的原因（限20字内）`"
            :disabled="busy"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton
          :disabled="busy"
          @click="reasonVisible = false"
        >
          取消
        </ElButton><ElButton
          type="primary"
          :loading="busy"
          @click="confirmReason"
        >
          确定
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.order-tabs { display: flex; flex-wrap: wrap; gap: var(--jz-space-2); margin-bottom: var(--jz-space-4); }
.order-tabs button { display: inline-flex; align-items: center; gap: var(--jz-space-2); min-height: var(--jz-control-height); padding: var(--jz-space-2) var(--jz-space-4); background: var(--jz-surface); border: 1px solid var(--jz-control-border); border-radius: var(--jz-radius-control); color: var(--jz-text-primary); font: inherit; font-size: var(--jz-font-control); cursor: pointer; }
.order-tabs button[aria-pressed="true"] { background: var(--jz-brand-primary); border-color: var(--jz-brand-primary); color: var(--jz-surface); }
.order-tab-count { padding: 0 var(--jz-space-1); border-radius: var(--jz-radius-control); background: var(--jz-page-background); color: var(--jz-brand-primary); font-variant-numeric: tabular-nums; }
.order-statistics-error { padding-bottom: var(--jz-space-3); color: var(--jz-danger); font-size: var(--jz-font-control); }
.order-date-filter { width: min(360px, 100%); }
.order-date-filter :deep(.el-date-editor) { width: 100%; min-width: 0; }
.order-detail-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: var(--jz-space-3); padding-bottom: var(--jz-space-4); border-bottom: 1px solid var(--jz-border); overflow-wrap: anywhere; }
.order-detail-grid { display: grid; grid-template-columns: 112px minmax(0, 1fr); gap: var(--jz-space-3); line-height: 1.7; }
.order-detail-grid dt { color: var(--jz-text-secondary); }
.order-detail-grid dd { margin: 0; overflow-wrap: anywhere; }
.order-bill dd { text-align: right; font-variant-numeric: tabular-nums; }
h3 { margin: var(--jz-space-6) 0 var(--jz-space-3); font-size: var(--jz-font-body); color: var(--jz-text-primary); }
.order-dialog-footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--jz-space-3); }
.order-dialog-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: var(--jz-space-2); margin-left: auto; }
.order-dialog-actions :deep(.el-button) { margin-left: 0; }
.order-reason-number { overflow-wrap: anywhere; }
:global(.order-detail-dialog), :global(.order-reason-dialog) { max-width: calc(100vw - 32px); border-radius: var(--jz-radius-dialog); }
:global(.order-detail-dialog .el-dialog__body) { max-height: 65vh; overflow: auto; }
:deep(.order-numeric), .order-detail-items { font-variant-numeric: tabular-nums; }
@media (max-width: 767px) {
  .order-filter .management-filter-field { width: 100%; }
  .order-tabs button { min-height: var(--jz-control-height-large); }
  .order-detail-grid { grid-template-columns: 88px minmax(0, 1fr); }
}
</style>
