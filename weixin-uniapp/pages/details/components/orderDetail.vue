<!-- 菜品列表 -->
<template>
  <view class="box order_list_cont" v-if="Object.keys(orderDetailsData).length">
    <view class="order_list">
      <view class="word_text"><view class="brand-order-heading"><text>{{ orderDetailsData.merchantName || '商家' }}</text></view><text class="word_style">{{ storeName }}</text></view>
      <view class="order-type">
        <view class="type_item" v-for="(obj, index) in orderDataes" :key="index">
          <view class="dish_img">
            <image mode="aspectFill" :src="obj.image" class="dish_img_url"></image>
          </view>
          <view class="dish_info">
            <view class="dish_name">{{ obj.name }}</view>
            <view class="dish_dishFlavor">{{
              obj.dishFlavor ? obj.dishFlavor : ""
            }}</view>
            <view class="dish_price">
              ×
              <text v-if="obj.number && obj.number > 0" class="dish_number">{{
                obj.number
              }}</text>
            </view>
            <view class="dish_active">
              <text>￥</text>
              {{ moneyText(obj.amount) }}
            </view>
          </view>
        </view>
        <view class="iconUp">
          <view @click="$emit('toggle')" v-if="orderDetailsData.orderDetailList.length > 2">
            <text>{{ !showDisplay ? "展开更多" : "点击收起" }}</text>
            <image class="icon_img" :class="showDisplay ? 'icon_imgDown' : ''" src="../../../static/toRight.png" mode="">
            </image>
          </view>
        </view>
        <view class="orderList">
          <view class="orderInfo">
            <text class="text">打包费</text>
            <text class="may">￥</text>
            {{ moneyText(packFee) }}
          </view>
          <view class="orderInfo">
            <text class="text">配送费</text>
            <text class="may">￥</text>
            {{ moneyText(deliveryFee) }}
          </view>
          <view class="totalMoney">
            合计
            <text class="text">
              <text>￥</text>
              {{ moneyText(totalAmount) }}
            </text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>
<script>
import { orderSnapshotStoreName } from "../../../utils/order-snapshot";
export default {
  computed: {
    storeName() { return orderSnapshotStoreName(this.orderDetailsData); },
    packFee() { return this.snapshotMoney("packFee", this.orderDetailsData.packFee ?? this.orderDetailsData.packAmount); },
    totalAmount() { return this.snapshotMoney("amount", this.orderDetailsData.amount); },
    deliveryFee() {
      return this.snapshotMoney("deliveryFee", this.orderDetailsData.deliveryFee);
    },
  },
  methods: {
    moneyText(value) {
      return value == null || value === '' || !Number.isFinite(Number(value)) || Number(value) < 0 ? '—' : Number(value).toFixed(2);
    },
    snapshotMoney(key, fallback) {
      let snapshot = {};
      try { snapshot = JSON.parse(this.orderDetailsData.pricingSnapshot || '{}') || {}; } catch (error) {}
      const value = snapshot[key] ?? fallback;
      return this.moneyText(value) === '—' ? null : Number(value);
    },
  },
  // 获取父级传的数据
  props: {
    // 菜品数据
    orderDataes: {
      type: Array,
      default: () => [],
    },
    // 订单信息
    orderDetailsData: {
      type: Object,
      default: () => ({}),
    },
    // 是否显示展开收起
    showDisplay: {
      type: Boolean,
      default: false,
    },
  },
};
</script>
<style src="../../order/style.scss" lang="scss"></style>
<style lang="scss" scoped>
.word_text { padding: 20rpx; }
</style>
