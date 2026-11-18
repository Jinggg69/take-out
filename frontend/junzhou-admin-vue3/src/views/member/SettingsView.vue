<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElForm, ElFormItem, ElSwitch, ElButton, ElAlert, ElMessage } from 'element-plus'
import { memberApi } from '@/api'
import type { MemberBody } from '@/api/member'
import { workspace } from '@/stores/workspace'
import PageHeader from '@/components/PageHeader.vue'
import DataState from '@/components/DataState.vue'
import FormFields from './FormFields.vue'
import { settingsPayload } from './forms'
import { unwrap, useMemberLoad } from './shared'
import { errorMessage } from '@/utils/error-message'
import './member.scss'
const { value: draft, loading, error, load } = useMemberLoad<MemberBody>(() => ({}), async () => unwrap(await memberApi.settings()))
const busy = ref(false), saveError = ref('')
onMounted(load)
watch(workspace.revision, () => { saveError.value = '' })
async function save() {
  if (busy.value || !workspace.can('member:manage')) return
  try { const body = settingsPayload(draft.value); busy.value = true; saveError.value = ''; unwrap(await memberApi.saveSettings(body)); ElMessage.success('会员规则已保存'); await load() }
  catch (e) { saveError.value = errorMessage(e, '规则保存失败') }
  finally { busy.value = false }
}
</script>
<template>
  <div class="member-page">
    <PageHeader title="会员设置" />
    <section class="management-panel">
      <DataState
        v-if="loading"
        kind="loading"
        title="正在加载会员规则"
      />
      <DataState
        v-else-if="error"
        kind="error"
        title="规则加载失败"
        :description="error"
      >
        <ElButton @click="load">
          重新加载
        </ElButton>
      </DataState>
      <ElForm
        v-else
        class="member-form member-settings"
        label-position="top"
        :disabled="busy || !workspace.can('member:manage')"
        @submit.prevent="save"
      >
        <p class="member-notice">
          积分永久有效。规则修改只影响新报价和新订单，已下单权益保留原快照。
        </p>
        <ElAlert
          v-if="saveError"
          :title="saveError"
          type="error"
          :closable="false"
        />
        <FormFields
          v-model="draft"
          :fields="[{ key: 'earnPerYuan', label: '商品实付每满1元赠送积分', type: 'number', hint: '不含配送、包装、赠送余额、积分抵扣及优惠券抵扣部分。' }, { key: 'pointsPerYuan', label: '抵扣1元所需积分', type: 'number' }, { key: 'maxDeductionPercent', label: '积分最多抵扣商品金额百分比', type: 'number' }]"
        />
        <ElFormItem label="优惠券可与积分叠加">
          <ElSwitch
            :model-value="Boolean(draft.allowCouponWithPoints)"
            @update:model-value="draft.allowCouponWithPoints = $event"
          />
        </ElFormItem>
        <ElFormItem label="优惠券可与等级优惠叠加">
          <ElSwitch
            :model-value="Boolean(draft.allowCouponWithLevel)"
            @update:model-value="draft.allowCouponWithLevel = $event"
          />
        </ElFormItem>
        <ElButton
          v-if="workspace.can('member:manage')"
          native-type="submit"
          type="primary"
          :loading="busy"
        >
          保存规则
        </ElButton>
      </ElForm>
    </section>
  </div>
</template>
<style scoped>.member-settings { max-width: 640px; }</style>
