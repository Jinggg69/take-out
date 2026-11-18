<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElAlert, ElButton } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores'
import { createPlatformAuthApi } from '@/api/platform'
import { platformRequest } from '@/utils/platform-request'
import { sessionCookies } from '@/utils/cookies'
import { errorMessage } from '@/utils/error-message'
import PasswordDialog from './components/PasswordDialog.vue'


const user = useUserStore(), router = useRouter()
const loading = ref(true), error = ref(''), verified = ref(false), password = ref(false), leaving = ref(false)
let active = true
async function verify() {
  loading.value = true; error.value = ''; verified.value = false
  const token = sessionCookies.getToken()
  try {
    const { data } = await createPlatformAuthApi(platformRequest).me()
    if (!active || token !== sessionCookies.getToken()) return
    if (String(data.code) !== '1') throw new Error(data.msg || '无法验证平台会话')
    if (data.data?.accountType !== 'PLATFORM') throw new Error('当前账号没有平台管理权限，请退出后重新登录')
    verified.value = true
  } catch (cause) { if (active) error.value = errorMessage(cause, '平台会话验证失败，请重试') }
  finally { if (active) loading.value = false }
}
async function logout() {
  if (leaving.value) return
  leaving.value = true
  try { await user.logout(); await router.replace('/login') }
  catch (cause) { error.value = errorMessage(cause, '退出失败，请重试') }
  finally { leaving.value = false }
}
onMounted(verify)
onUnmounted(() => { active = false })
</script>

<template>
  <div class="platform-layout">
    <aside class="platform-sidebar">
      <RouterLink
        class="platform-brand"
        to="/platform/tenants"
      >
        <span
          class="platform-mark"
          aria-hidden="true"
        >均</span>
        <span><strong>均州餐饮</strong><small>点餐营销管理系统</small></span>
      </RouterLink>
      <p class="platform-identity">
        平台运营管理
      </p>
      <nav aria-label="平台导航">
        <RouterLink to="/platform/tenants">
          商户管理
        </RouterLink>
        <RouterLink to="/platform/stores">
          门店管理
        </RouterLink>
      </nav>
    </aside>
    <div class="platform-workspace">
      <header class="platform-toolbar">
        <span>{{ user.displayName }}</span>
        <ElButton @click="password = true">
          修改密码
        </ElButton>
        <ElButton
          :loading="leaving"
          @click="logout"
        >
          退出登录
        </ElButton>
      </header>
      <main class="platform-content">
        <p
          v-if="loading"
          role="status"
        >
          正在验证平台会话…
        </p>
        <ElAlert
          v-if="error"
          :title="error"
          type="error"
          :closable="false"
          show-icon
        />
        <ElButton
          v-if="error && !verified"
          class="platform-retry"
          @click="verify"
        >
          重新验证
        </ElButton>
        <RouterView
          v-if="verified"
          :key="router.currentRoute.value.path"
        />
      </main>
    </div>
    <PasswordDialog v-model="password" />
  </div>
</template>

<style scoped lang="scss">
.platform-mark { display: grid; place-items: center; width: 40px; height: 40px; background: #982d34; color: #fff; border-radius: 8px; font-size: 24px; flex: none; }
.platform-identity { margin: 0 24px 16px; color: var(--jz-nav-text); }
.platform-layout { display: grid; grid-template-columns: 224px minmax(0, 1fr); min-height: 100dvh; }
.platform-sidebar { background: var(--jz-nav-background); color: var(--jz-nav-text); }
.platform-brand { display: flex; align-items: center; gap: 12px; min-height: 88px; padding: 16px 24px; color: var(--jz-brand-gold); text-decoration: none; }
.platform-brand img { object-fit: contain; }
.platform-brand > span:not(.platform-mark) { display: grid; gap: 4px; }
.platform-brand strong { font-size: var(--jz-font-section); }
.platform-brand small { color: var(--jz-nav-text); }
nav { display: grid; gap: 8px; padding: 16px 12px; }
nav a { padding: 12px 16px; border-radius: var(--jz-radius-control); color: inherit; text-decoration: none; }
nav a:hover { background: var(--jz-nav-hover); }
nav a.router-link-active { color: var(--jz-brand-gold); background: var(--jz-nav-selected); font-weight: 600; }
.platform-workspace { min-width: 0; }
.platform-toolbar { display: flex; justify-content: flex-end; align-items: center; flex-wrap: wrap; gap: 12px; min-height: 60px; padding: 12px 24px; background: var(--jz-surface); border-bottom: 1px solid var(--jz-border); }
.platform-toolbar .el-button { margin: 0; }
.platform-content { padding: 24px; }
.platform-retry { margin-block: 16px; }
@media (max-width: 767px) {
  .platform-layout { grid-template-columns: minmax(0, 1fr); grid-template-rows: auto 1fr; }
  .platform-brand { min-height: 64px; padding: 12px 16px; }
  .platform-brand > span:not(.platform-mark) { display: flex; align-items: baseline; gap: 12px; }
  nav { display: flex; padding: 0 12px 12px; }
  .platform-toolbar { padding: 12px 16px; gap: 8px; }
  .platform-content { padding: 16px; }
}
</style>
