<template>
  <view class="app-page">
    <view class="app-heading">我的</view>
    <view class="app-panel app-row account-panel">
      <image v-if="profile.avatarUrl" class="app-logo" :src="profile.avatarUrl" mode="aspectFill" />
      <view class="app-grow"><view class="app-title">{{ app.token ? profile.nickname || profile.nickName || '微信用户' : '登录后管理账户' }}</view><view class="app-meta">{{ app.token ? '我的订单与常用地址' : '地址簿可在各商家共用' }}</view></view>
      <button v-if="!app.token" class="app-button" :loading="loading" @click="login">登录</button>
    </view>
    <view v-if="loading && app.token" class="app-muted" role="status">正在加载账户信息…</view>
    <view v-if="error" class="app-panel"><view class="app-error">{{ error }}</view><button class="app-link" @click="app.token ? loadProfile() : login()">重试</button></view>
    <view class="app-section-title">常用服务</view>
    <view class="app-list">
      <button class="app-list-row" @click="address"><view class="app-grow"><view>收货地址</view><view class="app-meta">管理收货人和配送地址</view></view><text class="app-status">管理</text></button>
      <button class="app-list-row" @click="orders"><view class="app-grow"><view>我的订单</view><view class="app-meta">查看当前商家的订单</view></view><text class="app-status">查看</text></button>
      <button class="app-list-row" @click="openMerchantPicker"><view class="app-grow"><view>选择商家与门店</view><view class="app-meta">{{ context.storeName || '尚未选择门店' }}</view></view><text class="app-status">选择</text></button>
    </view>
    <view class="app-section-title">当前门店</view>
    <view v-if="app.selectedStoreId" class="app-panel"><view class="app-title">{{ context.storeName }}</view><view v-if="context.address" class="app-meta">{{ context.address }}</view><button v-if="context.contactPhone" class="app-link" @click="contact">联系门店 · {{ context.contactPhone }}</button><view v-else class="app-meta">门店暂未提供联系电话</view></view>
    <AppState v-else title="还没有选择门店" message="选择门店后，可查看营业信息和联系方式。" action="选择商家" @retry="openMerchantPicker" />
  </view>
</template>
<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/index.js'
import { getUserProfile } from '@/pages/api/api.js'
import { openMerchantPicker } from '@/services/context.js'
import AppState from '@/components/app-state/index.vue'
const app = useAppStore(), profile = ref({}), loading = ref(false), error = ref(''), context = computed(() => app.storeContext || {})
async function loadProfile() { if (!app.token) { profile.value = {}; return } loading.value = true; error.value = ''; const token = app.token; try { const r = await getUserProfile(); if (token !== app.token) return; if (r.code !== 1) throw new Error(r.msg || '账户信息加载失败'); profile.value = r.data || {} } catch (e) { if (token === app.token) error.value = e.message || e.msg || '账户信息加载失败，请重试' } finally { loading.value = false } }
async function login() { loading.value = true; error.value = ''; try { await app.ensureLogin(); await loadProfile() } catch (e) { error.value = e.message || e.msg || '登录未完成，请重试' } finally { loading.value = false } }
async function address() { error.value = ''; try { await app.ensureLogin(); app.setAddressBackUrl('/pages/my/my'); uni.navigateTo({ url: '/pages/address/address?form=my' }) } catch (e) { error.value = e.message || e.msg || '登录未完成，请重试' } }
function orders() { uni.switchTab({ url: '/pages/orders/index' }) }
function contact() { uni.makePhoneCall({ phoneNumber: context.value.contactPhone }) }
onShow(loadProfile)
</script>
<style lang="scss">@import '@/styles/app.scss';
.account-panel { margin-top: 28rpx; min-height: 180rpx; }
</style>
