<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElForm, ElFormItem, ElInput, ElButton, ElAlert } from 'element-plus'
import { memberApi } from '@/api'
import type { MemberRow } from '@/api/member'
import { workspace } from '@/stores/workspace'
import { adjustmentPayload } from './forms'
import { unwrap } from './shared'
import { errorMessage } from '@/utils/error-message'
const open = defineModel<boolean>({ required: true })
const props = defineProps<{ member?: MemberRow; kind: 'points' | 'balance' }>()
const emit = defineEmits<{ saved: [] }>()
const value = ref(''), reason = ref(''), error = ref(''), busy = ref(false)
let requestId = '', fingerprint = ''
watch(open, visible => { if (visible) { value.value = ''; reason.value = ''; error.value = ''; requestId = crypto.randomUUID(); fingerprint = '' } })
watch(workspace.revision, () => { open.value = false })
async function save() {
  if (!props.member || busy.value || !workspace.can(props.kind === 'points' ? 'member:point' : 'member:balance')) return
  try {
    const body = adjustmentPayload(props.kind, value.value, reason.value, requestId)
    const current = JSON.stringify({ ...body, requestId: undefined })
    if (fingerprint && fingerprint !== current) throw new Error('此操作已尝试提交；请保持原数值与原因重试，或关闭后创建新操作。')
    fingerprint = current; busy.value = true; error.value = ''
    unwrap(await memberApi.adjust(props.member.id, props.kind, body)); open.value = false; emit('saved')
  } catch (e) { error.value = errorMessage(e, '调整失败，请重试') }
  finally { busy.value = false }
}
</script>
<template>
  <ElDialog
    v-model="open"
    :title="kind === 'points' ? '调整积分' : '余额人工入账 / 冲正'"
    width="min(560px, 94vw)"
    :close-on-click-modal="false"
    :show-close="!busy"
    :close-on-press-escape="!busy"
  >
    <p class="member-notice">
      会员：{{ member?.memberNo }} {{ member?.name }}。{{ kind === 'points' ? '正数增加积分，负数扣减积分。积分永久有效。' : '人工操作会单独记入账本。正数入账本金，负数冲正；不会作为微信充值。' }}
    </p>
    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    />
    <ElForm
      label-position="top"
      :disabled="busy"
      @submit.prevent="save"
    >
      <ElFormItem :label="kind === 'points' ? '调整积分（整数）' : '调整金额（元）'">
        <ElInput
          v-model="value"
          inputmode="decimal"
        />
      </ElFormItem><ElFormItem label="调整原因">
        <ElInput
          v-model="reason"
          type="textarea"
          maxlength="500"
          show-word-limit
        />
      </ElFormItem>
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
        确认调整
      </ElButton>
    </template>
  </ElDialog>
</template>
