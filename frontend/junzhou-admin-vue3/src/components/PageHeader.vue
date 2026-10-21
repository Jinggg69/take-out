<script setup lang="ts">
defineProps<{ title: string }>()
</script>
<template>
  <header class="page-header">
    <h1>{{ title }}</h1><slot />
  </header>
</template>
<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: var(--jz-space-4); margin-bottom: var(--jz-space-6); }
h1 { margin: 0; font-size: var(--jz-font-title); font-weight: 600; line-height: 1.4; }
</style>
