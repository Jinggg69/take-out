<template>
  <view class="store-header">
    <view class="store-header-info"><view class="store-header-merchant">{{ context.merchantName || '选择商家开始点餐' }}</view><view class="store-header-name">{{ context.storeName || '尚未选择门店' }}</view><text v-if="app.selectedStoreId" class="store-header-status">{{ context.open === true ? '营业中' : context.open === false ? '休息中' : '营业状态待确认' }}</text></view>
    <button class="store-header-change" @click="change">切换商家</button>
  </view>
</template>
<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/store/index.js'
import { openMerchantPicker } from '@/services/context.js'
const app = useAppStore(), emit = defineEmits(['change'])
const context = computed(() => app.storeContext || {})
function change() { emit('change'); openMerchantPicker().catch(error => uni.showToast({ title: error.message || error.msg || '暂时无法打开商家列表', icon: 'none' })) }
</script>
<style scoped lang="scss">
.store-header { display: flex; justify-content: space-between; align-items: center; gap: 24rpx; padding: 28rpx; background: white; color: #1F2933; border-radius: 24rpx; }
.store-header-info { flex: 1; min-width: 0; }
.store-header-merchant { font-size: 24rpx; color: #58636F; overflow-wrap: anywhere; }
.store-header-name { font-size: 36rpx; font-weight: 650; line-height: 1.4; overflow-wrap: anywhere; }
.store-header-status { font-size: 24rpx; color: #176B4B; }
.store-header-change { margin: 0; padding: 22rpx 0 22rpx 12rpx; background: transparent; color: #176B4B; font-size: 26rpx; line-height: 1.5; flex-shrink: 0; }
.store-header-change::after { border: 0; }
</style>
