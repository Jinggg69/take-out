<template>
  <view class="app-page">
    <view class="app-row"><view class="app-heading">我的订单</view><button class="app-link" @click="openMerchantPicker">切换商家</button></view>
    <AppState v-if="!app.selectedMerchantId" title="先选择要查看的商家" message="订单按商家展示，收货地址可在各商家共用。" action="选择商家" @retry="openMerchantPicker" />
    <template v-else>
      <view class="app-subtitle">{{ merchantName || '当前商家' }} · 仅展示本人的订单</view>
      <AppState v-if="!app.token" title="登录后查看订单" message="使用微信登录，查看当前商家的订单记录。" action="登录并查看" @retry="login" />
      <template v-else>
        <picker :range="storeOptions" range-key="storeName" :value="storeIndex" @change="filterStore"><view class="app-panel app-row filter-picker"><text>门店</text><text>{{ storeOptions[storeIndex]?.storeName || '全部门店' }} · 点击选择</text></view></picker>
        <view v-if="filtersError" class="app-error">{{ filtersError }}<button class="app-link" @click="loadFilters">重试门店筛选</button></view>
        <view class="app-tabs"><button v-for="tab in tabs" :key="tab.key" class="app-tab" :class="{ active: activeTab === tab.key }" @click="filterStatus(tab.key)">{{ tab.label }}</button></view>
        <AppState v-if="loading && !records.length" loading />
        <AppState v-else-if="error && !records.length" title="订单加载失败" :message="error" action="重新加载" @retry="load(true)" />
        <AppState v-else-if="!records.length" title="暂无符合条件的订单" message="试试其他状态或门店筛选。" action="查看全部订单" @retry="resetFilters" />
        <view v-for="order in records" :key="order.orderId" class="app-panel order-panel">
          <view class="app-row"><view class="app-title app-grow">{{ order.storeName || '历史门店' }}</view><text class="app-status">{{ orderStatus(order) }}</text></view>
          <view class="app-meta">{{ order.orderTime }} · {{ fulfillmentName(order.fulfillmentType) }}</view>
          <view class="app-divider" />
          <view v-for="(item, index) in (order.items || []).slice(0, 3)" :key="index" class="app-row order-item"><text class="app-grow">{{ item.name }}</text><text class="app-muted">×{{ item.number }}</text></view>
          <view v-if="(order.items || []).length > 3" class="app-meta">另有 {{ order.items.length - 3 }} 项菜品</view>
          <view class="app-row order-total"><text class="app-muted">共 {{ order.totalQuantity }} 件</text><text class="app-price">¥{{ order.amount == null ? '—' : order.amount }}</text></view>
          <view v-if="order.storeAvailable === false" class="app-meta">原门店已停用，仍可查看订单详情。</view>
          <button class="app-button app-button-secondary" @click="detail(order)">查看订单详情</button>
        </view>
        <view v-if="error && records.length" class="app-error">{{ error }}</view>
        <button v-if="records.length < total" class="app-link" :loading="loading" :disabled="loading" @click="load()">{{ error ? '重试加载更多' : '加载更多订单' }}</button>
        <view v-else-if="records.length" class="app-muted order-end">已展示全部订单</view>
      </template>
    </template>
    <view v-if="actionError" class="app-error">{{ actionError }}</view>
  </view>
