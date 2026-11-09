<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElAlert, ElMessageBox, ElTabs, ElTabPane } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { workspace } from '@/stores/workspace'
import StoreSettingsForm from './StoreSettingsForm.vue'
import StoreMenuEditor from './StoreMenuEditor.vue'

const tab = ref('settings'), settingsDirty = ref(false), menuDirty = ref(false)
const busy = ref(false)
onBeforeRouteLeave(async () => {
  if (busy.value) return false
  if (!settingsDirty.value && !menuDirty.value) return true
  try {
    await ElMessageBox.confirm('尚有未保存的门店设置或菜单修改，离开将丢失这些修改。', '离开门店设置', { confirmButtonText: '放弃修改并离开', cancelButtonText: '继续编辑', type: 'warning' })
    return true
  } catch { return false }
})
function beforeUnload(event: BeforeUnloadEvent) {
  if (busy.value || settingsDirty.value || menuDirty.value) event.preventDefault()
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <section class="store-page">
    <PageHeader title="门店设置与菜单" />
    <p class="store-context">
      当前门店：{{ workspace.current.value?.name }}。以下设置仅对当前门店生效。
    </p>
    <ElAlert
      v-if="!workspace.can('shop:update')"
      title="您拥有查看权限，修改设置请联系门店管理员。"
      type="info"
      :closable="false"
    />
    <ElTabs
      v-model="tab"
      :before-leave="() => !busy"
    >
      <ElTabPane
        label="配送与营业设置"
        name="settings"
      >
        <StoreSettingsForm
          @dirty="settingsDirty = $event"
          @busy="busy = $event"
        />
      </ElTabPane>
      <ElTabPane
        label="门店菜单"
        name="menu"
        lazy
      >
        <StoreMenuEditor
          @dirty="menuDirty = $event"
          @busy="busy = $event"
        />
      </ElTabPane>
    </ElTabs>
  </section>
</template>

<style scoped>
.store-page { display: grid; gap: var(--jz-space-4); min-width: 0; }
.store-page :deep(.page-header) { margin-bottom: 0; }
.store-context { margin: 0; color: var(--jz-text-secondary); line-height: 1.6; }
</style>
