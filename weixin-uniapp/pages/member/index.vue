<template>
  <view class="app-page">
    <view class="app-heading">会员中心</view>
    <view class="app-subtitle">{{ app.selectedMerchant?.merchantName || '当前商户' }} · {{ app.storeContext?.storeName || '尚未选择门店' }}</view>
    <view v-if="!app.selectedStoreId" class="app-panel"><view>请选择商家和门店，查看该商户的会员权益。</view><button class="app-link" @click="openMerchantPicker">选择商家</button></view>
    <view v-else-if="!app.token" class="app-panel"><view>登录后查看或开通当前商户会员。</view><button class="app-button" @click="login">登录</button></view>
    <view v-else-if="loading && !profile" class="app-muted" role="status">正在加载会员信息…</view>
    <template v-if="profile">
      <view v-if="profile.simulation" class="member-notice">模拟环境：充值与余额不涉及真实资金。</view>
      <view v-if="profile.joined" class="app-panel member-card">
        <view class="app-row"><view class="app-title">{{ profile.member.name || '商户会员' }}</view><text>{{ profile.member.levelName || '普通会员' }}</text></view>
        <view class="app-meta">会员号 {{ profile.member.memberNo }}</view>
        <view v-if="Number(profile.member.status) === 0" class="app-error">会员已停用，可查看资产和历史记录。</view>
        <view class="app-divider" />
        <view class="app-row"><view><view class="app-muted">可用余额</view><view class="app-price">¥{{ yuan(profile.member.balance) }}</view></view><view><view class="app-muted">可用积分</view><view class="app-price">{{ profile.member.points ?? '—' }}</view></view></view>
        <view class="app-meta">可用优惠券 {{ couponCount ?? '—' }} 张</view>
        <view class="app-meta">本金 ¥{{ yuan(profile.member.principalBalance) }} · 赠送 ¥{{ yuan(profile.member.giftBalance) }}</view>
      </view>
      <view v-else class="app-panel"><view class="app-title">开通当前商户会员</view><view class="app-meta">会员资料、积分和余额在本商户各门店通用。</view><button class="app-button" :disabled="busy" @click="join">确认开通会员</button></view>
      <template v-if="profile.joined">
        <view class="app-section-title">会员服务</view>
        <view class="app-list"><button v-for="link in links" :key="link.url" class="app-list-row" @click="navigate(link.url)"><view class="app-grow"><view>{{ link.title }}</view><view class="app-meta">{{ link.detail }}</view></view><text class="app-status">查看</text></button></view>
        <view class="app-section-title">会员资料</view>
        <view class="app-panel member-form"><label>姓名<input v-model="form.name" maxlength="32" placeholder="会员姓名" /></label><label>手机号<input v-model="form.phone" type="number" maxlength="11" placeholder="手机号" /></label><label>生日<picker mode="date" :value="form.birthday" @change="form.birthday = $event.detail.value"><view class="member-input">{{ form.birthday || '选择生日（选填）' }}</view></picker></label><button class="app-button" :disabled="busy" @click="save">保存资料</button></view>
      </template>
    </template>
    <view v-if="error" class="app-panel"><view class="app-error" role="alert">{{ error }}</view><button class="app-link" :disabled="busy" @click="load">重新加载</button></view>
  </view>
</template>
<script setup>
import { reactive, watch } from 'vue'
import { useMemberContext, yuan } from '@/services/member-state.js'
import { joinMember, updateMember } from '@/services/member-api.js'
import { openMerchantPicker } from '@/services/context.js'
const form = reactive({ name: '', phone: '', birthday: '' })
const { app, profile, couponCount, loading, busy, error, load, action, login } = useMemberContext(() => Object.assign(form, { name: '', phone: '', birthday: '' }), true)
watch(profile, p => { if (p?.member) Object.assign(form, { name: p.member.name || '', phone: p.member.phone || '', birthday: p.member.birthday || '' }) })
const links = [ { title: '积分明细', detail: '查看积分收支 · 积分永久有效', url: 'records?kind=points' }, { title: '余额明细', detail: '本金与赠送余额收支', url: 'records?kind=wallet' }, { title: '优惠券包', detail: '可用、已使用和过期优惠券', url: 'coupons' }, { title: '领券与积分兑换', detail: '查看当前商户可领取的优惠', url: 'coupons?center=1' }, { title: '储值充值与记录', detail: '套餐、模拟结果与未使用批次退款', url: 'recharge' }, { title: '消费记录', detail: '查看当前商户会员订单', url: 'records?kind=orders' } ]
const navigate = url => uni.navigateTo({ url: '/pages/member/' + url })
const join = () => action('join', () => joinMember({}))
function save() { if (!form.name.trim() || !/^1\d{10}$/.test(form.phone)) { error.value = '请填写姓名和11位手机号'; return } const data = { ...form, name: form.name.trim(), birthday: form.birthday || null, version: profile.value.member.version }; action('profile', () => updateMember(data)) }
</script>
<style lang="scss">@import '@/styles/app.scss'; @import './member.scss';</style>
