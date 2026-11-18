<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElCheckbox, ElInput, ElOption, ElSelect } from 'element-plus'
import { memberApi, pointsApi } from '@/api'
import type { MemberRow } from '@/api/member'
import { POINTS_BATCH_LIMIT, setPointsSelection, type PointsAccount, type PointsAccountQuery } from '@/api/points'
import DataState from '@/components/DataState.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { workspace } from '@/stores/workspace'
import AdjustmentDialog from '@/views/member/AdjustmentDialog.vue'
import MemberTable from '@/views/member/MemberTable.vue'
import { unwrap, useMemberLoad, type Column } from '@/views/member/shared'
import { errorMessage } from '@/utils/error-message'
import PointsGrantDialog from './PointsGrantDialog.vue'

const emit = defineEmits<{ changed: [] }>()
const keyword = ref(''), levelId = ref<string>(), tagId = ref<string>(), status = ref<0 | 1>()
const page = ref(1), pageSize = ref(10)
const filters = ref<Omit<PointsAccountQuery, 'page' | 'pageSize'>>({})
const selected = ref(new Map<string, PointsAccount>())
const members = computed(() => [...selected.value.values()])
const batchOpen = ref(false), singleOpen = ref(false), selectedMember = ref<PointsAccount>()
const selectionError = ref('')
const canBatch = computed(() => workspace.canTenant('member:point:batch'))
const canAdjust = computed(() => workspace.canTenant('member:point'))
const { value: result, loading, error, load } = useMemberLoad(
  () => ({ records: [] as PointsAccount[], total: 0 }),
  async () => unwrap(await pointsApi.accounts({ ...filters.value, page: page.value, pageSize: pageSize.value })),
)
const { value: options, error: optionsError, load: loadOptions } = useMemberLoad(
  () => ({ levels: [] as MemberRow[], tags: [] as MemberRow[] }),
  async () => {
    const [levels, tags] = await Promise.all([memberApi.list('levels'), memberApi.list('tags')])
    return { levels: unwrap(levels), tags: unwrap(tags) }
  },
)
const columns: Column[] = [
  { key: 'memberNo', label: '会员号', width: 160 }, { key: 'name', label: '姓名' },
  { key: 'phone', label: '手机号', width: 145 }, { key: 'levelName', label: '等级' },
  { key: 'availablePoints', label: '可用积分' }, { key: 'frozenPoints', label: '冻结积分' },
  { key: 'pointsDebt', label: '待扣积分' }, { key: 'status', label: '状态', width: 90 },
]

function search() {
  filters.value = { keyword: keyword.value.trim() || undefined, levelId: levelId.value, tagId: tagId.value, status: status.value }
  page.value = 1
  void load()
}
function clearSelection() {
  selected.value = new Map()
  selectionError.value = ''
}
function toggle(row: MemberRow, checked: boolean) {
  const account = result.value.records.find(member => member.id === row.id)
  if (!account) return
  try {
    selected.value = setPointsSelection(selected.value, account, checked)
    selectionError.value = ''
  } catch (cause) { selectionError.value = errorMessage(cause, '选择会员失败') }
}
function openAdjustment(row: MemberRow) {
  selectedMember.value = result.value.records.find(member => member.id === row.id)
  if (selectedMember.value) singleOpen.value = true
}
function saved() {
  emit('changed')
  void load()
}
watch(workspace.revision, () => {
  clearSelection()
  batchOpen.value = false
})
onMounted(() => { void load(); void loadOptions() })
</script>

<template>
  <section class="management-panel">
    <form
      class="management-filter points-filters"
      @submit.prevent="search"
    >
      <ElInput
        v-model="keyword"
        aria-label="会员关键词"
        placeholder="会员号 / 姓名 / 手机号"
        clearable
      />
      <ElSelect
        v-model="levelId"
        aria-label="会员等级"
        placeholder="全部等级"
        clearable
        :value-on-clear="undefined"
      >
        <ElOption
          v-for="level in options.levels"
          :key="level.id"
          :label="level.name ?? level.id"
          :value="level.id"
        />
      </ElSelect>
      <ElSelect
        v-model="tagId"
        aria-label="会员标签"
        placeholder="全部标签"
        clearable
        :value-on-clear="undefined"
      >
        <ElOption
          v-for="tag in options.tags"
          :key="tag.id"
          :label="tag.name ?? tag.id"
          :value="tag.id"
        />
      </ElSelect>
      <ElSelect
        v-model="status"
        aria-label="会员状态"
        placeholder="全部状态"
        clearable
        :value-on-clear="undefined"
      >
        <ElOption
          label="启用"
          :value="1"
        />
        <ElOption
          label="停用"
          :value="0"
        />
      </ElSelect>
      <ElButton
        native-type="submit"
        :loading="loading"
      >
        查询
      </ElButton>
    </form>
    <ElAlert
      v-if="optionsError"
      :title="optionsError"
      type="warning"
      :closable="false"
    >
      <ElButton
        link
        @click="loadOptions"
      >
        重新加载筛选项
      </ElButton>
    </ElAlert>
    <div
      v-if="canBatch"
      class="points-actions"
    >
      <ElButton
        type="primary"
        :disabled="selected.size === 0"
        @click="batchOpen = true"
      >
        批量赠分
      </ElButton>
      <span aria-live="polite">已选 {{ selected.size }} / {{ POINTS_BATCH_LIMIT }} 人</span>
      <ElButton
        v-if="selected.size"
        link
        @click="clearSelection"
      >
        清空选择
      </ElButton>
    </div>
    <ElAlert
      v-if="selectionError"
      :title="selectionError"
      type="warning"
      :closable="false"
    />
    <DataState
      v-if="loading"
      kind="loading"
      title="正在查询会员积分"
    />
    <DataState
      v-else-if="error"
      kind="error"
      title="会员积分加载失败"
      :description="error"
    >
      <ElButton @click="load">
        重新加载
      </ElButton>
    </DataState>
    <MemberTable
      v-else-if="result.records.length"
      :rows="result.records"
      :columns="columns"
    >
      <template
        v-if="canBatch || canAdjust"
        #default="{ row }"
      >
        <ElCheckbox
          v-if="canBatch"
          :model-value="selected.has(row.id)"
          :disabled="!selected.has(row.id) && (row.status !== 1 || selected.size >= POINTS_BATCH_LIMIT)"
          :aria-label="`选择会员 ${row.memberNo}`"
          @change="toggle(row, $event === true)"
        >
          选择
        </ElCheckbox>
        <ElButton
          v-if="canAdjust"
          link
          type="primary"
          @click="openAdjustment(row)"
        >
          调整积分
        </ElButton>
      </template>
    </MemberTable>
    <DataState
      v-else
      title="暂无会员积分记录"
      description="可调整筛选条件后重新查询。"
    />
    <PaginationBar
      :page="page"
      :page-size="pageSize"
      :total="result.total"
      :loading="loading"
      @page-change="page = $event; load()"
      @size-change="pageSize = $event; page = 1; load()"
    />
    <PointsGrantDialog
      v-model="batchOpen"
      :members="members"
      @saved="clearSelection(); saved()"
    />
    <AdjustmentDialog
      v-model="singleOpen"
      kind="points"
      :member="selectedMember"
      @saved="saved"
    />
  </section>
</template>
