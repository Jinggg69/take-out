import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni.default ? uni.default() : uni()],
  server: { port: 5178, strictPort: true },
})
