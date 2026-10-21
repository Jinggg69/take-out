<script setup lang="ts">
const paths = {
  dashboard: ['M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z'],
  statistics: ['M4 4v16h17 M8 15v-4 M13 15V7 M18 15v-6'],
  order: ['M7 3h10v3h3v15H4V6h3z M7 3v5h10V3 M8 12h8 M8 16h6'],
  setmeal: ['M3 10h18v11H3z M6 10V4h12v6 M8 14h8 M9 17h6'],
  dish: ['M4 14a8 8 0 0 1 16 0 M3 14h18 M5 18h14 M12 4v2'],
  category: ['M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z'],
  employee: ['M8 7a4 4 0 1 0 8 0a4 4 0 1 0-8 0 M4 21v-3a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v3'],
  menu: ['M4 6h16 M4 12h16 M4 18h16'],
  collapse: ['M5 4v16 M19 6l-6 6 6 6'],
  chevron: ['m6 9 6 6 6-6'],
  shop: ['M4 10v11h16V10 M3 4h18l1 6a3 3 0 0 1-5 1a3 3 0 0 1-5 0a3 3 0 0 1-5 0a3 3 0 0 1-5-1z M9 21v-6h6v6'],
} as const
defineProps<{ name: keyof typeof paths }>()
</script>

<template>
  <svg
    class="app-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path
      v-for="path in paths[name]"
      :key="path"
      :d="path"
    />
  </svg>
</template>

<style scoped>
.app-icon { display: inline-block; width: 20px; height: 20px; flex: none; vertical-align: middle; }
</style>
