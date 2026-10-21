<script setup lang="ts">
import { ElPagination } from 'element-plus'
withDefaults(defineProps<{ page: number; pageSize: number; total: number; loading?: boolean }>(), { loading: false })
const emit = defineEmits<{ pageChange: [page: number]; sizeChange: [size: number] }>()
</script>

<template>
  <nav
    class="pagination-bar"
    aria-label="列表分页"
  >
    <ElPagination
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 30, 40]"
      :pager-count="5"
      :disabled="loading"
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="emit('pageChange', $event)"
      @size-change="emit('sizeChange', $event)"
    />
  </nav>
</template>

<style scoped>
.pagination-bar { display: flex; justify-content: flex-end; padding-top: var(--jz-space-6); }
.pagination-bar :deep(.el-pagination) { flex-wrap: wrap; justify-content: flex-end; gap: var(--jz-space-2); white-space: normal; }
.pagination-bar :deep(.el-pagination > *) { margin: 0; }
@media (max-width: 767px) {
  .pagination-bar, .pagination-bar :deep(.el-pagination) { justify-content: flex-start; }
}
</style>
