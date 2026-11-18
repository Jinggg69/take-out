<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElAlert, ElTabs, ElTabPane } from 'element-plus'
import { memberApi } from '@/api'
import type { MemberBody, MemberResource, MemberRow } from '@/api/member'
import { workspace } from '@/stores/workspace'
import PageHeader from '@/components/PageHeader.vue'
import DataState from '@/components/DataState.vue'
import MemberTable from './MemberTable.vue'
import FormFields from './FormFields.vue'
import IssueCouponDialog from './IssueCouponDialog.vue'
import LedgerView from './LedgerView.vue'
import { resources } from './schema'
import { resourcePayload, serverDate } from './forms'
import { unwrap, useMemberLoad } from './shared'
import { errorMessage } from '@/utils/error-message'
import './member.scss'
const props = defineProps<{ resource: MemberResource }>()
const config = computed(() => resources[props.resource])
const { value: rows, loading, error, load } = useMemberLoad<MemberRow[]>(() => [], async () => unwrap(await memberApi.list(props.resource)))
const open = ref(false), draft = ref<MemberBody>({}), editId = ref<string>(), busy = ref(false), saveError = ref(''), issueOpen = ref(false), issueId = ref(''), tab = ref('plans')
watch(() => props.resource, () => { open.value = false; tab.value = 'plans'; void load() }, { immediate: true })
watch(workspace.revision, () => { open.value = false; issueOpen.value = false })
function edit(row?: MemberRow) { editId.value = row?.id; draft.value = JSON.parse(JSON.stringify(row || config.value.defaults)); if (props.resource === 'coupons') { draft.value.startTime = serverDate(draft.value.startTime); draft.value.endTime = serverDate(draft.value.endTime) } saveError.value = ''; open.value = true }
async function save() {
  if (busy.value || !workspace.can(config.value.permission)) return
  try {
    const body = resourcePayload(props.resource, draft.value)
    busy.value = true; saveError.value = ''
    unwrap(await memberApi.save(props.resource, editId.value, body)); open.value = false; await load()
  } catch (e) { saveError.value = errorMessage(e, '保存失败，请重试') }
  finally { busy.value = false }
}
</script>
<template>
  <div class="member-page">
    <PageHeader :title="resource === 'recharge-plans' ? '储值管理' : config.title">
      <ElButton
        v-if="workspace.can(config.permission) && tab === 'plans'"
        type="primary"
        @click="edit()"
      >
        新增{{ config.title === '优惠券' ? '优惠券' : config.title === '会员等级' ? '等级' : config.title === '会员标签' ? '标签' : '套餐' }}
      </ElButton>
    </PageHeader>
    <ElTabs
      v-if="resource === 'recharge-plans'"
      v-model="tab"
    >
      <ElTabPane
        label="充值套餐"
        name="plans"
      /><ElTabPane
        label="充值记录与退款"
        name="recharges"
      /><ElTabPane
        label="余额流水"
        name="wallet"
      />
    </ElTabs>
    <LedgerView
      v-if="resource === 'recharge-plans' && tab !== 'plans'"
      :kind="tab === 'wallet' ? 'wallet' : 'recharges'"
      embedded
    />
    <section
      v-else
      class="management-panel"
    >
      <p
        v-if="resource === 'recharge-plans'"
        class="member-notice"
      >
        充值使用模拟支付，不涉及真实资金。赠送余额不计入商品销售收入。
      </p>
      <p
        v-if="resource === 'coupons'"
        class="member-notice"
      >
        已发放的券保留原规则与有效期；停用模板只停止新发放。每单最多使用一张券。
      </p>
      <DataState
        v-if="loading"
        kind="loading"
        title="正在加载"
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
        v-else-if="rows.length"
        :rows="rows"
        :columns="config.columns"
      >
        <template #default="{ row }">
          <ElButton
            v-if="workspace.can(config.permission)"
            link
            type="primary"
            @click="edit(row)"
          >
            编辑
          </ElButton><ElButton
            v-if="resource === 'coupons' && workspace.can('coupon:issue') && row.status === 1"
            link
            type="primary"
            @click="issueId = row.id; issueOpen = true"
          >
            发券
          </ElButton>
        </template>
      </MemberTable>
      <DataState
        v-else
        :title="`暂无${config.title}`"
        description="通过上方新增按钮配置，保存后即可在此查看。"
      />
    </section>
    <ElDialog
      v-model="open"
      :title="`${editId ? '编辑' : '新增'}${config.title}`"
      width="min(640px, 94vw)"
      :close-on-click-modal="false"
      :show-close="!busy"
      :close-on-press-escape="!busy"
    >
      <ElAlert
        v-if="saveError"
        :title="saveError"
        type="error"
        :closable="false"
      />
      <ElForm
        label-position="top"
        class="member-form"
        :disabled="busy"
        @submit.prevent="save"
      >
        <FormFields
          v-model="draft"
          :fields="config.fields"
        />
      </ElForm>
      <template #footer>
        <ElButton
          :disabled="busy"
          @click="open = false"
        >
          取消
        </ElButton><ElButton
          type="primary"
          :loading="busy"
          @click="save"
        >
          保存
        </ElButton>
      </template>
    </ElDialog>
    <IssueCouponDialog
      v-model="issueOpen"
      :template-id="issueId"
      @saved="load"
    />
  </div>
</template>
