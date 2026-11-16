<!--菜品信息-->
<template>
  <view class="box order_list">
    <view class="word_text"><view class="brand-order-heading"><text>{{ $appStore.storeContext && $appStore.storeContext.merchantName || '商家' }}</text></view>
      <text class="word_style">{{ shopInfo.shopName }}</text>
    </view>
    <view class="order-type">
      <view class="type_item" v-for="(obj, index) in orderDataes" :key="index">
        <view class="dish_img">
          <image mode="aspectFill" :src="obj.image" class="dish_img_url"></image>
        </view>
        <view class="dish_info">
          <view class="dish_name"> {{ obj.name }} </view>
          <view class="dish_dishFlavor" v-if="obj.dishFlavor">
            {{ obj.dishFlavor }}
          </view>
          <view class="dish_price">×
            <text v-if="obj.number && obj.number > 0" class="dish_number">{{
              obj.number
            }}</text>
          </view>
          <view class="dish_active">
            <text>￥</text> {{ Number(obj.amount || 0).toFixed(2) }}
          </view>
        </view>
      </view>
      <view class="iconUp">
        <view @click="$emit('toggle')" v-if="orderListDataes.length > 3">
          <text>{{ !showDisplay ? "展开更多" : "点击收起" }}</text>
          <image class="icon_img" :class="showDisplay ? 'icon_imgDown' : ''" src="../../../static/toRight.png" mode="">
          </image>
        </view>
      </view>
      <view class="orderList">
        <view class="orderInfo">
          <text class="text">打包费</text><text class="may">￥</text>{{ packFee.toFixed(2) }}
        </view>
        <view class="orderInfo">
          <text class="text">配送费</text><text class="may">￥</text>{{ deliveryFee }}
        </view>
        <view class="totalMoney">
          合计<text class="text"><text>￥</text>{{ orderDishPrice.toFixed(2) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>
<script>

import { DELIVERY_FEE } from "../../../utils/order-pricing";
export default {
  // 获取父级传的数据
  props: {
    packFee: { type: Number, default: 0 },
    deliveryFee: {
      type: Number,
      default: DELIVERY_FEE,
    },
    // 菜品数据
    orderDataes: {
      type: Array,
      default: () => [],
    },
    // 是否显示更多收起
    showDisplay: {
      type: Boolean,
      default: false,
    },
    // 菜品数据
    orderListDataes: {
      type: Array,
      default: () => [],
    },
    // 打包费
    orderDishNumber: {
      type: Number,
      default: 0,
    },
    // 合计总钱数
    orderDishPrice: {
      type: Number,
      default: 0,
    },
  },
  computed: {
    shopInfo() { return this.$appStore.shopInfo },
  },
};
</script>
<style src="./../style.scss" lang="scss" scoped></style>
<style lang="scss" scoped>
.word_text { padding: 20rpx; }
</style>
