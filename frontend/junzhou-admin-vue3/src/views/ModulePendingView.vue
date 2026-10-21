<script setup lang="ts">
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
const route = useRoute()
</script>
<template>
  <div class="module-page">
    <PageHeader :title="route.meta.pageTitle || '工作台'" />
    <section
      class="module-content"
      aria-label="页面状态"
    >
      <AppIcon name="dashboard" />
      <h2>此页面暂未开放</h2>
      <p>账户与营业设置可通过顶部工具栏使用。</p>
    </section>
  </div>
</template>
<style scoped>
.module-content { display: flex; min-height: 360px; flex-direction: column; align-items: center; justify-content: center; padding: var(--jz-space-8); border: 1px solid var(--jz-border); border-radius: var(--jz-radius-panel); background: var(--jz-surface); text-align: center; }
.module-content > .app-icon { width: 32px; height: 32px; color: var(--jz-text-secondary); }
h2 { margin: var(--jz-space-4) 0 var(--jz-space-2); font-size: var(--jz-font-section); font-weight: 500; }
p { margin: 0; color: var(--jz-text-secondary); font-size: var(--jz-font-control); }
</style>
