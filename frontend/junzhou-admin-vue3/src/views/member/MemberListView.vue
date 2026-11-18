<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElInput, ElSelect, ElOption, ElButton, ElDatePicker, ElMessage, ElMessageBox } from 'element-plus'
import { memberApi } from '@/api'
import type { MemberPage, MemberRow } from '@/api/member'
import { workspace } from '@/stores/workspace'
import PageHeader from '@/components/PageHeader.vue'
import DataState from '@/components/DataState.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import MemberTable from './MemberTable.vue'
import MemberEditor from './MemberEditor.vue'
import MemberDetailDrawer from './MemberDetailDrawer.vue'
import AdjustmentDialog from './AdjustmentDialog.vue'
import IssueCouponDialog from './IssueCouponDialog.vue'
import { unwrap, useMemberLoad, type Column } from './shared'
import { errorMessage } from '@/utils/error-message'
import './member.scss'
const keyword = ref(''), status = ref<number>(), levelId = ref(''), tagId = ref(''), dates = ref<string[]>([]), page = ref(1), pageSize = ref(10)
const levels = ref<MemberRow[]>([]), tags = ref<MemberRow[]>([]), filterError = ref('')
const { value: result, loading, error, load } = useMemberLoad<MemberPage>(() => ({ records: [], total: 0 }), async () => unwrap(await memberApi.page({ page: page.value, pageSize: pageSize.value, keyword: keyword.value || undefined, status: status.value, levelId: levelId.value || undefined, tagId: tagId.value || undefined, startDate: dates.value?.[0], endDate: dates.value?.[1] })))
const editorOpen = ref(false), record = ref<MemberRow>(), detailOpen = ref(false), detailId = ref(''), adjustmentOpen = ref(false), adjustmentKind = ref<'points' | 'balance'>('points'), issueOpen = ref(false), pending = ref(false)
const columns: Column[] = [{ key: 'avatar', label: '头像', width: 70 }, { key: 'memberNo', label: '会员号', width: 170 }, { key: 'name', label: '姓名' }, { key: 'phone', label: '手机号', width: 160 }, { key: 'levelName', label: '等级' }, { key: 'points', label: '积分' }, { key: 'balance', label: '余额', money: true }, { key: 'totalSpent', label: '累计消费', money: true }, { key: 'storeName', label: '开卡门店' }, { key: 'createTime', label: '开卡时间', width: 180 }, { key: 'status', label: '状态' }]
async function loadFilters() { try { const res = await Promise.all([memberApi.list('levels'), memberApi.list('tags')]); levels.value = unwrap(res[0]); tags.value = unwrap(res[1]); filterError.value = '' } catch (e) { filterError.value = errorMessage(e, '筛选项加载失败') } }
onMounted(() => { void load(); void loadFilters() })
watch(workspace.revision, () => { editorOpen.value = false; detailOpen.value = false; adjustmentOpen.value = false; issueOpen.value = false; levels.value = []; tags.value = []; void loadFilters() })
function edit(row?: MemberRow) { record.value = row; editorOpen.value = true }
function adjust(row: MemberRow, kind: 'points' | 'balance') { record.value = row; adjustmentKind.value = kind; adjustmentOpen.value = true }
async function toggle(row: MemberRow) {
  if (pending.value || !workspace.can('member:status')) return
  pending.value = true
  try { await ElMessageBox.confirm(`确认${row.status === 1 ? '停用' : '启用'}会员 ${row.memberNo}？停用后保留历史与退款能力。`, '调整会员状态', { type: 'warning' }); unwrap(await memberApi.status(row.id, row.status === 1 ? 0 : 1, row.version)); ElMessage.success('会员状态已更新'); await load() }
  catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(errorMessage(e, '状态更新失败')) }
  finally { pending.value = false }
}
</script>
<template>
  <div class="member-page">
    <PageHeader title="会员列表">
      <ElButton
        v-if="workspace.can('member:create')"
        type="primary"
        @click="edit()"
      >
        新增线下会员
      </ElButton>
    </PageHeader>
    <section class="management-panel">
      <form
        class="management-filter"
        @submit.prevent="page = 1; load()"
      >
        <ElInput
          v-model="keyword"
          aria-label="会员搜索"
          placeholder="会员号 / 姓名 / 手机号"
          clearable
        />
        <ElSelect
          v-model="levelId"
          aria-label="会员等级"
          placeholder="全部等级"
          clearable
        >
          <ElOption
            v-for="row in levels"
            :key="row.id"
            :label="String(row.name)"
            :value="row.id"
          />
        </ElSelect>
        <ElSelect
          v-model="tagId"
          aria-label="会员标签"
          placeholder="全部标签"
          clearable
        >
          <ElOption
            v-for="row in tags"
            :key="row.id"
            :label="String(row.name)"
            :value="row.id"
          />
        </ElSelect>
        <ElSelect
          v-model="status"
          aria-label="会员状态"
          placeholder="全部状态"
          clearable
        >
          <ElOption
            label="启用"
            :value="1"
          /><ElOption
            label="停用"
            :value="0"
          />
        </ElSelect>
        <ElDatePicker
          v-model="dates"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          start-placeholder="开卡开始时间"
          end-placeholder="开卡结束时间"
        />
        <ElButton
          native-type="submit"
          :loading="loading"
        >
          查询
        </ElButton>
      </form>
      <p
        v-if="filterError"
        role="alert"
        class="member-hint"
      >
        {{ filterError }} <ElButton
          link
          type="primary"
          @click="loadFilters"
        >
          重试筛选项
        </ElButton>
      </p>
      <DataState
        v-if="loading"
        kind="loading"
        title="正在加载会员"
      />
      <DataState
        v-else-if="error"
        kind="error"
        title="会员加载失败"
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
        <template #default="{ row }">
          <ElButton
            link
            type="primary"
            @click="detailId = row.id; detailOpen = true"
          >
            详情
          </ElButton>
          <ElButton
            v-if="workspace.can('member:update')"
            link
            type="primary"
            @click="edit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-if="workspace.can('member:status')"
            link
            :disabled="pending"
            @click="toggle(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </ElButton>
          <ElButton
            v-if="workspace.can('member:point')"
            link
            type="primary"
            @click="adjust(row, 'points')"
          >
            积分
          </ElButton>
          <ElButton
            v-if="workspace.can('member:balance')"
            link
            type="primary"
            @click="adjust(row, 'balance')"
          >
            余额
          </ElButton>
          <ElButton
            v-if="workspace.can('coupon:issue') && row.status === 1"
            link
            type="primary"
            @click="record = row; issueOpen = true"
          >
            发券
          </ElButton>
        </template>
      </MemberTable>
      <DataState
        v-else
        title="暂无匹配的会员"
        description="可调整筛选条件，或使用上方按钮登记线下会员。"
      />
      <PaginationBar
        :page="page"
        :page-size="pageSize"
        :total="result.total"
        :loading="loading"
        @page-change="page = $event; load()"
        @size-change="pageSize = $event; page = 1; load()"
      />
    </section>
    <MemberEditor
      v-model="editorOpen"
      :record="record"
      @saved="load"
    />
    <MemberDetailDrawer
      v-model="detailOpen"
      :member-id="detailId"
    />
    <AdjustmentDialog
      v-model="adjustmentOpen"
      :member="record"
      :kind="adjustmentKind"
      @saved="load"
    />
    <IssueCouponDialog
      v-model="issueOpen"
      :member-id="record?.id"
      @saved="load"
    />
  </div>
</template>