</template>
<script setup>
import { computed, ref } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/index.js'
import { getMyOrders, getOrderFilters } from '@/pages/api/api.js'
import { openMerchantPicker } from '@/services/context.js'
import { usePagination } from '@/components/app-discovery/pagination.js'
import AppState from '@/components/app-state/index.vue'
const app = useAppStore(), activeTab = ref('all'), storeIndex = ref(0), filtersError = ref(''), actionError = ref(''), filterMerchantName = ref('')
const storeOptions = ref([{ storeId: '', storeName: '全部门店' }])
const merchantName = computed(() => filterMerchantName.value || app.selectedMerchant?.merchantName || app.storeContext?.merchantName)
const tabs = [{ key: 'all', label: '全部' }, { key: '1', label: '待付款' }, { key: '2', label: '待接单' }, { key: '3', label: '已接单' }, { key: '4', label: '配送中' }, { key: '5', label: '已完成' }, { key: '6', label: '已取消' }, { key: 'refund', label: '退款' }]
const { records, loading, error, total, load } = usePagination(params => {
  if (!app.selectedMerchantId || !app.token) throw new Error('请选择商家并登录后查看订单')
  const storeId = storeOptions.value[storeIndex.value]?.storeId
  return getMyOrders({ ...params, merchantId: app.selectedMerchantId, ...(storeId ? { storeId } : {}), ...(activeTab.value === 'refund' ? { refundOnly: true } : activeTab.value !== 'all' ? { status: Number(activeTab.value) } : {}) })
})
async function loadFilters() {
  if (!app.selectedMerchantId || !app.token) return
  const merchantId = app.selectedMerchantId, token = app.token
  filtersError.value = ''
  try { const result = await getOrderFilters({ merchantId }); if (merchantId !== app.selectedMerchantId || token !== app.token) return; if (result.code !== 1) throw new Error(result.msg || '门店筛选加载失败'); storeOptions.value = [{ storeId: '', storeName: '全部门店' }, ...(result.data.stores || [])]; filterMerchantName.value = result.data.merchantName || '' }
  catch (e) { if (merchantId === app.selectedMerchantId && token === app.token) filtersError.value = e.message || e.msg || '门店筛选加载失败' }
}
function filterStore(e) { storeIndex.value = Number(e.detail.value); load(true) }
function filterStatus(key) { activeTab.value = key; load(true) }
function resetFilters() { storeIndex.value = 0; activeTab.value = 'all'; load(true) }
async function refresh() { if (!app.selectedMerchantId || !app.token) return; storeIndex.value = 0; storeOptions.value = [{ storeId: '', storeName: '全部门店' }]; filterMerchantName.value = ''; load(true); await loadFilters() }
async function login() { actionError.value = ''; try { await app.ensureLogin(); await refresh() } catch (e) { actionError.value = e.message || e.msg || '登录未完成，请重试' } }
function detail(order) { uni.navigateTo({ url: '/pages/details/index?orderId=' + encodeURIComponent(order.orderId) + '&merchantId=' + encodeURIComponent(app.selectedMerchantId) + '&storeId=' + encodeURIComponent(order.storeId) }) }
function fulfillmentName(type) { return ({ DINE_IN: '堂食', PICKUP: '自取', DELIVERY: '外卖' })[type] || '外卖' }
function orderStatus(order) {
  if (order.refundStatus && order.refundStatus !== 'NONE') return ({ SUCCESS: '已退款', REFUNDED: '已退款', REQUESTED: '退款处理中', PROCESSING: '退款处理中', VERIFYING: '退款确认中', CLOSED: '退款已关闭', ABNORMAL: '退款异常', FAILED: '退款异常', PENDING: '退款处理中' })[order.refundStatus] || '退款处理中'
  if (order.payStatus === 2) return '已退款'
  if (order.payStatus === 3) return '退款处理中'
  if (order.payStatus === 4) return '退款异常'
  return ({ 1: '待付款', 2: '待接单', 3: '已接单', 4: '配送中', 5: '已完成', 6: '已取消' })[order.status] || '查看详情'
}
onShow(refresh); onReachBottom(() => { if (app.selectedMerchantId && app.token) load() })
</script>
<style lang="scss">@import '@/styles/app.scss';
.filter-picker { min-height: 96rpx; margin-bottom: 16rpx; font-size: 26rpx; }
.order-item { margin: 12rpx 0; }
.order-total { margin: 28rpx 0; }
.order-end { padding: 24rpx 0; text-align: center; }
</style>
