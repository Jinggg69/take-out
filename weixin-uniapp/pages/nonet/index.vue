<template>
  <view class="nonet_content">
    <view class="success_info">
      <image class="success_icon" src="../../static/brand/state-network.png" mode="aspectFit"></image>
      <view class="success_title"> 网络无法连接 </view>
      <view class="go_dish" @click="goIndex()"> 点击刷新 </view>
    </view>
  </view>
</template>
<script>
import { mapState } from "vuex";
export default {
  computed: {
    tableInfo: function () {
      return this.shopInfo();
    },
  },
  methods: {
    ...mapState(["shopInfo"]),
    goIndex() {
      uni.navigateTo({ url: "/pages/index/index" });
    },
  },
};
</script>
<style src="./../common/Navbar/navbar.scss" lang="scss" scoped>
.nonet_content { padding: 96rpx 32rpx calc(48rpx + env(safe-area-inset-bottom)); text-align: center; }
.success_info { background: #fff; border: 1rpx solid #EADFD1; border-radius: 24rpx; padding: 48rpx 24rpx; }
.success_icon { width: 320rpx; height: 240rpx; }
.success_title { color: #302824; font-size: 36rpx; margin: 24rpx 0; font-weight: 600; }
.go_dish { margin: 32rpx auto 0; min-height: 88rpx; line-height: 88rpx; width: 300rpx; color: #FFF8EE; background: #9E2026; font-size: 28rpx; border-radius: 16rpx; }
</style>
<style lang="scss" scoped>
.nonet_content { padding: 96rpx 32rpx calc(48rpx + env(safe-area-inset-bottom)); text-align: center; }
.success_info { background: #fff; border: 1rpx solid #EADFD1; border-radius: 24rpx; padding: 48rpx 24rpx; }
.success_icon { width: 320rpx; height: 240rpx; }
.success_title { color: #302824; font-size: 36rpx; margin: 24rpx 0; font-weight: 600; }
.go_dish { margin: 32rpx auto 0; min-height: 88rpx; line-height: 88rpx; width: 300rpx; color: #FFF8EE; background: #9E2026; font-size: 28rpx; border-radius: 16rpx; }
</style>
 