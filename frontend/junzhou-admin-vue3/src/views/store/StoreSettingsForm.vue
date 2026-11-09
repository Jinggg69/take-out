<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElInput, ElMessageBox } from 'element-plus'
import { storeApi } from '@/api'
import { workspace } from '@/stores/workspace'
import { errorMessage } from '@/utils/error-message'
import { settingsDraft, settingsPayload } from '@/utils/store-settings'
import type { SettingsDraft } from '@/utils/store-settings'

const emit = defineEmits<{ dirty: [value: boolean]; busy: [value: boolean] }>()
const draft = ref<SettingsDraft>(), baseline = ref(''), loading = ref(false), saving = ref(false)
const error = ref(''), success = ref(''), errorElement = ref<HTMLElement>()
const dirty = computed(() => !!draft.value && JSON.stringify(draft.value) !== baseline.value)
watch(dirty, value => emit('dirty', value))
watch(saving, value => emit('busy', value))
const canEdit = computed(() => workspace.can('shop:update') && !saving.value && !loading.value)
let active = true
const fields: { key: keyof Omit<SettingsDraft, 'version'>; label: string; type: string; hint?: string }[] = [
  { key: 'address', label: '门店地址', type: 'text' },
  { key: 'longitude', label: '经度（BD-09）', type: 'number', hint: '范围 −180～180，最多六位小数。' },
  { key: 'latitude', label: '纬度（BD-09）', type: 'number', hint: '范围 −90～90，最多六位小数。' },
  { key: 'opensAt', label: '开始营业时间', type: 'time' },
  { key: 'closesAt', label: '结束营业时间', type: 'time', hint: '开始与结束时间相同表示全天营业；结束早于开始表示跨午夜。' },
  { key: 'deliveryRadiusMeters', label: '配送半径（米）', type: 'number', hint: '1～100000 米，填写整数。' },
  { key: 'minimumOrderAmount', label: '起送金额（元）', type: 'number' },
  { key: 'packFeePerItem', label: '每件打包费（元）', type: 'number' },
  { key: 'deliveryFee', label: '配送费（元）', type: 'number' },
  { key: 'freeDeliveryThreshold', label: '免配送费门槛（元，可选）', type: 'number', hint: '留空表示不启用；填 0 表示所有订单免配送费。' },
]
async function showError(message: string) { error.value = message; await nextTick(); errorElement.value?.focus() }
async function load() {
  if (loading.value || saving.value) return
  if (dirty.value) {
    try { await ElMessageBox.confirm('重新加载将放弃当前未保存的设置。', '重新加载门店设置', { confirmButtonText: '放弃修改并加载', cancelButtonText: '继续编辑', type: 'warning' }) } catch { return }
  }
  loading.value = true; error.value = ''; success.value = ''
  try {
    const { data } = await storeApi.settings()
    if (!active) return
    if (String(data.code) !== '1' || !data.data) throw new Error(data.msg || '门店设置加载失败')
    draft.value = settingsDraft(data.data); baseline.value = JSON.stringify(draft.value)
  } catch (e) { if (active) await showError(errorMessage(e, '门店设置加载失败，请重新加载')) }
  finally { if (active) loading.value = false }
}
async function save() {
  if (!canEdit.value || !draft.value) return
  error.value = ''; success.value = ''
  let payload
  try { payload = settingsPayload(draft.value) } catch (e) { await showError(errorMessage(e, '请检查输入')); return }
  saving.value = true
  try {
    const { data } = await storeApi.saveSettings(payload)
    if (!active) return
    if (String(data.code) !== '1') throw new Error(data.msg || '保存失败，请重试；若版本已变更，请重新加载后再编辑')
    // Always reload the server version; the write response may contain no body.
    draft.value = undefined; baseline.value = ''; success.value = '门店设置已保存。'
    const response = await storeApi.settings()
    if (!active) return
    if (String(response.data.code) !== '1' || !response.data.data) throw new Error('设置已保存，但最新版本读取失败，请重新加载后继续编辑')
    draft.value = settingsDraft(response.data.data); baseline.value = JSON.stringify(draft.value)
  } catch (e) { if (active) await showError(errorMessage(e, '保存或读取最新设置失败，请重试')) }
  finally { if (active) saving.value = false }
}
onMounted(load)
onBeforeUnmount(() => { active = false })
</script>

<template>
  <div class="settings-panel management-panel">
    <p class="settings-help">
      坐标请使用百度地图 BD-09 坐标系；保存时须填写完整经纬度。金额支持零值及最多两位小数。
    </p>
    <p
      v-if="loading"
      role="status"
    >
      正在加载门店设置…
    </p>
    <div
      v-if="error"
      ref="errorElement"
      tabindex="-1"
    >
      <ElAlert
        :title="error"
        type="error"
        :closable="false"
        role="alert"
      />
      <p
        v-if="draft"
        class="settings-help"
      >
        输入已保留。若其他人已修改设置，请重新加载最新版本后再编辑。
      </p>
    </div>
    <ElAlert
      v-if="success"
      :title="success"
      type="success"
      :closable="false"
      role="status"
    />
    <form
      v-if="draft"
      class="settings-form"
      @submit.prevent="save"
    >
      <div
        v-for="field in fields"
        :key="field.key"
        class="settings-field"
        :class="{ 'full-width': field.key === 'address' }"
      >
        <label :for="`store-${field.key}`">{{ field.label }}</label>
        <ElInput
          :id="`store-${field.key}`"
          v-model="draft[field.key]"
          :type="field.type"
          :step="field.type === 'number' ? 'any' : undefined"
          :disabled="!canEdit"
          :maxlength="field.key === 'address' ? 255 : undefined"
          :aria-describedby="field.hint ? `hint-${field.key}` : undefined"
        />
        <small
          v-if="field.hint"
          :id="`hint-${field.key}`"
        >{{ field.hint }}</small>
      </div>
      <div class="settings-actions full-width">
        <ElButton
          v-if="workspace.can('shop:update')"
          type="primary"
          native-type="submit"
          :loading="saving"
          :disabled="loading || !dirty"
        >
          保存门店设置
        </ElButton>
        <ElButton
          :disabled="saving || loading"
          @click="load"
        >
          重新加载
        </ElButton>
        <span
          v-if="dirty"
          role="status"
        >有未保存的修改</span>
      </div>
    </form>
    <ElButton
      v-else-if="!loading"
      :disabled="saving"
      @click="load"
    >
      重新加载
    </ElButton>
  </div>
</template>

<style scoped>
.settings-panel { padding: var(--jz-space-6); display: grid; gap: var(--jz-space-4); }
.settings-help { margin: 0; line-height: 1.6; color: var(--jz-text-secondary); }
.settings-form { max-width: 880px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--jz-space-6); }
.settings-field { display: grid; gap: var(--jz-space-2); align-content: start; }
.settings-field label { font-weight: 500; }
.settings-field small { color: var(--jz-text-secondary); line-height: 1.6; }
.full-width { grid-column: 1 / -1; }
.settings-actions { display: flex; align-items: center; flex-wrap: wrap; gap: var(--jz-space-3); }
.settings-actions span { color: var(--jz-text-secondary); }
@media (max-width: 767px) { .settings-panel { padding: var(--jz-space-4); } .settings-form { grid-template-columns: minmax(0, 1fr); } }
</style>
