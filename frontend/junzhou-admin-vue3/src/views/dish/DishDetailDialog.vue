<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { ElButton, ElDialog, ElImage } from 'element-plus'
import DataState from '@/components/DataState.vue'
import { dishApi } from '@/api'
import type { DishCategory, DishId } from '@/api/dish'
import { formatDishPrice } from '@/utils/dish'
import { createDishDetail } from './detail-state'

const props = defineProps<{ id: DishId; name: string; categories: DishCategory[] }>()
const emit = defineEmits<{ close: [] }>()
const detail = createDishDetail(dishApi)
const { state } = detail
watch(() => props.id, id => { void detail.load(id) }, { immediate: true })
onBeforeUnmount(detail.dispose)
function close() { detail.dispose(); emit('close') }
</script>

<template>
  <ElDialog
    :model-value="true"
    :title="`菜品详情 · ${state.record?.name || props.name}`"
    width="min(720px, calc(100vw - 24px))"
    class="jz-dialog dish-detail-dialog"
    align-center
    @close="close"
  >
    <DataState
      v-if="state.loading"
      kind="loading"
      title="正在加载菜品详情"
      description="请稍候"
    />
    <DataState
      v-else-if="state.error"
      kind="error"
      title="菜品详情加载失败"
      :description="state.error"
    >
      <ElButton @click="detail.load(props.id)">
        重新加载
      </ElButton>
    </DataState>
    <div
      v-else-if="state.record"
      class="dish-detail-content"
      tabindex="0"
      role="region"
      aria-label="菜品详情内容"
    >
      <div class="dish-detail-summary">
        <ElImage
          class="dish-detail-image"
          :src="state.record.image"
          :alt="`${state.record.name}图片`"
          fit="contain"
        >
          <template #error>
            <span class="dish-detail-image-empty">暂无图片</span>
          </template>
        </ElImage>
        <dl class="dish-detail-fields">
          <div>
            <dt>菜品分类</dt>
            <dd>{{ state.record.categoryName || props.categories.find(item => String(item.id) === String(state.record?.categoryId))?.name || '—' }}</dd>
          </div>
          <div>
            <dt>售价（元）</dt>
            <dd class="dish-detail-price">
              {{ formatDishPrice(state.record.price) }}
            </dd>
          </div>
          <div>
            <dt>售卖状态</dt>
            <dd>
              <span
                v-if="String(state.record.status) === '0' || String(state.record.status) === '1'"
                class="management-status"
                :data-enabled="String(state.record.status) === '1'"
              >{{ String(state.record.status) === '1' ? '启售' : '停售' }}</span>
              <span v-else>—</span>
            </dd>
          </div>
          <div>
            <dt>最后操作时间</dt>
            <dd>{{ state.record.updateTime || '—' }}</dd>
          </div>
        </dl>
      </div>
      <section class="dish-detail-section">
        <h3>菜品描述</h3>
        <p class="dish-detail-description">
          {{ state.record.description || '暂无描述' }}
        </p>
      </section>
      <section class="dish-detail-section">
        <h3>口味选择</h3>
        <dl
          v-if="state.flavors.length"
          class="dish-detail-fields dish-detail-flavors"
        >
          <div
            v-for="(flavor, index) in state.flavors"
            :key="index"
          >
            <dt>{{ flavor.name }}</dt>
            <dd>{{ flavor.value.length ? flavor.value.join('、') : '暂无选项' }}</dd>
          </div>
        </dl>
        <p v-else>
          暂无口味设置
        </p>
      </section>
    </div>
    <template #footer>
      <ElButton @click="close">
        关闭
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.dish-detail-content { max-height: 60vh; overflow: auto; padding: var(--jz-space-1); overflow-wrap: anywhere; }
.dish-detail-content:focus-visible { outline: 2px solid var(--jz-brand-primary); outline-offset: 2px; }
.dish-detail-summary { display: grid; grid-template-columns: 160px minmax(0, 1fr); gap: var(--jz-space-6); }
.dish-detail-image { display: block; width: 160px; height: 120px; border-radius: var(--jz-radius-control); background: var(--jz-page-background); }
.dish-detail-image-empty { display: grid; place-items: center; height: 100%; color: var(--jz-text-secondary); }
.dish-detail-fields { margin: 0; }
.dish-detail-fields > div { display: grid; grid-template-columns: 100px minmax(0, 1fr); gap: var(--jz-space-3); padding-bottom: var(--jz-space-3); }
.dish-detail-fields dt { color: var(--jz-text-secondary); }
.dish-detail-fields dd { min-width: 0; margin: 0; }
.dish-detail-price { font-variant-numeric: tabular-nums; }
.dish-detail-section { margin-top: var(--jz-space-6); }
.dish-detail-section h3 { margin: 0 0 var(--jz-space-3); font-size: var(--jz-font-body); font-weight: 600; }
.dish-detail-section p { margin: 0; }
.dish-detail-description { white-space: pre-wrap; }
@media (max-width: 479px) { .dish-detail-summary { grid-template-columns: minmax(0, 1fr); gap: var(--jz-space-4); } }
</style>
