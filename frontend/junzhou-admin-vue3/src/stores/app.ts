import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { SessionStorage } from '../utils/cookies.ts'

export function createAppStore(storage: SessionStorage) {
  return defineStore('app', () => {
    const sidebarOpened = ref(storage.getSidebarStatus() !== 'closed')
    const isMobile = ref(false)
    function setSidebarOpen(opened: boolean) {
      sidebarOpened.value = opened
      storage.setSidebarStatus(opened)
    }
    function toggleSidebar() { setSidebarOpen(!sidebarOpened.value) }
    function setMobile(mobile: boolean) {
      isMobile.value = mobile
      if (mobile) setSidebarOpen(false)
    }
    return { sidebarOpened, isMobile, setSidebarOpen, toggleSidebar, setMobile }
  })
}
