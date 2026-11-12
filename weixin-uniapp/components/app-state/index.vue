<template>
  <view class="app-state" role="status" aria-live="polite">
    <view class="app-state-title">{{ loading ? '正在加载…' : title }}</view>
    <view v-if="message" class="app-state-copy">{{ message }}</view>
    <button v-if="action && !loading" class="app-button app-button-secondary" @click="$emit('retry')">{{ action }}</button>
  </view>
</template>
<script setup>
defineProps({ loading: Boolean, title: String, message: String, action: String })
defineEmits(['retry'])
</script>
<style lang="scss" scoped>
.app-state { padding: 64rpx 28rpx; text-align: center; }
.app-state-title { font-size: 30rpx; font-weight: 600; color: #1F2933; }
.app-state-copy { margin: 16rpx 0 28rpx; color: #58636F; font-size: 26rpx; line-height: 1.7; }
</style>
