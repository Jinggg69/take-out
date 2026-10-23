<script setup lang="ts">
import { ElButton, ElOption, ElSelect } from 'element-plus'
import { flavorOptions } from '@/utils/dish'
import type { EditableFlavor } from '@/utils/dish'

const flavors = defineModel<EditableFlavor[]>({ required: true })
defineProps<{ disabled: boolean }>()
function options(index: number) {
  const choices = flavorOptions(flavors.value.filter((_, i) => i !== index))
  const current = flavors.value[index]
  if (current?.name && !choices.some(item => item.name === current.name)) choices.unshift({ name: current.name, value: [...current.value] })
  return choices
}
function choose(name: string, index: number) {
  const preset = options(index).find(item => item.name === name)
  if (preset) flavors.value.splice(index, 1, { name: preset.name, value: [...preset.value] })
}
</script>

<template>
  <fieldset
    class="flavor-editor"
    :disabled="disabled"
  >
    <legend>口味做法配置</legend>
    <p class="management-form-hint">
      可选择甜味、温度、忌口或辣度，并删除不提供的选项。
    </p>
    <div
      v-for="(flavor, index) in flavors"
      :key="index"
      class="flavor-row"
    >
      <div class="flavor-name">
        <label :for="`dish-flavor-${index}`">口味名</label>
        <ElSelect
          :id="`dish-flavor-${index}`"
          :model-value="flavor.name"
          :disabled="disabled"
          placeholder="请选择口味"
          size="large"
          @change="choose($event, index)"
        >
          <ElOption
            v-for="option in options(index)"
            :key="option.name"
            :label="option.name"
            :value="option.name"
          />
        </ElSelect>
      </div>
      <div
        class="flavor-values"
        :aria-label="`${flavor.name || '未选择口味'}的选项`"
      >
        <span
          v-if="!flavor.value.length"
          class="flavor-empty"
        >{{ flavor.name ? '暂无选项' : '选择口味后显示选项' }}</span>
        <ElButton
          v-for="(value, valueIndex) in flavor.value"
          :key="valueIndex"
          class="flavor-value"
          :disabled="disabled"
          :aria-label="`删除${flavor.name}选项${value}`"
          @click="flavor.value.splice(valueIndex, 1)"
        >
          {{ value }}
          <svg
            width="12"
            height="12"
            viewBox="0 0 16 16"
            fill="none"
            aria-hidden="true"
          ><path
            d="m4 4 8 8m0-8-8 8"
            stroke="currentColor"
            stroke-width="1.5"
          /></svg>
        </ElButton>
      </div>
      <ElButton
        link
        type="danger"
        :disabled="disabled"
        :aria-label="`删除第${index + 1}项口味${flavor.name}`"
        @click="flavors.splice(index, 1)"
      >
        删除口味
      </ElButton>
    </div>
    <ElButton
      v-if="flavors.length < 4 && flavorOptions(flavors).length"
      :disabled="disabled"
      class="flavor-add"
      @click="flavors.push({ name: '', value: [] })"
    >
      添加口味
    </ElButton>
  </fieldset>
</template>

<style scoped>
.flavor-editor { min-width: 0; margin: 0 0 var(--jz-space-6); padding: 0; border: 0; }
legend { padding: 0; color: var(--jz-text-primary); font-size: var(--jz-font-control); }
.flavor-editor > p { margin-bottom: var(--jz-space-3); }
.flavor-row { display: flex; align-items: flex-end; flex-wrap: wrap; gap: var(--jz-space-3); padding-block: var(--jz-space-4); border-bottom: 1px solid var(--jz-border); }
.flavor-name { display: grid; gap: var(--jz-space-2); width: 160px; font-size: var(--jz-font-control); }
.flavor-values { display: flex; align-items: center; flex: 1; flex-wrap: wrap; gap: var(--jz-space-2); min-width: 180px; min-height: var(--jz-control-height-large); }
.flavor-value { gap: var(--jz-space-2); margin-left: 0; }
.flavor-empty { color: var(--jz-text-secondary); font-size: var(--jz-font-control); }
.flavor-add { margin-top: var(--jz-space-4); }
@media (max-width: 767px) { .flavor-name { width: 100%; } .flavor-values { flex-basis: 100%; min-width: 0; } }
</style>
