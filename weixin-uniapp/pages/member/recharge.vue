<template>
 <view class="app-page">
  <view class="app-heading">储值充值</view><view class="app-subtitle">{{ app.selectedMerchant?.merchantName || '当前商户' }} · {{ app.storeContext?.storeName || '请选择门店' }}</view>
  <button v-if="!app.selectedStoreId" class="app-link" @click="openMerchantPicker">选择商家与门店</button><button v-else-if="!app.token" class="app-button" @click="login">登录查看</button>
  <view v-else-if="loading && !profile" class="app-muted" role="status">正在加载会员信息…</view>
  <template v-else-if="profile?.joined">
   <view class="member-notice">{{ profile.simulation ? '模拟充值，不涉及真实资金。请主动选择模拟结果，只有服务端确认成功后入账。' : '当前环境不提供模拟充值，请联系商户。' }}</view>
   <view class="app-panel"><view class="app-muted">当前可用余额</view><view class="app-price">¥{{ yuan(profile.member.balance) }}</view></view>
   <view class="app-section-title">充值套餐</view>
   <view v-for="plan in plans" :key="plan.id" class="app-panel"><view class="app-row"><view><view class="app-title">{{ plan.name }}</view><view class="app-meta">充值 ¥{{ yuan(plan.amount) }} · 赠送 ¥{{ yuan(plan.giftAmount) }}</view></view><button class="app-button" :disabled="busy || disabled || !profile.simulation" @click="create(plan)">模拟充值</button></view></view>
   <view v-if="!plans.length && !recordsLoading" class="app-panel member-empty">商户暂无上架的充值套餐</view>
   <view class="app-section-title">充值记录</view><view class="app-muted">仅支持完全未使用、未冻结批次整笔退还本金，赠送额同步撤回。</view>
   <view v-for="r in records" :key="r.id" class="app-panel">
    <view class="app-row"><view class="app-title">充值 ¥{{ yuan(r.amount) }}</view><text class="app-status">{{ statusText(r.status) }}</text></view><view class="app-meta">赠送 ¥{{ yuan(r.giftAmount) }} · {{ r.paymentChannel === 'MOCK' ? '模拟充值' : r.paymentChannel }}</view><view class="app-meta">{{ r.number }} · {{ r.createTime }}</view>
    <view v-if="r.status === 'PENDING' && r.paymentChannel === 'MOCK'" class="member-actions"><button class="app-button" :disabled="busy || disabled || !profile.simulation" @click="simulate(r, 'SUCCESS')">模拟成功</button><button class="app-button app-button-secondary" :disabled="busy || disabled || !profile.simulation" @click="simulate(r, 'FAIL')">模拟失败</button><button class="app-button app-button-secondary" :disabled="busy || disabled || !profile.simulation" @click="simulate(r, 'CANCEL')">取消</button></view>
    <button v-if="r.status === 'SUCCESS'" class="app-link" :disabled="busy || r.refundEligible === false" @click="refund(r)">{{ r.refundEligible === false ? '批次已使用或冻结，暂不可退款' : '申请整笔退款' }}</button>
   </view>
   <view v-if="!records.length && !recordsLoading" class="app-panel member-empty">暂无充值记录</view><view v-if="recordsLoading" class="app-muted" role="status">正在加载…</view><button v-if="records.length < total" class="app-link" :disabled="recordsLoading || busy" @click="fetchData(false)">加载更多记录</button>
  </template>
  <view v-else-if="profile" class="app-panel"><view>请先开通当前商户会员。</view><button class="app-link" @click="goCenter">前往会员中心</button></view>
  <view v-if="error" class="app-error" role="alert">{{ error }}</view><button v-if="app.token && app.selectedStoreId" class="app-link" :disabled="busy || recordsLoading" @click="refresh">刷新余额与记录</button>
 </view>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { useMemberContext, yuan, statusText } from '@/services/member-state.js'
import { rechargePlans, memberRecords, createRecharge, mockRecharge, refundRecharge } from '@/services/member-api.js'
import { openMerchantPicker } from '@/services/context.js'
const records = ref([]), plans = ref([]), total = ref(0), page = ref(1), recordsLoading = ref(false)
let requestVersion = 0
const { app, profile, loading, busy, error, scope, current, load, action, login } = useMemberContext(() => { requestVersion++; records.value = []; plans.value = []; total.value = 0; recordsLoading.value = false })
const disabled = computed(() => Number(profile.value?.member?.status) === 0)
async function fetchData(reset = true) {
 if (!profile.value?.joined || (!reset && recordsLoading.value)) return
 const stamp = scope.value, run = ++requestVersion, next = reset ? 1 : page.value + 1; recordsLoading.value = true; error.value = ''
 try { const [p, r] = await Promise.all([rechargePlans(), memberRecords('recharges', { page: next, pageSize: 20 })]); if (!current(stamp) || run !== requestVersion) return; plans.value = p.data; records.value = reset ? r.data.records : records.value.concat(r.data.records); total.value = Number(r.data.total); page.value = next }
 catch(e) { if(current(stamp) && run === requestVersion) error.value = e.msg || '充值记录加载失败' }
 finally { if(current(stamp) && run === requestVersion) recordsLoading.value = false }
}
watch(profile, () => fetchData())
async function refresh() { if(await load()) await fetchData() }
function create(plan) { return action('recharge:' + plan.id, key => createRecharge(String(plan.id), key), async () => { await fetchData(); uni.showToast({ title: '请选择记录中的模拟结果', icon: 'none' }) }) }
function simulate(r, outcome) { action('mock:' + r.id + ':' + outcome, () => mockRecharge(r.id, outcome), refresh) }
function refund(r) { const stamp = scope.value; uni.showModal({ title: '确认整笔退款', content: '退回该批次本金 ¥' + yuan(r.amount) + '，并撤回赠送额 ¥' + yuan(r.giftAmount) + '。由服务端核验批次是否完全未使用。', success: result => { if(result.confirm && current(stamp)) action('refund:' + r.id, key => refundRecharge(r.id, key, '用户申请未使用充值批次退款'), refresh) } }) }
function goCenter() { uni.navigateTo({url:'/pages/member/index'}) }
</script>
<style lang="scss">@import '@/styles/app.scss'; @import './member.scss';</style>
