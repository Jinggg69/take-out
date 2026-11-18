<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElForm, ElFormItem, ElSelect, ElOption, ElButton, ElAlert } from 'element-plus'
import { memberApi } from '@/api'
import type { MemberRow } from '@/api/member'
import { workspace } from '@/stores/workspace'
import { unwrap } from './shared'
import { errorMessage } from '@/utils/error-message'
const open = defineModel<boolean>({ required: true })
const props = defineProps<{ templateId?: string; memberId?: string }>()
const emit = defineEmits<{ saved: [] }>()
const members = ref<MemberRow[]>([]), coupons = ref<MemberRow[]>([]), memberIds = ref<string[]>([]), couponId = ref(''), busy = ref(false), error = ref('')
let requestId = '', generation = 0
watch(open, async visible => {
  const ticket = ++generation
  if (!visible) return
  error.value = ''; members.value = []; coupons.value = []; memberIds.value = props.memberId ? [props.memberId] : []; couponId.value = props.templateId || ''; requestId = crypto.randomUUID()
  try { if (!props.templateId) coupons.value = unwrap(await memberApi.list('coupons')).filter(c => c.status === 1) }
  catch (e) { if (ticket === generation) error.value = errorMessage(e, '优惠券加载失败') }
})
watch(workspace.revision, () => { generation++; open.value = false })
async function search(keyword: string) {
  const ticket = ++generation
  try { const rows = unwrap(await memberApi.page({ page: 1, pageSize: 30, keyword, status: 1 })).records; if (ticket === generation) members.value = rows }
  catch (e) { if (ticket === generation) error.value = errorMessage(e, '会员查询失败') }
}
async function submit() {
  if (busy.value || !workspace.can('coupon:issue')) return
  if (!couponId.value || !memberIds.value.length) { error.value = '请选择优惠券和会员'; return }
  busy.value = true; error.value = ''
  try { unwrap(await memberApi.issue(couponId.value, memberIds.value, requestId)); open.value = false; emit('saved') }
  catch (e) { error.value = errorMessage(e, '发券失败，请重试') }
  finally { busy.value = false }
}
</script>
<template>
  <ElDialog
    v-model="open"
    title="向会员发券"
    width="min(560px, 94vw)"
    :close-on-click-modal="false"
    :show-close="!busy"
    :close-on-press-escape="!busy"
  >
    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    />
    <ElForm
      label-position="top"
      class="member-form"
      @submit.prevent="submit"
    >
      <ElFormItem
        v-if="!templateId"
        label="优惠券"
      >
        <ElSelect
          v-model="couponId"
          placeholder="请选择已启用的券"
        >
          <ElOption
            v-for="row in coupons"
            :key="row.id"
            :label="String(row.name)"
            :value="row.id"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem
        v-if="!memberId"
        label="指定会员"
      >
        <ElSelect
          v-model="memberIds"
          multiple
          filterable
          remote
          :remote-method="search"
          placeholder="输入会员号、姓名或手机号查询"
        >
          <ElOption
            v-for="row in members"
            :key="row.id"
            :label="`${row.memberNo} ${row.name || ''}`"
            :value="row.id"
          />
        </ElSelect>
      </ElFormItem>
      <p class="member-hint">
        发行库存、每人限领和会员状态由服务端校验。重试沿用同一业务号。
      </p>
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
        @click="submit"
      >
        确认发券
      </ElButton>
    </template>
  </ElDialog>
</template>
