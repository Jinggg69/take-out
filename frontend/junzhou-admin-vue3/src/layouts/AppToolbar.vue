<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElDropdown, ElDropdownMenu, ElDropdownItem, ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import PasswordDialog from './components/PasswordDialog.vue'
import ShopStatusDialog from './components/ShopStatusDialog.vue'
import { useAppStore, useUserStore } from '@/stores'
import { authApi } from '@/api'
import type { ShopStatus } from '@/api/types'
import { errorMessage } from '@/utils/error-message'

const app = useAppStore()
const user = useUserStore()
const route = useRoute()
const router = useRouter()
const status = ref<ShopStatus | null>(null)
const statusLoading = ref(true)
const shopVisible = ref(false)
const passwordVisible = ref(false)
const loggingOut = ref(false)
let active = true
onUnmounted(() => { active = false })
onMounted(async () => {
  try {
    const { data } = await authApi.getStatus()
    if (!active) return
    if (String(data.code) !== '1' || ![0, 1].includes(data.data)) throw new Error(data.msg || '获取营业状态失败')
    status.value = data.data
  } catch (error) {
    if (active) ElMessage.error(errorMessage(error, '获取营业状态失败，可在营业状态设置中重试'))
  } finally { if (active) statusLoading.value = false }
})
async function accountCommand(command: string) {
  if (command === 'password') { passwordVisible.value = true; return }
  if (command !== 'logout' || loggingOut.value) return
  loggingOut.value = true
  try { await user.logout(); await router.replace('/login') }
  catch (error) { ElMessage.error(errorMessage(error, '退出登录失败，请检查网络后重试')) }
  finally { loggingOut.value = false }
}
</script>

<template>
  <header class="toolbar">
    <div class="toolbar-location">
      <ElButton
        text
        class="toolbar-icon"
        :aria-label="app.sidebarOpened ? '收起导航' : '展开导航'"
        :aria-expanded="app.sidebarOpened"
        @click="app.toggleSidebar"
      >
        <AppIcon :name="app.isMobile ? 'menu' : 'collapse'" />
      </ElButton>
      <span
        class="current-location"
        :title="route.meta.pageTitle || '管理系统'"
      >{{ route.meta.pageTitle || '管理系统' }}</span>
    </div>
    <div class="toolbar-actions">
      <span
        class="business-status"
        :data-state="status"
        role="status"
      >{{ statusLoading ? '获取状态中' : status === 1 ? '营业中' : status === 0 ? '打烊中' : '状态未知' }}</span>
      <ElButton
        text
        class="shop-settings toolbar-icon"
        aria-label="营业状态设置"
        :disabled="statusLoading"
        @click="shopVisible = true"
      >
        <AppIcon name="shop" /><span class="settings-label">营业状态设置</span>
      </ElButton>
      <ElDropdown
        trigger="click"
        @command="accountCommand"
      >
        <ElButton
          text
          class="account-trigger"
          :aria-label="`${user.displayName}，账户菜单`"
          :disabled="loggingOut"
          :loading="loggingOut"
        >
          <span class="account-name">{{ user.displayName }}</span><AppIcon name="chevron" />
        </ElButton>
        <template #dropdown>
          <ElDropdownMenu>
            <ElDropdownItem command="password">
              修改密码
            </ElDropdownItem><ElDropdownItem
              command="logout"
              divided
            >
              退出登录
            </ElDropdownItem>
          </ElDropdownMenu>
        </template>
      </ElDropdown>
    </div>
    <ShopStatusDialog
      v-model="shopVisible"
      :status="status"
      @saved="status = $event"
      @loaded="status = $event"
    />
    <PasswordDialog v-model="passwordVisible" />
  </header>
</template>

<style scoped lang="scss">
.toolbar { display: flex; position: sticky; top: 0; z-index: 20; align-items: center; justify-content: space-between; gap: var(--jz-space-3); min-height: var(--jz-toolbar-height); padding-inline: var(--jz-space-6); border-bottom: 1px solid var(--jz-border); background: var(--jz-surface); }
.toolbar-location, .toolbar-actions { display: flex; align-items: center; gap: var(--jz-space-3); min-width: 0; }
.toolbar-location { flex: 1; }
.toolbar-actions { flex-shrink: 0; }
.current-location { min-width: 0; overflow: hidden; color: var(--jz-text-secondary); font-size: var(--jz-font-control); text-overflow: ellipsis; white-space: nowrap; }
.toolbar-icon { flex: none; min-width: 36px; min-height: 36px; }
.toolbar-actions > .el-button { margin-left: 0; }
.business-status { flex: none; padding: var(--jz-space-1) var(--jz-space-2); border-radius: var(--jz-radius-control); color: var(--jz-text-secondary); background: var(--jz-page-background); font-size: var(--jz-font-caption); white-space: nowrap; }
.business-status[data-state="1"] { color: var(--jz-success); background: color-mix(in srgb, var(--jz-success), white 94%); }
.business-status[data-state="0"] { color: var(--jz-text-secondary); }
.settings-label { margin-left: var(--jz-space-2); }
.account-trigger { gap: var(--jz-space-2); }
.account-name { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 767px) {
  .toolbar { padding-inline: var(--jz-space-3); gap: var(--jz-space-2); }
  .toolbar-location, .toolbar-actions { gap: var(--jz-space-1); }
  .settings-label { display: none; }
  .account-name { max-width: 64px; }
  .toolbar-icon, .account-trigger { min-height: 44px; min-width: 44px; padding-inline: var(--jz-space-2); }
}
@media (max-width: 400px) { .account-name { max-width: 48px; } }
</style>
