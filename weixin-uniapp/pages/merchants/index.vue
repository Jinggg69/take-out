<template>
  <view class="app-page">
    <view class="app-heading">选择商家</view>
    <view class="app-subtitle">找到商家，再选择用餐门店</view>
    <view v-if="app.lastSelection" class="app-panel">
      <view class="app-small app-muted">上次选择</view>
      <view class="app-title">{{ app.lastSelection.storeName || app.lastSelection.merchantName || '上次的门店' }}</view>
      <view v-if="restoreError" class="app-error">{{ restoreError }}</view>
      <button class="app-button" :loading="restoring" :disabled="busy || restoring" @click="resume">继续上次选择</button>
      <button class="app-link" :disabled="busy" @click="history">查看上次商家的订单</button>
    </view>
    <view class="app-search">
      <input v-model="keyword" placeholder="搜索商家名称" aria-label="商家名称" maxlength="100" confirm-type="search" @confirm="search" />
      <button class="app-button" @click="search">搜索</button>
    </view>
    <AppState v-if="loading && !records.length" loading />
    <AppState v-else-if="error && !records.length" title="商家加载失败" :message="error" action="重新加载" @retry="load(true)" />
    <AppState v-else-if="!records.length" title="没有找到商家" message="试试其他名称，或清空搜索查看全部商家。" action="查看全部商家" @retry="clearSearch" />
    <view v-else class="app-list">
      <button v-for="merchant in records" :key="merchant.merchantId" class="app-list-row" :disabled="busy" @click="choose(merchant)">
        <image v-if="merchant.logoUrl" class="app-logo" :src="merchant.logoUrl" mode="aspectFill" />
        <view v-else class="app-mark">{{ (merchant.merchantName || '商').slice(0, 1) }}</view>
        <view class="app-grow"><view class="app-title">{{ merchant.merchantName }}</view><view v-if="merchant.introduction" class="app-muted">{{ merchant.introduction }}</view><view class="app-meta">{{ merchant.activeStoreCount }} 家门店</view></view>
        <text class="app-status">选择</text>
      </button>
    </view>
    <view v-if="actionError" class="app-error">{{ actionError }}</view>
    <view v-if="error && records.length" class="app-error">{{ error }}</view>
    <button v-if="records.length < total" class="app-link" :loading="loading" :disabled="loading" @click="load()">{{ error ? '重试加载更多' : '加载更多商家' }}</button>
    <button class="app-link" @click="goAccount">我的账户与地址</button>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/index.js'
import { getMerchants, getMerchantStores, getStoreContext } from '@/pages/api/api.js'
import { restoreLastSelection } from '@/services/context.js'
import { usePagination } from '@/components/app-discovery/pagination.js'
import AppState from '@/components/app-state/index.vue'
const app = useAppStore(), keyword = ref(''), busy = ref(false), actionError = ref(''), restoring = ref(false), restoreError = ref('')
let appliedKeyword = ''
const { records, loading, error, total, load } = usePagination(params => getMerchants({ ...params, keyword: appliedKeyword }))
function search() { appliedKeyword = keyword.value.trim(); load(true) }
function clearSearch() { keyword.value = ''; search() }
function goAccount() { uni.switchTab({ url: '/pages/my/my' }) }
function guard(targetStoreId) {
  const pending = app.pendingSubmission || app.paymentInFlight
  if (pending && (!targetStoreId || String(targetStoreId) !== String(pending.storeId || app.selectedStoreId))) throw new Error('有订单正在提交或支付，请先完成原门店订单后再切换。')
}
async function choose(merchant) {
  busy.value = true; actionError.value = ''
  const generation = app.storeGeneration
  try {
    guard()
    const response = await getMerchantStores(merchant.merchantId, { page: 1, pageSize: 2 })
    if (generation !== app.storeGeneration) throw new Error('选择已变化，请重新选择商家')
    if (response.code !== 1) throw new Error(response.msg || '门店加载失败')
    if (Number(response.data.total) === 1) {
      const result = await getStoreContext(response.data.records[0].storeId)
      if (generation !== app.storeGeneration) throw new Error('选择已变化，请重新选择商家')
      if (result.code !== 1) throw new Error(result.msg || '门店验证失败')
      if (String(result.data.merchantId) !== String(merchant.merchantId)) throw new Error('门店归属已变更，请重新选择商家')
      app.selectContext(result.data)
      uni.switchTab({ url: '/pages/index/index' })
    }
    else if (Number(response.data.total) > 1) { app.selectMerchant(merchant); uni.navigateTo({ url: '/pages/stores/index?merchantId=' + encodeURIComponent(merchant.merchantId) }) }
    else throw new Error('该商家暂无可用门店，请选择其他商家。')
  } catch (e) { actionError.value = e.message || e.msg || '选择失败，请重试' }
  finally { busy.value = false }
}
async function resume() {
  restoring.value = true; restoreError.value = ''
  try { guard(app.lastSelection?.storeId); const context = await restoreLastSelection(); if (!context) throw new Error('上次门店已不可用，请重新选择。'); app.selectContext(context); uni.switchTab({ url: '/pages/index/index' }) }
  catch (e) { restoreError.value = e.message || e.msg || '上次门店暂不可用，请重试或查看历史订单。' }
  finally { restoring.value = false }
}
function history() { try { guard(); app.setHistoryMerchant(app.lastSelection); uni.switchTab({ url: '/pages/orders/index' }) } catch (e) { actionError.value = e.message || e.msg } }
onLoad(() => load(true)); onReachBottom(() => load())
</script>
<style lang="scss">@import '@/styles/app.scss';</style>
