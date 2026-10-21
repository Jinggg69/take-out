<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ElButton, ElDialog, ElRadio, ElRadioGroup, ElMessage } from 'element-plus'
import type { ShopStatus } from '@/api/types'
import { authApi } from '@/api'
import { errorMessage } from '@/utils/error-message'
import FormFeedback from '@/components/FormFeedback.vue'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ status: ShopStatus | null }>()
const emit = defineEmits<{ saved: [status: ShopStatus]; loaded: [status: ShopStatus] }>()
const draft = ref<ShopStatus | null>(null)
const loading = ref(false)
const saving = ref(false)
const message = ref('')
const feedback = ref<InstanceType<typeof FormFeedback>>()
watch(visible, open => {
  if (open) { draft.value = props.status; message.value = ''; if (draft.value === null) void load() }
})
async function load() {
  loading.value = true; message.value = ''
  try {
    const { data } = await authApi.getStatus()
    if (String(data.code) !== '1' || ![0, 1].includes(data.data)) throw new Error(data.msg || '获取营业状态失败')
    draft.value = data.data; emit('loaded', data.data)
  } catch (error) { message.value = errorMessage(error, '获取营业状态失败，请检查网络后重试') }
  finally { loading.value = false }
}
async function save() {
  if (draft.value === null || saving.value || loading.value) return
  saving.value = true; message.value = ''
  try {
    const status = draft.value
    const { data } = await authApi.setStatus(status)
    if (String(data.code) !== '1') throw new Error(data.msg || '营业状态保存失败')
    emit('saved', status)
    ElMessage.success('营业状态已更新')
    visible.value = false
  } catch (error) {
    message.value = errorMessage(error, '营业状态保存失败，请检查网络后重试')
    await nextTick(); feedback.value?.focus()
  } finally { saving.value = false }
}
</script>

<template>
  <ElDialog
    v-model="visible"
    title="营业状态设置"
    width="min(560px, calc(100vw - 32px))"
    class="jz-dialog"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    destroy-on-close
  >
    <FormFeedback
      ref="feedback"
      :message="message"
    />
    <p
      v-if="loading"
      role="status"
    >
      正在获取营业状态…
    </p>
    <ElRadioGroup
      v-else-if="draft !== null"
      v-model="draft"
      class="shop-options"
      aria-label="营业状态"
      :disabled="saving"
    >
      <div class="shop-option">
        <ElRadio :value="1">
          营业中
        </ElRadio>
        <p>当前餐厅处于营业状态，自动接收任何订单，可点击打烊进入店铺打烊状态。</p>
      </div>
      <div class="shop-option">
        <ElRadio :value="0">
          打烊中
        </ElRadio>
        <p>当前餐厅处于打烊状态，仅接受营业时间内的预定订单，可点击营业中手动恢复营业状态。</p>
      </div>
    </ElRadioGroup>
    <ElButton
      v-else
      @click="load"
    >
      重新获取营业状态
    </ElButton>
    <template #footer>
      <ElButton
        :disabled="saving"
        @click="visible = false"
      >
        取消
      </ElButton>
      <ElButton
        type="primary"
        :loading="saving"
        :disabled="loading || draft === null"
        @click="save"
      >
        确定
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.shop-options { display: flex; flex-direction: column; align-items: stretch; width: 100%; }
.shop-option { padding: var(--jz-space-3) 0; }
.shop-option + .shop-option { border-top: 1px solid var(--jz-border); }
.shop-option p { margin: var(--jz-space-2) 0 0 24px; color: var(--jz-text-secondary); font-size: var(--jz-font-control); line-height: 1.7; }
</style>
