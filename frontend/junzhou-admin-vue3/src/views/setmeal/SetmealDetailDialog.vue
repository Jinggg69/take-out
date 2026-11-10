<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { ElButton, ElDialog, ElTable, ElTableColumn } from 'element-plus'
import DataState from '@/components/DataState.vue'
import { setmealApi } from '@/api'
import type { SetmealId } from '@/api/setmeal'
import { formatSetmealPrice } from '@/utils/setmeal'
import { createSetmealDetail } from './detail-state'

const props = defineProps<{ id: SetmealId; name: string }>()
const emit = defineEmits<{ close: [] }>()
const detail = createSetmealDetail(setmealApi)
const { state } = detail
watch(() => props.id, id => { void detail.load(id) }, { immediate: true })
onBeforeUnmount(detail.dispose)
function close() { detail.dispose(); emit('close') }
</script>

<template>
  <ElDialog
    :model-value="true"
    :title="`套餐菜品明细 · ${state.name || props.name}`"
    width="min(720px, calc(100vw - 24px))"
    class="jz-dialog setmeal-detail-dialog"
    align-center
    @close="close"
  >
    <DataState
      v-if="state.loading"
      kind="loading"
      title="正在加载套餐菜品明细"
      description="请稍候"
    />
    <DataState
      v-else-if="state.error"
      kind="error"
      title="套餐菜品明细加载失败"
      :description="state.error"
    >
      <ElButton @click="detail.load(props.id)">
        重新加载
      </ElButton>
    </DataState>
    <ElTable
      v-else-if="state.dishes.length"
      :data="state.dishes"
      max-height="min(480px, 55vh)"
      class="setmeal-detail-table"
      aria-label="套餐菜品明细"
      stripe
    >
      <ElTableColumn
        prop="name"
        label="菜品名称"
        min-width="100"
      />
      <!-- @vue-generic {import('@/api/setmeal').SetmealDish} -->
      <ElTableColumn
        label="单价（元）"
        width="112"
        align="right"
      >
        <template #default="{ row }">
          {{ formatSetmealPrice(row.price) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="copies"
        label="份数"
        width="72"
        align="right"
      />
    </ElTable>
    <DataState
      v-else
      title="该套餐暂无菜品"
    />
    <template #footer>
      <ElButton @click="close">
        关闭
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.setmeal-detail-table { font-variant-numeric: tabular-nums; }
.setmeal-detail-table :deep(.cell) { overflow-wrap: anywhere; }
</style>
