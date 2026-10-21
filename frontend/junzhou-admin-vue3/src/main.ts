import { createApp } from 'vue'
import 'element-plus/dist/index.css'
import '@/styles/index.scss'
import App from './App.vue'
import { router } from './router'
import { pinia } from './stores'

createApp(App).use(pinia).use(router).mount('#app')
