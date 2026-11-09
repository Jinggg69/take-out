<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElInput, ElMessageBox, ElOption, ElSelect, ElSwitch, ElTable, ElTableColumn } from 'element-plus'
import { storeApi } from '@/api'
import type { MenuType, StoreMenuItem } from '@/api/store'
import { workspace } from '@/stores/workspace'
import { menuPayload } from '@/utils/store-settings'
import { errorMessage } from '@/utils/error-message'

type MenuRow = Omit<StoreMenuItem, 'overridePrice'> & { overridePrice: string }
const emit = defineEmits<{ dirty: [value: boolean]; busy: [value: boolean] }>()
const type = ref<MenuType>('dish'), rows = ref<MenuRow[]>([]), baseline = ref<Record<string, string>>({})
const loading = ref(false), loaded = ref(false), savingId = ref<string | number | null>(null)
const error = ref(''), success = ref(''), errorElement = ref<HTMLElement>()
const dirtyRow = (row: MenuRow) => baseline.value[String(row.id)] !== JSON.stringify(row)
const dirty = computed(() => rows.value.some(dirtyRow))
const busy = computed(() => savingId.value !== null)
watch(dirty, value => emit('dirty', value))
watch(busy, value => emit('busy', value))
const editable = computed(() => workspace.can('shop:update') && !busy.value && !loading.value)
let active = true
async function discard() {
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm('此操作会放弃尚未保存的菜单修改。', '切换或重新加载菜单', { confirmButtonText: '放弃修改并继续', cancelButtonText: '继续编辑', type: 'warning' })
    return true
  } catch { return false }
}
async function showError(message: string) { error.value = message; await nextTick(); errorElement.value?.focus() }
async function load(nextType: MenuType = type.value) {
  if (loading.value || busy.value || !(await discard())) return
  type.value = nextType; loading.value = true; loaded.value = false; error.value = ''; success.value = ''
  rows.value = []; baseline.value = {}
  try {
    const { data } = await storeApi.menu(type.value)
    if (!active) return
    if (String(data.code) !== '1' || !Array.isArray(data.data)) throw new Error(data.msg || '门店菜单加载失败')
    rows.value = data.data.map(row => ({ ...row, overridePrice: row.overridePrice == null ? '' : String(row.overridePrice) }))
    baseline.value = Object.fromEntries(rows.value.map(row => [String(row.id), JSON.stringify(row)])); loaded.value = true
  } catch (e) { if (active) await showError(errorMessage(e, '门店菜单加载失败，请重新加载')) }
  finally { if (active) loading.value = false }
}
async function save(row: MenuRow) {
  if (!editable.value) return
  error.value = ''; success.value = ''
  let payload
  try { payload = menuPayload(row) } catch (e) { await showError(`${row.name}：${errorMessage(e, '请检查输入')}`); return }
  savingId.value = row.id
  try {
    const { data } = await storeApi.saveMenu(type.value, payload)
    if (!active) return
    if (String(data.code) !== '1') throw new Error(data.msg || '菜单保存失败')
    row.overridePrice = payload.overridePrice == null ? '' : String(payload.overridePrice)
    baseline.value[String(row.id)] = JSON.stringify(row)
    success.value = `“${row.name}”的门店菜单设置已保存。`
  } catch (e) { if (active) await showError(`${row.name}：${errorMessage(e, '保存失败，输入已保留，请重试')}`) }
  finally { if (active) savingId.value = null }
}
onMounted(() => load())
onBeforeUnmount(() => { active = false })
</script>

<template>
  <section
    class="management-panel menu-panel"
    aria-label="门店菜单编辑"
  >
    <div class="menu-controls">
      <label for="store-menu-type">菜单类型</label>
      <ElSelect
        id="store-menu-type"
        :model-value="type"
        :disabled="loading || busy"
        @update:model-value="load"
      >
        <ElOption
          label="菜品"
          value="dish"
        />
        <ElOption
          label="套餐"
          value="setmeal"
        />
      </ElSelect>
      <ElButton
        :disabled="loading || busy"
        @click="load()"
      >
        重新加载
      </ElButton>
      <span
        v-if="dirty"
        role="status"
      >有未保存的修改，请逐行保存。</span>
    </div>
    <p class="menu-help">
      目录价格来自统一菜品或套餐。门店售价留空时沿用目录价格；售罄状态、上下架和排序仅影响当前门店。
    </p>
    <div
      v-if="error"
      ref="errorElement"
      tabindex="-1"
    >
      <ElAlert
        :title="error"
        type="error"
        :closable="false"
        role="alert"
      />
    </div>
    <ElAlert
      v-if="success"
      :title="success"
      type="success"
      :closable="false"
      role="status"
    />
    <p
      v-if="loading"
      role="status"
    >
      正在加载门店菜单…
    </p>
    <ElTable
      v-if="loaded"
      :data="rows"
      row-key="id"
      class="menu-table"
      empty-text="暂无此类型的菜品或套餐，请先维护统一目录。"
    >
      <ElTableColumn
        prop="name"
        label="名称"
        min-width="160"
      />
      <ElTableColumn
        label="目录价格（元）"
        width="140"
        align="right"
      >
        <template #default="{ row }">
          {{ Number(row.price).toFixed(2) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        label="门店售价（元）"
        min-width="170"
        align="right"
      >
        <template #default="{ row }">
          <ElInput
            v-model="row.overridePrice"
            type="number"
            placeholder="沿用目录价"
            :disabled="!editable"
            :aria-label="`${row.name}的门店售价，留空沿用目录价`"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        label="上下架"
        width="140"
      >
        <template #default="{ row }">
          <ElSwitch
            v-model="row.onSale"
            :active-value="1"
            :inactive-value="0"
            :disabled="!editable"
            :aria-label="`${row.name}上下架`"
          />
          <span class="menu-state">{{ row.onSale === 1 ? '上架' : '下架' }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn
        label="售罄状态"
        width="150"
      >
        <template #default="{ row }">
          <ElSwitch
            v-model="row.soldOut"
            :active-value="1"
            :inactive-value="0"
            :disabled="!editable"
            :aria-label="`${row.name}是否售罄`"
          />
          <span class="menu-state">{{ row.soldOut === 1 ? '已售罄' : '未售罄' }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn
        label="排序"
        width="110"
        align="right"
      >
        <template #default="{ row }">
          <ElInput
            v-model.number="row.sort"
            type="number"
            :disabled="!editable"
            :aria-label="`${row.name}的排序`"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        v-if="workspace.can('shop:update')"
        label="操作"
        width="110"
        fixed="right"
      >
        <template #default="{ row }">
          <ElButton
            type="primary"
            link
            :loading="savingId === row.id"
            :disabled="!editable || !dirtyRow(row as MenuRow)"
            :aria-label="`保存${row.name}的菜单设置`"
            @click="save(row as MenuRow)"
          >
            保存
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </section>
</template>

<style scoped>
.menu-panel { display: grid; gap: var(--jz-space-4); padding: var(--jz-space-6); min-width: 0; }
.menu-controls { display: flex; align-items: center; flex-wrap: wrap; gap: var(--jz-space-3); }
.menu-controls :deep(.el-select) { width: 160px; }
.menu-controls span, .menu-help { color: var(--jz-text-secondary); }
.menu-help { margin: 0; line-height: 1.6; }
.menu-state { margin-left: var(--jz-space-2); }
.menu-table { width: 100%; font-variant-numeric: tabular-nums; }
.menu-table :deep(input) { text-align: right; }
@media (max-width: 767px) { .menu-panel { padding: var(--jz-space-4); } }
</style>
