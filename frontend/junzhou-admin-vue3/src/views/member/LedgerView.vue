<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElInput, ElDatePicker, ElDialog, ElForm, ElFormItem, ElAlert } from 'element-plus'
import { memberApi } from '@/api'
import type { MemberLedger, MemberPage, MemberRow } from '@/api/member'
import { workspace } from '@/stores/workspace'
import PageHeader from '@/components/PageHeader.vue'
import DataState from '@/components/DataState.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import MemberTable from './MemberTable.vue'
import { unwrap, useMemberLoad, pointColumns, walletColumns, rechargeColumns } from './shared'
import { required } from './forms'
import { errorMessage } from '@/utils/error-message'
import './member.scss'
const props = defineProps<{ kind: MemberLedger; embedded?: boolean }>()
const keyword = ref(''), dates = ref<string[]>([]), page = ref(1), pageSize = ref(10)
const title = computed(() => ({ points: '积分流水', wallet: '余额流水', recharges: '充值记录' })[props.kind])
const columns = computed(() => ({ points: pointColumns, wallet: walletColumns, recharges: rechargeColumns })[props.kind])
const { value: result, loading, error, load } = useMemberLoad<MemberPage>(() => ({ records: [], total: 0 }), async () => unwrap(await memberApi.ledger(props.kind, { page: page.value, pageSize: pageSize.value, keyword: keyword.value || undefined, startDate: dates.value?.[0], endDate: dates.value?.[1] })))
watch(() => props.kind, () => { page.value = 1; void load() }, { immediate: true })
const refundRow = ref<MemberRow>(), reason = ref(''), refundError = ref(''), busy = ref(false), refundOpen = ref(false)
let requestId = ''
watch(workspace.revision, () => { refundOpen.value = false })
function refund(row: MemberRow) { refundRow.value = row; requestId = crypto.randomUUID(); reason.value = ''; refundError.value = ''; refundOpen.value = true }
async function submitRefund() {
  if (!refundRow.value || busy.value || !workspace.can('member:refund')) return
  try { const text = required(reason.value, '退款原因'); busy.value = true; refundError.value = ''; unwrap(await memberApi.refund(refundRow.value.id, text, requestId)); refundOpen.value = false; await load() }
  catch (e) { refundError.value = errorMessage(e, '退款失败，请重试') }
  finally { busy.value = false }
}
</script>
<template>
  <div class="member-page">
    <PageHeader
      v-if="!embedded"
      :title="title"
    />
    <section class="management-panel">
      <form
        class="management-filter"
        @submit.prevent="page = 1; load()"
      >
        <ElInput
          v-model="keyword"
          aria-label="会员号或业务号"
          placeholder="会员号 / 姓名 / 业务号"
          clearable
        /><ElDatePicker
          v-model="dates"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        /><ElButton
          native-type="submit"
          :loading="loading"
        >
          查询
        </ElButton>
      </form>
      <DataState
        v-if="loading"
        kind="loading"
        title="正在加载流水"
      />
      <DataState
        v-else-if="error"
        kind="error"
        title="加载失败"
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
      >
        <template
          v-if="kind === 'recharges' && workspace.can('member:refund')"
          #default="{ row }"
        >
          <ElButton
            v-if="['PAID', 'SUCCESS'].includes(String(row.status))"
            :disabled="row.refundEligible === false"
            :title="row.refundEligible === false ? '批次已使用或有冻结，暂不可退款' : '整笔退回本金并撤回赠送额'"
            link
            type="danger"
            @click="refund(row)"
          >
            整笔退款
          </ElButton>
        </template>
      </MemberTable>
      <DataState
        v-else
        title="暂无记录"
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
    <ElDialog
      v-model="refundOpen"
      title="充值整笔退款"
      width="min(520px, 94vw)"
      :close-on-click-modal="false"
      :show-close="!busy"
      :close-on-press-escape="!busy"
    >
      <p class="member-notice">
        仅允许当前净消耗为零、无冻结或退款中占用的原充值批次退款。服务端核验后原路退回本金，并撤回赠送额。
      </p>
      <ElAlert
        v-if="refundError"
        :title="refundError"
        type="error"
        :closable="false"
      />
      <ElForm
        label-position="top"
        @submit.prevent="submitRefund"
      >
        <ElFormItem label="退款原因">
          <ElInput
            v-model="reason"
            type="textarea"
            maxlength="500"
            :disabled="busy"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton
          :disabled="busy"
          @click="refundOpen = false"
        >
          取消
        </ElButton><ElButton
          type="danger"
          :loading="busy"
          @click="submitRefund"
        >
          确认整笔退款
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>
