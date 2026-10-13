<!--空数据展示-->
<template>
  <view class="empty-box">
    <image class="empty-img" src="./../../static/brand/state-address.png" mode="aspectFit" />
    <view class="empty-des">{{textLabel}}</view>
  </view>
</template>
<script>
export default {
  // 获取父级传的数据
  props:{
    // 空页面提示
    'textLabel':{
      type: String,
      default: '暂无数据'
    }
  }
}
</script>
<style lang="scss" scoped>.empty-box { width: 100%; text-align: center; padding: 80rpx 24rpx 40rpx; box-sizing: border-box; }
.empty-img { width: 320rpx; height: 240rpx; }
.empty-des { line-height: 1.7; margin-top: 24rpx; font-size: 28rpx; color: #756B62; }</style>
