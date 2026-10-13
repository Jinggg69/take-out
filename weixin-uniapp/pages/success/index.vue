<template>
  <view class="success_content">
    <view class="success_info">
      <image
        class="success_icon"
        src="../../static/brand/state-success.png"
        mode="aspectFit"
      ></image>
      <view class="success_title"> 下单成功 </view>
      <view class="word-box">
        <text class="word_bottom"
          >预计<text class="word_date">{{ arrivalTime }}</text
          >送达</text
        >
      </view>
      <view class="success_desc"> 后厨正在为您准备美味，请耐心等待 </view>
      <!-- 新添加 -->
      <view class="btns">
        <view class="go_dish defaultBtn" @click="goIndex()"> 返回首页 </view>
        <view class="go_dish" @click="goOrder"> 查看订单 </view>
      </view>
    </view>
  </view>
</template>
<script>
import { mapState } from "vuex";
export default {
  data() {
    return {
      arrivalTime: "",
      orderId: null,
    };
  },
  computed: {
    tableInfo: function () {
      return this.shopInfo();
    },
  },
  onLoad(options) {
    // 获取一小时以后的时间
    this.getHarfAnOur();
    this.orderId = options.orderId;
  },
  methods: {
    ...mapState(["shopInfo", "arrivals"]),
    // 回首页
    goIndex() {
      uni.navigateTo({
        url: "/pages/index/index?status=" + "不验证",
      });
    },
    // 查看订单
    goOrder() {
      uni.navigateTo({
        url: "/pages/details/index?orderId=" + this.orderId,
      });
    },
    // 获取一小时以后的时间
    getHarfAnOur() {
      this.arrivalTime = this.arrivals();
    },
  },
};
</script>
<style src="./../common/Navbar/navbar.scss" lang="scss" scoped>
.success_content { padding: 96rpx 32rpx calc(48rpx + env(safe-area-inset-bottom)); }
.success_info { text-align: center; background: #fff; padding: 48rpx 24rpx; border: 1rpx solid #EADFD1; border-top: 4rpx solid #C9A45C; border-radius: 24rpx; }
.success_icon { width: 180rpx; height: 180rpx; }
.success_title { font-size: 36rpx; font-weight: 600; color: #9E2026; margin: 20rpx 0; }
.word-box { margin-top: 24rpx; font-size: 28rpx; color: #756B62; }
.word_date { color: #9E2026; font-weight: 600; padding: 0 8rpx; }
.success_desc { margin: 24rpx 0 32rpx; font-size: 26rpx; line-height: 1.7; color: #756B62; }
.btns { display: flex; gap: 20rpx; }
.go_dish { flex: 1; min-height: 88rpx; line-height: 88rpx; background: #9E2026; color: #FFF8EE; font-size: 28rpx; border-radius: 16rpx; border: 1rpx solid #9E2026; }
.defaultBtn { background: #FFF8EE; color: #9E2026; border-color: #EADFD1; }
</style>
<style lang="scss" scoped>
.success_content { padding: 96rpx 32rpx calc(48rpx + env(safe-area-inset-bottom)); }
.success_info { text-align: center; background: #fff; padding: 48rpx 24rpx; border: 1rpx solid #EADFD1; border-top: 4rpx solid #C9A45C; border-radius: 24rpx; }
.success_icon { width: 180rpx; height: 180rpx; }
.success_title { font-size: 36rpx; font-weight: 600; color: #9E2026; margin: 20rpx 0; }
.word-box { margin-top: 24rpx; font-size: 28rpx; color: #756B62; }
.word_date { color: #9E2026; font-weight: 600; padding: 0 8rpx; }
.success_desc { margin: 24rpx 0 32rpx; font-size: 26rpx; line-height: 1.7; color: #756B62; }
.btns { display: flex; gap: 20rpx; }
.go_dish { flex: 1; min-height: 88rpx; line-height: 88rpx; background: #9E2026; color: #FFF8EE; font-size: 28rpx; border-radius: 16rpx; border: 1rpx solid #9E2026; }
.defaultBtn { background: #FFF8EE; color: #9E2026; border-color: #EADFD1; }
</style>
 