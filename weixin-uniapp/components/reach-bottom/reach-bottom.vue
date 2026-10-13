<!--底部数据加载中-->
<template>
  <view class="bottom-box">
    <view class="bottom-item">
      <text class="title">{{loadingText}}</text>
    </view>
  </view>
</template>
<script>
export default {
  // 获取父级传的数据
  props: {
    // 数据加载提示
    loadingText:{
      type: String,
      default: ''
    }
  }
}
</script>

<style lang="scss">.bottom-box { width: 100%; padding-bottom: env(safe-area-inset-bottom); }
.bottom-item { width: 100%; min-height: 88rpx; line-height: 88rpx; text-align: center; }
.bottom-item .title { color: #756B62; font-size: 24rpx; }</style>
