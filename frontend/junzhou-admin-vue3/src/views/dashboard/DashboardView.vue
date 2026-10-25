<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElButton } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import DataState from '@/components/DataState.vue'
import { dashboardApi as api } from '@/api'
import { createDashboardResource, dashboardCount, dashboardMoney, dashboardRate, dashboardOrderLinks } from '@/utils/dashboard'
import DashboardOrders from './DashboardOrders.vue'

const business = createDashboardResource(api.getBusinessData)
const orders = createDashboardResource(api.getOrdersOverview)
const dishes = createDashboardResource(api.getDishesOverview)
const setmeals = createDashboardResource(api.getSetmealsOverview)
const resources = [business, orders, dishes, setmeals]
const refreshing = computed(() => resources.some(resource => resource.state.loading))
const date = ref(new Date().toLocaleDateString('zh-CN'))
const orderList = ref<InstanceType<typeof DashboardOrders>>()
const metrics = computed(() => [
  { label: '营业额', value: dashboardMoney(business.state.data?.turnover) },
  { label: '有效订单', value: dashboardCount(business.state.data?.validOrderCount) },
  { label: '订单完成率', value: dashboardRate(business.state.data?.orderCompletionRate) },
  { label: '平均客单价', value: dashboardMoney(business.state.data?.unitPrice) },
  { label: '新增用户', value: dashboardCount(business.state.data?.newUsers) },
])
const salesSections = [
  { key: 'dish', title: '菜品总览', label: '菜品', path: '/dish', resource: dishes },
  { key: 'setmeal', title: '套餐总览', label: '套餐', path: '/setmeal', resource: setmeals },
]
async function refreshOverview() {
  date.value = new Date().toLocaleDateString('zh-CN')
  await Promise.all(resources.map(resource => resource.load()))
}
async function refresh() { await Promise.all([refreshOverview(), orderList.value?.refresh()]) }
void refreshOverview()
onBeforeUnmount(() => resources.forEach(resource => resource.dispose()))
</script>

<template>
  <div class="dashboard-page">
    <PageHeader title="工作台">
      <ElButton
        :loading="refreshing"
        :disabled="orderList?.busy"
        @click="refresh"
      >
        刷新工作台
      </ElButton>
    </PageHeader>
    <section
      class="management-panel dashboard-section"
      aria-labelledby="dashboard-business-title"
    >
      <header class="dashboard-section-heading">
        <h2 id="dashboard-business-title">
          今日数据 <small>{{ date }}</small>
        </h2>
        <RouterLink to="/statistics">
          详细数据
        </RouterLink>
      </header>
      <DataState
        v-if="business.state.loading"
        kind="loading"
        title="正在加载今日数据"
      />
      <DataState
        v-else-if="business.state.error"
        kind="error"
        title="今日数据加载失败"
        :description="business.state.error"
      >
        <ElButton @click="business.load">
          重新加载今日数据
        </ElButton>
      </DataState>
      <dl
        v-else
        class="dashboard-metrics"
      >
        <div
          v-for="metric in metrics"
          :key="metric.label"
        >
          <dt>{{ metric.label }}</dt><dd>{{ metric.value }}</dd>
        </div>
      </dl>
    </section>
    <section
      class="management-panel dashboard-section"
      aria-labelledby="dashboard-orders-title"
    >
      <header class="dashboard-section-heading">
        <h2 id="dashboard-orders-title">
          订单管理 <small>{{ date }}</small>
        </h2>
        <RouterLink to="/order">
          订单明细
        </RouterLink>
      </header>
      <DataState
        v-if="orders.state.loading"
        kind="loading"
        title="正在加载今日订单数量"
      />
      <DataState
        v-else-if="orders.state.error"
        kind="error"
        title="订单数量加载失败"
        :description="orders.state.error"
      >
        <ElButton @click="orders.load">
          重新加载订单数量
        </ElButton>
      </DataState>
      <dl
        v-else
        class="dashboard-metrics dashboard-order-metrics"
      >
        <div
          v-for="item in dashboardOrderLinks"
          :key="item.key"
        >
          <dt>{{ item.label }}</dt>
          <dd>
            <RouterLink
              :to="item.status ? { path: '/order', query: { status: item.status } } : '/order'"
              :aria-label="`${item.label}：${dashboardCount(orders.state.data?.[item.key])}，查看订单`"
            >
              {{ dashboardCount(orders.state.data?.[item.key]) }}
            </RouterLink>
          </dd>
        </div>
      </dl>
    </section>
    <div class="dashboard-sales-grid">
      <section
        v-for="section in salesSections"
        :key="section.key"
        class="management-panel dashboard-section"
        :aria-labelledby="`dashboard-${section.key}-title`"
      >
        <header class="dashboard-section-heading">
          <h2 :id="`dashboard-${section.key}-title`">
            {{ section.title }}
          </h2>
          <RouterLink :to="section.path">
            {{ section.label }}管理
          </RouterLink>
        </header>
        <DataState
          v-if="section.resource.state.loading"
          kind="loading"
          :title="`正在加载${section.title}`"
        />
        <DataState
          v-else-if="section.resource.state.error"
          kind="error"
          :title="`${section.title}加载失败`"
          :description="section.resource.state.error"
        >
          <ElButton @click="section.resource.load">
            重新加载{{ section.title }}
          </ElButton>
        </DataState>
        <dl
          v-else
          class="dashboard-metrics dashboard-sales-metrics"
        >
          <div><dt>已启售</dt><dd>{{ dashboardCount(section.resource.state.data?.sold) }}</dd></div>
          <div><dt>已停售</dt><dd>{{ dashboardCount(section.resource.state.data?.discontinued) }}</dd></div>
        </dl>
        <div class="dashboard-add">
          <RouterLink :to="`${section.path}/add`">
            新增{{ section.label }}
          </RouterLink>
        </div>
      </section>
    </div>
    <DashboardOrders
      ref="orderList"
      @changed="refreshOverview"
    />
  </div>
</template>

<style scoped>
.dashboard-page { min-width: 0; }
.dashboard-section { margin-bottom: var(--jz-space-4); padding: var(--jz-space-6); }
.dashboard-section-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--jz-space-3); }
.dashboard-section-heading h2 { margin: 0; font-size: var(--jz-font-section); font-weight: 600; }
.dashboard-section-heading small { display: inline-block; margin-left: var(--jz-space-2); color: var(--jz-text-secondary); font-size: var(--jz-font-control); font-weight: 400; }
.dashboard-page a { color: var(--jz-brand-primary); text-underline-offset: 3px; }
.dashboard-section-heading a, .dashboard-add a { font-size: var(--jz-font-control); }
.dashboard-metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: var(--jz-space-4); margin: var(--jz-space-6) 0 0; }
.dashboard-metrics dt { color: var(--jz-text-secondary); font-size: var(--jz-font-control); }
.dashboard-metrics dd { margin: var(--jz-space-2) 0 0; font-size: var(--jz-font-title); font-weight: 600; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.dashboard-sales-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--jz-space-4); }
.dashboard-sales-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.dashboard-add { margin-top: var(--jz-space-4); text-align: right; }
.dashboard-section :deep(.data-state) { min-height: 140px; padding: var(--jz-space-4) 0; }
@media (max-width: 1100px) { .dashboard-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); } .dashboard-sales-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 767px) { .dashboard-section { padding: var(--jz-space-4); } .dashboard-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } .dashboard-sales-grid { grid-template-columns: minmax(0, 1fr); gap: 0; } }
</style>
