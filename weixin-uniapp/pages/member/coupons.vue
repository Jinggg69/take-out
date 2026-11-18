<template>
  <view class="app-page">
    <view class="app-heading">{{ center ? '领券与积分兑换' : '优惠券包' }}</view>
    <view class="app-subtitle">{{ app.selectedMerchant?.merchantName || '当前商户' }} · 每单最多使用一张优惠券</view>
    <button v-if="!app.selectedStoreId" class="app-link" @click="openMerchantPicker">先选择商家与门店</button>
    <button v-else-if="!app.token" class="app-button" @click="login">登录查看</button>
    <view v-else-if="loading && !profile" class="app-muted" role="status">正在加载会员信息…</view>
  <template v-else-if="profile?.joined">
      <view class="app-tabs"><button class="app-tab" :class="{ active: !center }" @click="switchCenter(false)">我的券包</button><button class="app-tab" :class="{ active: center }" @click="switchCenter(true)">领券中心</button></view>
      <view v-if="!center" class="app-tabs"><button v-for="s in statuses" :key="s.value" class="app-tab" :class="{ active: status === s.value }" @click="changeStatus(s.value)">{{ s.label }}</button></view>
      <view v-for="c in records" :key="c.id" class="app-panel">
        <view class="app-row"><view class="app-title">{{ c.name }}</view><view class="app-price">{{ couponValue(c) }}</view></view>
        <view class="app-meta">满 ¥{{ yuan(c.minSpend) }} 可用{{ c.maxDiscount ? ' · 最高减 ¥' + yuan(c.maxDiscount) : '' }}</view>
        <view class="app-meta">{{ c.startTime }} 至 {{ c.endTime }}</view>
        <view class="app-meta">{{ c.storeIds?.length ? '限指定门店使用' : '本商户门店通用' }}{{ c.fulfillmentTypes?.length ? ' · ' + c.fulfillmentTypes.map(typeLabel).join(' / ') : '' }}</view>
        <view v-if="center && c.claimable === false" class="app-meta">已达限领数量或当前不可领取</view>
        <view v-if="center && c.remaining != null" class="app-meta">剩余 {{ c.remaining }} 张 · 已领取 {{ c.claimedCount ?? 0 }} 张</view>
        <view v-if="!center" class="app-status">{{ statusText(c.status) }}</view>
        <view v-else class="member-actions"><button v-if="!Number(c.pointsCost)" class="app-button" :disabled="busy || disabled || c.claimable === false" @click="claim(c)">领取</button><button v-else class="app-button" :disabled="busy || disabled || c.claimable === false || Number(c.pointsCost) > Number(profile.member.points)" @click="claim(c)">{{ c.pointsCost }} 积分兑换</button></view>
      </view>
      <view v-if="!records.length && !recordsLoading" class="app-panel member-empty">{{ center ? '暂无可领取的优惠券' : '暂无此类优惠券' }}</view>
      <view v-if="recordsLoading" role="status" class="app-muted">正在加载优惠券…</view>
      <button v-if="records.length < total" class="app-link" :disabled="recordsLoading" @click="fetchPage(false)">加载更多</button>
    </template>
    <view v-else-if="profile" class="app-panel"><view>开通会员后可领取优惠券。</view><button class="app-link" @click="goCenter">前往会员中心</button></view>
    <view v-if="error" role="alert" class="app-error">{{ error }}</view>
    <button v-if="app.token && app.selectedStoreId" class="app-link" :disabled="busy || recordsLoading" @click="refresh">刷新</button>
  </view>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useMemberContext, yuan, couponValue, statusText } from '@/services/member-state.js'
import { memberRecords, couponCenter, claimCoupon, exchangeCoupon } from '@/services/member-api.js'
import { openMerchantPicker } from '@/services/context.js'
const center = ref(false), status = ref('AVAILABLE'), records = ref([]), total = ref(0), page = ref(1), recordsLoading = ref(false)
let version = 0
const { app, profile, loading, busy, error, scope, current, load, action, login } = useMemberContext(() => { version++; records.value = []; total.value = 0; recordsLoading.value = false })
const disabled = computed(() => Number(profile.value?.member?.status) === 0)
const statuses = [{ value: 'AVAILABLE', label: '可用' }, { value: 'USED', label: '已使用' }, { value: 'EXPIRED', label: '已过期' }]
onLoad(o => { center.value = o.center === '1' })
const typeLabel = type => ({ DELIVERY: '外卖', PICKUP: '自取', DINE_IN: '堂食' }[type] || type)
async function fetchPage(reset = true) {
 if (!profile.value?.joined) return
 const stamp = scope.value, run = ++version, next = reset ? 1 : page.value + 1
 recordsLoading.value = true; error.value = ''
 try { const r = await (center.value ? couponCenter({ page: next, pageSize: 20 }) : memberRecords('coupons', { page: next, pageSize: 20, status: status.value })); if (!current(stamp) || version !== run) return; records.value = reset ? r.data.records : records.value.concat(r.data.records); total.value = Number(r.data.total); page.value = next }
 catch(e) { if(current(stamp) && version === run) error.value = e.msg || '优惠券加载失败' }
 finally { if(current(stamp) && version === run) recordsLoading.value = false }
}
watch(profile, () => fetchPage())
function switchCenter(value) { if (busy.value) return; center.value = value; records.value = []; fetchPage() }
function changeStatus(value) { status.value = value; records.value = []; fetchPage() }
async function refresh() { if(await load()) await fetchPage() }
function claim(c) { action((Number(c.pointsCost) ? 'exchange:' : 'claim:') + c.id, key => Number(c.pointsCost) ? exchangeCoupon(c.id, key) : claimCoupon(c.id, key), async () => { uni.showToast({ title: '已放入优惠券包', icon: 'success' }); await load(); await fetchPage() }) }
function goCenter() { uni.navigateTo({ url: '/pages/member/index' }) }
</script>
<style lang="scss">@import '@/styles/app.scss'; @import './member.scss';</style>
