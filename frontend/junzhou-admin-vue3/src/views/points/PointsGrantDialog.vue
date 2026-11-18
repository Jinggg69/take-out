<script setup lang="ts">
import axios from 'axios'
import { computed, ref, watch } from 'vue'
import { ElAlert, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { pointsApi } from '@/api'
import { pointsGrantPayload, type PointsAccount, type PointsGrant } from '@/api/points'
import { pendingWrites, workspace } from '@/stores/workspace'
import { errorMessage } from '@/utils/error-message'
import { unwrap } from '@/views/member/shared'

const open = defineModel<boolean>({ required: true })
const props = defineProps<{ members: PointsAccount[] }>()
const emit = defineEmits<{ saved: [] }>()
const points = ref(''), reason = ref(''), error = ref(''), busy = ref(false)
const attempted = ref<PointsGrant>()
const memberIds = computed(() => props.members.map(member => member.id))
const total = computed(() => {
  if (!/^\d+$/.test(points.value.trim())) return '—'
  const value = Number(points.value) * props.members.length
  return Number.isSafeInteger(value) && value > 0 ? value.toLocaleString('zh-CN') : '—'
})

watch(open, visible => {
  if (!visible || attempted.value) return
  points.value = ''; reason.value = ''; error.value = ''
})
watch(memberIds, ids => {
  if (attempted.value && JSON.stringify([...ids].sort()) !== JSON.stringify(attempted.value.memberIds)) attempted.value = undefined
})
watch(workspace.revision, () => { attempted.value = undefined })

async function grant() {
  if (busy.value || !workspace.canTenant('member:point:batch')) return
  try {
    const payload = attempted.value ?? pointsGrantPayload(memberIds.value, points.value, reason.value, crypto.randomUUID())
    attempted.value = payload
    busy.value = true; error.value = ''; pendingWrites.value++
    try {
      const result = unwrap(await pointsApi.grant(payload))
      attempted.value = undefined
      open.value = false
      ElMessage.success(`已向 ${result.memberCount} 名会员赠分，批次 ${result.batchNo}`)
      emit('saved')
    } finally { pendingWrites.value--; busy.value = false }
  } catch (cause) {
    error.value = errorMessage(cause, '赠分失败，请核对后重试')
    const status = axios.isAxiosError(cause) ? cause.response?.status : undefined
    if (status === 400 || status === 403) attempted.value = undefined
  }
}
function close() {
  if (!busy.value) open.value = false
}
</script>

<template>
  <ElDialog
    v-model="open"
    title="批量赠分"
    width="min(580px, 94vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :show-close="!busy"
  >
    <p>已选 {{ members.length }} 名会员，预计赠送 {{ total }} 积分。</p>
    <details class="grant-members">
      <summary>查看已选会员名单</summary>
      <ul>
        <li
          v-for="member in members"
          :key="member.id"
        >
          {{ member.memberNo }} {{ member.name }}
        </li>
      </ul>
    </details>
    <p class="points-note">
      赠分优先抵扣会员的待扣积分，剩余部分计入可用积分。
    </p>
    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    />
    <p
      v-if="attempted && error"
      class="points-note"
    >
      重试使用同一批次。需要改动本次内容时，可先核对流水，再重新选择名单。
    </p>
    <ElForm
      label-position="top"
      :disabled="busy || !!attempted"
      @submit.prevent="grant"
    >
      <ElFormItem label="每人赠送积分">
        <ElInput
          v-model="points"
          aria-label="每人赠送积分"
          inputmode="numeric"
        />
      </ElFormItem>
      <ElFormItem label="赠送原因">
        <ElInput
          v-model="reason"
          aria-label="赠送原因"
          type="textarea"
          :maxlength="500"
          show-word-limit
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton
        :disabled="busy"
        @click="close"
      >
        取消
      </ElButton>
      <ElButton
        :loading="busy"
        type="primary"
        @click="grant"
      >
        {{ attempted && error ? '重试赠分' : '确认赠分' }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.grant-members ul { max-height: 150px; margin: 8px 0; overflow: auto; }
.grant-members li { overflow-wrap: anywhere; }
</style>
