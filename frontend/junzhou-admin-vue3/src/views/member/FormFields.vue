<script setup lang="ts">
import { ElFormItem, ElInput, ElDatePicker, ElSelect, ElOption } from 'element-plus'
import type { MemberBody } from '@/api/member'
import { workspace } from '@/stores/workspace'
import type { Field } from './schema'
type SelectValue = string | number | string[]
type InputValue = string | number
defineProps<{ fields: Field[] }>()
const model = defineModel<MemberBody>({ required: true })
const fulfillment = [{ label: '堂食', value: 'DINE_IN' }, { label: '自取', value: 'PICKUP' }, { label: '外卖', value: 'DELIVERY' }]
</script>
<template>
  <ElFormItem
    v-for="field in fields"
    :key="field.key"
    :label="field.label"
  >
    <ElDatePicker
      v-if="field.type === 'date' || field.type === 'datetime'"
      :model-value="model[field.key] as string"
      :type="field.type === 'date' ? 'date' : 'datetime'"
      :value-format="field.type === 'date' ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss'"
      :aria-label="field.label"
      @update:model-value="model[field.key] = $event"
    />
    <ElSelect
      v-else-if="['select', 'stores', 'fulfillment'].includes(field.type || '')"
      :model-value="model[field.key] as SelectValue"
      :multiple="field.type !== 'select'"
      :aria-label="field.label"
      clearable
      @update:model-value="model[field.key] = $event"
    >
      <ElOption
        v-for="option in field.type === 'stores' ? workspace.stores.value.map(s => ({ label: s.name, value: s.id })) : field.type === 'fulfillment' ? fulfillment : field.options"
        :key="option.value"
        :label="option.label"
        :value="option.value"
      />
    </ElSelect>
    <ElInput
      v-else
      :model-value="model[field.key] as InputValue"
      :type="field.type === 'textarea' ? 'textarea' : 'text'"
      :inputmode="field.type === 'number' ? 'decimal' : 'text'"
      :aria-label="field.label"
      :maxlength="field.type === 'textarea' ? 500 : 100"
      @update:model-value="model[field.key] = $event"
    />
    <small
      v-if="field.hint"
      class="member-hint"
    >{{ field.hint }}</small>
  </ElFormItem>
</template>
