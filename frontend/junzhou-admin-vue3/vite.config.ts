import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { createDevProxy } from './build/proxy.ts'

const projectRoot = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, projectRoot, '')
  return {
    base: './',
    plugins: [vue()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      host: '127.0.0.1',
      port: 8889,
      strictPort: true,
      watch: { ignored: ['**/.verification/**', '**/.npm-cache/**'] },
      proxy: createDevProxy(env.API_PROXY_TARGET || 'http://localhost:8080/admin'),
    },
    preview: { host: '127.0.0.1', port: 4189, strictPort: true },
  }
})
