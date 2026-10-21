<script setup lang="ts">
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { routePending } from './router'
</script>

<template>
  <ElConfigProvider :locale="zhCn">
    <div
      v-if="routePending"
      class="route-progress"
      role="progressbar"
      aria-label="页面加载中"
    />
    <RouterView />
  </ElConfigProvider>
</template>

<style scoped>
.route-progress {
  position: fixed;
  inset: 0 0 auto;
  z-index: 3000;
  height: 2px;
  background: var(--jz-brand-primary);
}
</style>
