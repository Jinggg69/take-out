<script setup lang="ts">
import { ref } from 'vue'
defineProps<{ message?: string; items?: { fieldId: string; message: string }[] }>()
const container = ref<HTMLElement>()
function focus() { container.value?.focus() }
function focusField(id: string) { document.getElementById(id)?.focus() }
defineExpose({ focus })
</script>

<template>
  <div
    v-if="message || items?.length"
    ref="container"
    class="form-feedback"
    role="alert"
    tabindex="-1"
  >
    <p v-if="message">
      {{ message }}
    </p>
    <ul v-if="items?.length">
      <li
        v-for="item in items"
        :key="item.fieldId"
      >
        <a
          :href="`#${item.fieldId}`"
          @click.prevent="focusField(item.fieldId)"
        >{{ item.message }}</a>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.form-feedback { margin-bottom: var(--jz-space-4); padding: var(--jz-space-3) var(--jz-space-4); border: 1px solid var(--jz-danger); border-radius: var(--jz-radius-control); color: var(--jz-danger); background: color-mix(in srgb, var(--jz-danger), white 96%); font-size: var(--jz-font-control); }
p { margin: 0; }
ul { margin: 0; padding-left: var(--jz-space-4); }
a { color: inherit; }
</style>
