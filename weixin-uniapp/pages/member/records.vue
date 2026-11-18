<template>
  <view class="app-page">
    <view class="app-heading">{{ title }}</view>
    <view class="app-subtitle">{{ app.selectedMerchant?.merchantName || '当前商户' }}</view>
    <view v-if="!app.selectedStoreId" class="app-panel"><button class="app-link" @click="openMerchantPicker">先选择商家与门店</button></view>
    <button v-else-if="!app.token" class="app-button" @click="login">登录查看</button>
    <view v-else-if="loading && !profile" class="app-muted" role="status">正在加载会员信息…</view>
  <template v-else-if="profile?.joined">
      <view v-if="kind === 'points'" class="member-notice">积分永久有效。可用积分 {{ profile.member.points ?? '—' }}</view>
      <view v-if="kind === 'wallet'" class="member-notice">{{ profile.simulation ? '模拟余额 · 不涉及真实资金。' : '' }}当前余额 ¥{{ yuan(profile.member.balance) }}</view>
      <view class="app-panel">
        <view v-for="item in records" :key="item.id" class="member-record">
          <template v-if="kind === 'orders'"><view class="app-row"><text>{{ item.number || item.orderNumber || '订单' }}</text><text class="app-price">¥{{ yuan(item.amount) }}</text></view><view class="app-meta">{{ item.storeName || '' }} · {{ item.orderTime || item.createTime }}</view></template>
          <template v-else><view class="app-row"><text>{{ item.reason || item.assetType || '账户变更' }}</text><text class="app-price">{{ ledgerChange(item, kind) }}</text></view><view class="app-meta">{{ ledgerAfter(item, kind) }} · {{ item.createTime }}</view><view class="app-muted app-small">业务号 {{ item.businessNo || '—' }}</view></template>
        </view>
        <view v-if="!records.length && !recordsLoading" class="member-empty">暂无{{ title }}</view>
        <view v-if="recordsLoading" role="status" class="app-muted">正在加载…</view>
        <button v-if="records.length < total" class="app-link" :disabled="recordsLoading" @click="fetchPage(false)">加载更多</button>
      </view>
    </template>
    <view v-else-if="profile" class="app-panel">开通会员后可查看记录。<button class="app-link" @click="center">前往会员中心</button></view>
    <view v-if="error" class="app-error" role="alert">{{ error }}</view>
    <button v-if="app.token && app.selectedStoreId" class="app-link" :disabled="recordsLoading || loading" @click="refresh">刷新记录</button>
  </view>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useMemberContext, yuan, ledgerChange, ledgerAfter } from '@/services/member-state.js'
import { memberRecords } from '@/services/member-api.js'
import { openMerchantPicker } from '@/services/context.js'
const kind = ref('points'), records = ref([]), total = ref(0), page = ref(1), recordsLoading = ref(false)
const { app, profile, loading, error, current, scope, load, login } = useMemberContext(() => { records.value = []; total.value = 0; page.value = 1; recordsLoading.value = false })
const title = computed(() => ({ points: '积分明细', wallet: '余额明细', orders: '消费记录' }[kind.value]))
onLoad(options => { kind.value = ['points', 'wallet', 'orders'].includes(options.kind) ? options.kind : 'points' })
async function fetchPage(reset = true) {
  if (!profile.value?.joined || recordsLoading.value) return
  const stamp = scope.value; recordsLoading.value = true; error.value = ''
  const next = reset ? 1 : page.value + 1
  try { const r = await memberRecords(kind.value, { page: next, pageSize: 20 }); if (!current(stamp)) return; records.value = reset ? r.data.records : records.value.concat(r.data.records); total.value = Number(r.data.total); page.value = next }
  catch (e) { if (current(stamp)) error.value = e.msg || '记录加载失败，请重试' }
  finally { if (current(stamp)) recordsLoading.value = false }
}
watch(profile, () => fetchPage())
onShow(() => { if (profile.value) fetchPage() })
async function refresh() { if (await load()) await fetchPage() }
function center() { uni.navigateTo({ url: '/pages/member/index' }) }
</script>
<style lang="scss">@import '@/styles/app.scss'; @import './member.scss';</style>
