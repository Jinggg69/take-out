<template>
  <view class="app-page">
    <template v-if="!app.selectedStoreId">
      <view class="app-heading">今天想吃什么？</view>
      <AppState title="先选择商家与门店" message="查看门店营业信息，选择堂食、自取或外卖。" action="选择商家" @retry="openMerchantPicker" />
      <button class="app-button app-button-secondary" :loading="busy" @click="scan">扫码堂食</button>
    </template>
    <template v-else>
      <AppStoreHeader />
      <view class="store-details app-panel">
        <view v-if="context.address">{{ context.address }}</view>
        <view v-if="context.opensAt && context.closesAt" class="app-meta">营业时间 {{ context.opensAt }}—{{ context.closesAt }}</view>
        <view class="store-fees"><text v-if="context.minimumOrderAmount != null">起送 ¥{{ context.minimumOrderAmount }}</text><text v-if="context.deliveryFee != null">配送 ¥{{ context.deliveryFee }}</text><text v-if="context.packFeePerItem != null">打包 ¥{{ context.packFeePerItem }}/份</text></view>
        <button v-if="context.contactPhone" class="app-link" @click="contact">联系门店</button>
      </view>
      <view v-if="app.diningContext" class="app-panel"><view class="app-title">当前桌台 · {{ app.diningContext.name || app.diningContext.tableName || '堂食' }}</view><button class="app-link" @click="enter('DINE_IN')">继续堂食点餐</button></view>
      <view class="app-section-title">选择用餐方式</view>
      <view class="app-list">
        <button class="app-list-row" :disabled="busy" @click="scan"><view class="app-grow"><view class="app-title">堂食点餐</view><view class="app-meta">扫描桌码，加入当前桌台</view></view><text class="app-status">扫码入桌</text></button>
        <button class="app-list-row" :disabled="busy" @click="enter('PICKUP')"><view class="app-grow"><view class="app-title">到店自取</view><view class="app-meta">提前下单，到店取餐</view></view><text class="app-status">去点单</text></button>
        <button class="app-list-row" :disabled="busy" @click="enter('DELIVERY')"><view class="app-grow"><view class="app-title">外卖配送</view><view class="app-meta">选择地址后确认配送范围与费用</view></view><text class="app-status">去点单</text></button>
      </view>
      <view v-if="context.open === false" class="app-meta">门店当前休息中，提交订单时将再次确认营业状态。</view>
      <view class="app-section-title">浏览菜单</view>
      <AppState v-if="loading" loading />
      <AppState v-else-if="error" title="菜单暂未加载" :message="error" action="重新加载" @retry="loadCategories" />
      <view v-else-if="categories.length" class="app-list"><button v-for="category in categories" :key="category.id" class="app-list-row" @click="browse"><view class="app-grow">{{ category.name }}</view><text class="app-status">查看菜品</text></button></view>
      <AppState v-else title="门店暂未提供菜单" message="稍后再来看看，或联系门店了解详情。" action="重新加载" @retry="loadCategories" />
    </template>
    <view v-if="actionError" class="app-error">{{ actionError }}</view>
  </view>
</template>
<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/index.js'
import { getCategoryList } from '@/pages/api/api.js'
import { openMerchantPicker, scanDining, goToMenu } from '@/services/context.js'
import AppState from '@/components/app-state/index.vue'
import AppStoreHeader from '@/components/app-store-header.vue'
const app = useAppStore(), context = computed(() => app.storeContext || {})
const categories = ref([]), loading = ref(false), error = ref(''), busy = ref(false), actionError = ref('')
let requestVersion = 0
async function loadCategories() {
  const version = ++requestVersion, generation = app.storeGeneration
  categories.value = []; error.value = ''; if (!app.selectedStoreId) return
  loading.value = true
  try { const result = await getCategoryList(); if (generation !== app.storeGeneration || version !== requestVersion) return; if (result.code !== 1) throw new Error(result.msg || '菜单加载失败'); categories.value = result.data || [] }
  catch (e) { if (version === requestVersion && generation === app.storeGeneration) error.value = e.message || e.msg || '网络异常，请重试' }
  finally { if (version === requestVersion) loading.value = false }
}
async function scan() { busy.value = true; actionError.value = ''; try { await scanDining() } catch (e) { actionError.value = e.message || e.msg || '扫码未完成，请重新扫描门店桌码' } finally { busy.value = false } }
async function enter(type) { actionError.value = ''; try { await goToMenu(type) } catch (e) { actionError.value = e.message || e.msg || '暂时无法点餐，请重试' } }
function browse() { enter(app.fulfillmentType || 'DELIVERY') }
function contact() { uni.makePhoneCall({ phoneNumber: context.value.contactPhone }) }
onShow(loadCategories)
</script>
<style lang="scss">@import '@/styles/app.scss';
.store-details { margin-top: 20rpx; }
.store-fees { display: flex; flex-wrap: wrap; gap: 12rpx 28rpx; margin-top: 20rpx; color: #58636F; font-size: 24rpx; }
</style>
