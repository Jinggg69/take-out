<script setup lang="ts">
import { ElTable, ElTableColumn, ElAvatar } from 'element-plus'
import { useAppStore } from '@/stores'
import type { MemberRow } from '@/api/member'
import { display, money, statusLabels, type Column } from './shared'
const app = useAppStore()
defineProps<{ rows: MemberRow[]; columns: Column[] }>()
defineSlots<{ default(props: { row: MemberRow }): unknown }>()
</script>
<template>
  <div
    class="management-table-region"
    role="region"
    aria-label="会员数据，可横向滚动"
    tabindex="0"
  >
    <ElTable
      :data="rows"
      stripe
      row-key="id"
    >
      <ElTableColumn
        v-for="column in columns"
        :key="column.key"
        :label="column.label"
        :min-width="column.width || 130"
      >
        <template #default="{ row }">
          <ElAvatar
            v-if="column.key === 'avatar'"
            :src="row.avatar || undefined"
            :size="36"
          >
            {{ String(row.name || '会员').slice(0, 1) }}
          </ElAvatar>
          <span
            v-else-if="column.key === 'status' && !column.labels && [0, 1].includes(row.status)"
            class="management-status"
            :data-enabled="row.status === 1"
          >{{ row.status === 1 ? '启用' : '停用' }}</span>
          <template v-else>
            {{ column.money ? money(row[column.key]) : column.labels?.[String(row[column.key])] || statusLabels[String(row[column.key])] || display(row[column.key]) }}
          </template>
        </template>
      </ElTableColumn>
      <!-- @vue-generic {MemberRow} -->
      <ElTableColumn
        v-if="$slots.default"
        label="操作"
        min-width="210"
        :fixed="app.isMobile ? undefined : 'right'"
      >
        <template #default="{ row }">
          <div class="management-row-actions">
            <slot :row="row" />
          </div>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>
