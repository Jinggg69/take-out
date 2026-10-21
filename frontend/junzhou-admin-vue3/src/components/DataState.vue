<script setup lang="ts">
withDefaults(defineProps<{ title: string; description?: string; kind?: 'loading' | 'empty' | 'error' }>(), { kind: 'empty', description: '' })
</script>

<template>
  <div
    class="data-state"
    :data-kind="kind"
    :role="kind === 'error' ? 'alert' : 'status'"
    :aria-busy="kind === 'loading'"
  >
    <h2>{{ title }}</h2>
    <p v-if="description">
      {{ description }}
    </p>
    <div
      v-if="$slots.default"
      class="data-state-actions"
    >
      <slot />
    </div>
  </div>
</template>

<style scoped>
.data-state { display: flex; min-height: 240px; flex-direction: column; align-items: center; justify-content: center; padding: var(--jz-space-8) var(--jz-space-4); text-align: center; }
h2 { margin: 0; font-size: var(--jz-font-section); font-weight: 500; }
p { max-width: 48ch; margin: var(--jz-space-2) 0 0; color: var(--jz-text-secondary); font-size: var(--jz-font-control); overflow-wrap: anywhere; }
.data-state-actions { margin-top: var(--jz-space-6); }
</style>
