<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElDrawer } from 'element-plus'
import AppSidebar from './AppSidebar.vue'
import AppToolbar from './AppToolbar.vue'
import { useAppStore } from '@/stores'
import { useOrderReminders } from '@/composables/useOrderReminders'
import newOrderSound from '@/assets/audio/preview.mp3'
import reminderSound from '@/assets/audio/reminder.mp3'

const app = useAppStore()
const media = window.matchMedia('(max-width: 991px)')
app.setMobile(media.matches)
const onResize = () => app.setMobile(media.matches)
onMounted(() => media.addEventListener('change', onResize))
onUnmounted(() => media.removeEventListener('change', onResize))
const drawerVisible = computed({ get: () => app.sidebarOpened, set: value => app.setSidebarOpen(value) })
const newOrderAudio = ref<HTMLAudioElement>()
const reminderAudio = ref<HTMLAudioElement>()
useOrderReminders(newOrderAudio, reminderAudio)
function focusMain() { document.getElementById('main-content')?.focus() }
</script>

<template>
  <a
    class="skip-link"
    href="#main-content"
    @click.prevent="focusMain"
  >跳到主要内容</a>
  <div
    class="app-layout"
    :class="{ 'is-collapsed': !app.sidebarOpened, 'is-mobile': app.isMobile }"
  >
    <div
      v-if="!app.isMobile"
      class="desktop-sidebar"
    >
      <AppSidebar :collapsed="!app.sidebarOpened" />
    </div>
    <ElDrawer
      v-else
      v-model="drawerVisible"
      title="导航菜单"
      direction="ltr"
      :size="224"
      class="navigation-drawer"
    >
      <AppSidebar @navigate="app.setSidebarOpen(false)" />
    </ElDrawer>
    <div class="app-workspace">
      <AppToolbar />
      <main
        id="main-content"
        class="main-content"
        tabindex="-1"
      >
        <RouterView />
      </main>
    </div>
    <audio
      ref="newOrderAudio"
      :src="newOrderSound"
      preload="auto"
      hidden
    />
    <audio
      ref="reminderAudio"
      :src="reminderSound"
      preload="auto"
      hidden
    />
  </div>
</template>

<style scoped lang="scss">
.app-layout { display: grid; grid-template-columns: var(--jz-sidebar-width) minmax(0, 1fr); min-height: 100dvh; }
.app-layout.is-collapsed { grid-template-columns: var(--jz-sidebar-collapsed-width) minmax(0, 1fr); }
.desktop-sidebar { position: sticky; top: 0; align-self: start; height: 100dvh; overflow: auto; }
.app-workspace { min-width: 0; }
.main-content { min-width: 0; padding: var(--jz-space-6); }
.skip-link { position: fixed; top: -80px; left: var(--jz-space-4); z-index: 4000; padding: var(--jz-space-3); color: var(--jz-surface); background: var(--jz-brand-primary); }
.skip-link:focus { top: var(--jz-space-3); }
.is-mobile { display: block; }
@media (max-width: 767px) { .main-content { padding: var(--jz-space-4); } }
</style>
