import { createSSRApp } from 'vue'
import App from './App'
import getAppStore, { pinia } from './store/index.js'

export function createApp() {
  const app = createSSRApp(App)
  app.use(pinia)
  const state = getAppStore()
  state.restoreSession()
  app.config.globalProperties.$appStore = state
  return { app }
}
