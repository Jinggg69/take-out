<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { ElButton, ElDatePicker, ElTabs, ElTabPane } from 'element-plus'
import { pointsApi } from '@/api'
import type { PointsOverview } from '@/api/points'
import PageHeader from '@/components/PageHeader.vue'
import DataState from '@/components/DataState.vue'
import { pendingWrites } from '@/stores/workspace'
import SettingsView from '@/views/member/SettingsView.vue'
import { unwrap, useMemberLoad } from '@/views/member/shared'
import PointsAccounts from './PointsAccounts.vue'
import PointsLedger from './PointsLedger.vue'
import './points.scss'

const route = useRoute()
const router = useRouter()
const tab = computed(() => ['accounts', 'ledger', 'rules'].includes(String(route.query.tab)) ? String(route.query.tab) : 'overview')
const dates = ref<[string, string] | null>(null)
const { value: overview, error, loading, load } = useMemberLoad<PointsOverview | undefined>(
  () => undefined,
  async () => unwrap(await pointsApi.overview({ startDate: dates.value?.[0], endDate: dates.value?.[1] })),
)

const balances = [
  { key: 'availablePoints', label: '可用积分' },
  { key: 'frozenPoints', label: '冻结积分' },
  { key: 'pointsDebt', label: '待扣积分' },
] as const
const movements = [
  { key: 'creditedPoints', label: '实际记入' },
  { key: 'debitedPoints', label: '实际扣减' },
] as const

function selectTab(value: string | number) {
  if (pendingWrites.value || value === tab.value) return
  void router.replace({ query: value === 'overview' ? {} : { tab: value } })
}

onMounted(load)
onBeforeRouteLeave(() => pendingWrites.value === 0)
onBeforeRouteUpdate(() => pendingWrites.value === 0)
</script>

<template>
  <div class="points-page">
    <PageHeader title="积分管理">
      <span
        v-if="overview?.environment === 'MOCK'"
        class="points-environment"
      >模拟环境</span>
      <span
        v-else-if="overview?.environment === 'WECHAT'"
        class="points-environment"
      >微信支付环境</span>
    </PageHeader>
    <ElTabs
      :model-value="tab"
      @update:model-value="selectTab"
    >
      <ElTabPane
        label="积分概览"
        name="overview"
        :disabled="pendingWrites > 0"
      >
        <section
          v-if="tab === 'overview'"
          class="management-panel points-overview"
        >
          <DataState
            v-if="loading || !overview && !error"
            kind="loading"
            title="正在加载积分概览"
          />
          <DataState
            v-else-if="error"
            kind="error"
            title="积分概览加载失败"
            :description="error"
          >
            <ElButton @click="load">
              重新加载
            </ElButton>
          </DataState>
          <template v-else-if="overview">
            <h2>当前积分</h2>
            <dl class="points-metrics">
              <div
                v-for="metric in balances"
                :key="metric.key"
              >
                <dt>{{ metric.label }}</dt>
                <dd>{{ overview[metric.key] }}</dd>
              </div>
            </dl>
            <h2>积分收支</h2>
            <form
              class="management-filter points-filters"
              @submit.prevent="load"
            >
              <ElDatePicker
                v-model="dates"
                aria-label="积分收支日期"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
              />
              <ElButton native-type="submit">
                查询
              </ElButton>
            </form>
            <p class="points-note">
              未选择日期时显示近 30 天。待扣积分变动单独计入流水。
            </p>
            <dl class="points-metrics">
              <div
                v-for="metric in movements"
                :key="metric.key"
              >
                <dt>{{ metric.label }}</dt>
                <dd>{{ overview[metric.key] }}</dd>
              </div>
            </dl>
          </template>
        </section>
      </ElTabPane>
      <ElTabPane
        label="会员积分"
        name="accounts"
        lazy
        :disabled="pendingWrites > 0"
      >
        <PointsAccounts @changed="load" />
      </ElTabPane>
      <ElTabPane
        label="积分流水"
        name="ledger"
        :disabled="pendingWrites > 0"
      >
        <PointsLedger v-if="tab === 'ledger'" />
      </ElTabPane>
      <ElTabPane
        label="积分规则"
        name="rules"
        :disabled="pendingWrites > 0"
      >
        <SettingsView
          v-if="tab === 'rules'"
          embedded
        />
      </ElTabPane>
    </ElTabs>
  </div>
</template>
