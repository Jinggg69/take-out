<!--购买页-->
<template>
  <view class="customer-box">
    <view class="wrap">
      <view class="contion">
        <view class="orderPay"><view class="brand-pay-name">{{ $appStore.storeContext && $appStore.storeContext.merchantName || '商家' }}</view>
          <view>
            <view v-if="timeout">订单已超时</view>
            <view v-else
              >支付剩余时间<text>{{ rocallTime }}</text></view
            >
          </view>
          <view class="money"
            >￥<text>{{ orderDataInfo.orderAmount }}</text></view
          >
          <view>{{ shopInfo().shopName }}-{{ orderDataInfo.orderNumber }}</view>
        </view>
      </view>
      <view v-if="mockCashier" class="mock-cashier">
        <view class="mock-cashier__title">模拟收银台</view>
        <view class="mock-cashier__notice">学习演示 · 不会扣款</view>
        <view class="mock-cashier__description">选择支付结果，体验订单的后续处理。</view>
        <button class="mock-cashier__primary" :disabled="paying" :loading="paying" @click="chooseMock('SUCCESS')">模拟支付成功</button>
        <button class="mock-cashier__secondary" :disabled="paying" @click="chooseMock('FAILED')">模拟支付失败</button>
        <button class="mock-cashier__secondary" :disabled="paying" @click="chooseMock('CANCELLED')">取消模拟支付</button>
        <view class="mock-cashier__recovery">
          <view class="mock-cashier__recovery-title">体验异常恢复</view>
          <view class="mock-cashier__description">模拟已付款但通知丢失，由系统查单找回结果。</view>
          <button class="mock-cashier__secondary" :disabled="paying" @click="chooseMock('LOST_NOTIFY')">模拟通知丢失</button>
        </view>
        <button v-if="needsConfirmation" class="mock-cashier__secondary" :disabled="paying" @click="handleSave">查询支付结果</button>
      </view>
      <view v-else class="box payBox">
        <view class="contion">
          <view class="example-body">
            <radio-group class="uni-list" @change="styleChange">
              <view class="uni-list-item">
                <view
                  class="uni-list-item__container"
                  v-for="(item, index) in payMethodList"
                  :key="item"
                >
                  <view class="uni-list-item__content">
                    <icon class="wechatIcon"></icon
                    ><text class="uni-list-item__content-title">{{
                      item
                    }}</text>
                  </view>
                  <view class="uni-list-item__extra">
                    <radio
                      :value="item"
                      color="#164B3B"
                      :checked="index == activeRadio"
                      class="radioIcon"
                    />
                  </view>
                </view>
              </view>
            </radio-group>
          </view>
        </view>
      </view>
      <view v-if="!mockCashier" class="bottomBox btnBox">
        <button class="add_btn" type="primary" plain="true" :disabled="paying" :loading="paying" @click="handleSave">
          {{ paying ? paymentMessage : (needsConfirmation ? "查询支付结果" : "确认支付") }}
        </button>
      </view>
    </view>
  </view>
</template>

<script>

import { paymentOrder, confirmOrderPayment, submitMockPayment } from "@/pages/api/api.js";
export default {
  data() {
    return {
      mockCashier: false,
      isMockPayment: false,
      paying: false,
      paymentMessage: "正在发起支付",
      paymentRun: 0,
      pageActive: true,
      needsConfirmation: false,
      paymentFinished: false,
      confirmTimer: null,
      confirmWait: null,
      cashierError: null,
      timeout: false,
      rocallTime: "",
      orderId: null,
      paymentStoreId: null,
      paymentUserId: null, paymentAuthEpoch: null,
      orderDataInfo: {},
      activeRadio: 0,
      payMethodList: ["微信支付"],
      times: null,
    };
  },
  created() {
    const order = this.orderData();
    this.orderDataInfo = { ...order, orderNumber: order.orderNumber || order.number, orderAmount: order.orderAmount ?? order.amount };
  },
  mounted() {
    this.runTimeBack();
  },
  onLoad(options) {
    this.orderId = options.orderId;
    this.paymentStoreId = this.$appStore.selectedStoreId;
    this.paymentUserId = this.$appStore.userId;
    this.paymentAuthEpoch = this.$appStore.authEpoch;
    const matches = saved => saved && (saved.userId == null || String(saved.userId) === String(this.$appStore.userId)) && String(saved.orderId) === String(this.orderId) && String(saved.storeId) === String(this.$appStore.selectedStoreId);
    const pending = this.$appStore.paymentInFlight;
    const legacy = uni.getStorageSync && uni.getStorageSync('paymentRecovery:' + this.$appStore.userId);
    const saved = matches(pending) ? pending : matches(legacy) ? legacy : null;
    if (saved) {
      this.needsConfirmation = true; this.orderDataInfo = saved.orderDataInfo || this.orderDataInfo;
    }
  },
  onShow() {
    this.pageActive = true;
    if (this.needsConfirmation && !this.paying) this.handleSave();
    else if (!this.paying && this.orderDataInfo.orderTime) this.runTimeBack();
  },
  onHide() { this.stopPaymentWork(); },
  onUnload() { this.stopPaymentWork(); },
  methods: {
    updatePaymentLock(active) {
      if (this.paymentUserId !== null && String(this.paymentUserId) !== String(this.$appStore.userId)) return;
      const recovery = { userId: this.paymentUserId ?? this.$appStore.userId, orderId: this.orderId, storeId: this.paymentStoreId ?? this.$appStore.selectedStoreId, orderDataInfo: this.orderDataInfo };
      this.$appStore.setPaymentInFlight(active || this.needsConfirmation ? recovery : null);
      const key = 'paymentRecovery:' + this.$appStore.userId;
      if (this.needsConfirmation) { if (uni.setStorageSync) uni.setStorageSync(key, recovery); }
      else if (uni.removeStorageSync) uni.removeStorageSync(key);
    },
    orderData() { return this.$appStore.orderData }, shopInfo() { return this.$appStore.shopInfo },
    isCurrentPayment(run) {
      return this.pageActive && run === this.paymentRun && (this.paymentUserId === null || (String(this.paymentUserId) === String(this.$appStore.userId) && this.paymentAuthEpoch === this.$appStore.authEpoch));
    },
    stopPaymentWork() {
      this.pageActive = false;
      this.paymentRun++;
      this.paying = false;
      clearTimeout(this.times);
      clearTimeout(this.confirmTimer);
      if (this.confirmWait) this.confirmWait();
      this.confirmWait = null;
      this.updatePaymentLock(false);
    },
    async handleSave() {
      if (this.paying || !this.pageActive || this.paymentFinished) return;
      if (!this.$appStore.selectedStoreId || (this.paymentStoreId && String(this.paymentStoreId) !== String(this.$appStore.selectedStoreId))) { uni.showToast({ title: '请返回订单原门店支付', icon: 'none' }); return; }
      if (this.paymentUserId === null) { this.paymentUserId = this.$appStore.userId; this.paymentAuthEpoch = this.$appStore.authEpoch; }
      this.paying = true;
      this.updatePaymentLock(true);
      const run = ++this.paymentRun;
      clearTimeout(this.times);
      try {
        if (!this.needsConfirmation && !this.timeout) {
          this.paymentMessage = "正在发起支付";
          const res = await paymentOrder({
            orderNumber: this.orderDataInfo.orderNumber,
            payMethod: 1,
          });
          if (!this.isCurrentPayment(run)) return;
          if (!res || res.code !== 1 || !res.data) throw { msg: (res && res.msg) || "暂时无法发起支付，请重试" };
          if (res.data.channel === 'MOCK') {
            this.isMockPayment = true;
            this.mockCashier = true;
            this.needsConfirmation = false;
            return;
          }
          this.needsConfirmation = true;
          this.updatePaymentLock(true);
          // Callback form behaves consistently in both Vue 2 and Vue 3 uni-app runtimes.
          this.cashierError = await new Promise(resolve => {
            try {
              uni.requestPayment({
                ...res.data,
                package: res.data.packageStr,
                success: () => resolve(null),
                fail: error => resolve(error),
              });
            } catch (error) { resolve(error); }
          });
        }
        if (!this.isCurrentPayment(run)) return;
        this.needsConfirmation = true;
        await this.confirmPaymentResult(run);
      } catch (error) {
        if (this.isCurrentPayment(run)) uni.showToast({ title: error.msg || "网络异常，请稍后重试", icon: "none" });
      } finally {
        if (this.isCurrentPayment(run)) {
          this.paying = false;
          this.updatePaymentLock(this.needsConfirmation);
          if (!this.needsConfirmation && !this.paymentFinished) this.runTimeBack();
        }
      }
    },
    async chooseMock(outcome) {
      if (this.paying || !this.pageActive || !this.mockCashier) return;
      if (!this.$appStore.selectedStoreId || (this.paymentStoreId && String(this.paymentStoreId) !== String(this.$appStore.selectedStoreId))) { uni.showToast({ title: '请返回订单原门店支付', icon: 'none' }); return; }
      if (this.paymentUserId === null) { this.paymentUserId = this.$appStore.userId; this.paymentAuthEpoch = this.$appStore.authEpoch; }
      this.paying = true;
      this.needsConfirmation = true;
      this.updatePaymentLock(true);
      this.paymentMessage = "正在提交模拟结果";
      const run = ++this.paymentRun;
      clearTimeout(this.times);
      try {
        const res = await submitMockPayment(this.orderId, outcome);
        if (!this.isCurrentPayment(run)) return;
        if (!res || res.code !== 1 || !res.data) throw { msg: (res && res.msg) || "模拟结果提交失败，请重试" };
        if (['FAILED', 'CANCELLED'].includes(outcome) && Number(res.data.payStatus) === 0) {
          this.needsConfirmation = false;
          uni.showToast({ title: outcome === 'FAILED' ? "模拟支付失败，可以重试" : "已取消模拟支付", icon: "none" });
          return;
        }
        if (outcome === 'LOST_NOTIFY') uni.showToast({ title: "已模拟通知丢失，正在查单", icon: "none" });
        await this.confirmPaymentResult(run);
      } catch (error) {
        if (this.isCurrentPayment(run)) uni.showToast({ title: error.msg || "结果未确认，请重试或查询订单", icon: "none" });
      } finally {
        if (this.isCurrentPayment(run)) {
          this.paying = false;
          this.updatePaymentLock(this.needsConfirmation);
          if (!this.needsConfirmation && !this.paymentFinished) this.runTimeBack();
        }
      }
    },
    async confirmPaymentResult(run) {
      this.paymentMessage = "正在确认支付结果";
      for (let attempt = 0; attempt < 6; attempt++) {
        if (!this.isCurrentPayment(run)) return;
        try {
          const res = await confirmOrderPayment(this.orderId);
          if (!this.isCurrentPayment(run)) return;
          if (res && res.code === 1 && res.data) {
            const order = res.data;
            const status = Number(order.status), paid = Number(order.payStatus);
            if (paid === 1 && status >= 2 && status <= 5 && !order.refundStatus) {
              this.$appStore.setOrderData({ ...this.orderDataInfo, ...order, fulfillmentType: order.fulfillmentType || this.orderDataInfo.fulfillmentType });
              this.needsConfirmation = false;
              this.paymentFinished = true;
              uni.showToast({ title: this.isMockPayment ? "模拟支付成功" : "支付成功", icon: "success" });
              uni.redirectTo({ url: "/pages/success/index?orderId=" + this.orderId + (this.isMockPayment ? "&mock=1" : "") });
              return;
            }
            if (status === 6 || order.refundStatus || [2, 3, 4].includes(paid)) {
              this.needsConfirmation = false;
              this.paymentFinished = true;
              uni.showToast({ title: "订单已取消或退款，请查看订单", icon: "none" });
              uni.redirectTo({ url: "/pages/details/index?orderId=" + this.orderId });
              return;
            }
          }
        } catch (error) {
          if (!this.isCurrentPayment(run)) return;
          if (error.staleStore || error.statusCode === 401) {
            uni.showToast({ title: error.msg || "请重新登录后查询订单", icon: "none" });
            return;
          }
          // A timeout leaves payment unconfirmed; the next query or worker may recover it.
        }
        if (attempt < 5) {
          await new Promise(resolve => {
            this.confirmWait = resolve;
            this.confirmTimer = setTimeout(resolve, 2000);
          });
          this.confirmWait = null;
        }
      }
      if (!this.isCurrentPayment(run)) return;
      const cancelled = this.cashierError && /cancel/i.test(this.cashierError.errMsg || "");
      uni.showToast({ title: cancelled ? "已取消支付，请查看订单状态" : "支付结果确认中，请稍后查询订单", icon: "none" });
      this.needsConfirmation = true;
      this.paymentFinished = true;
      uni.redirectTo({ url: "/pages/details/index?orderId=" + this.orderId });
    },
    // // 订单倒计时
    runTimeBack() {
      clearTimeout(this.times);
      if (!this.pageActive || !this.orderDataInfo.orderTime) return;
      const end = Date.parse(this.orderDataInfo.orderTime.replace(/-/g, "/"));
      const now = Date.parse(new Date());
      const m15 = 15 * 60 * 1000;
      const msec = m15 - (now - end);
      if (msec < 0) {
        this.timeout = true;
        clearTimeout(this.times);
      } else {
        let min = parseInt((msec / 1000 / 60) % 60);
        let sec = parseInt((msec / 1000) % 60);
        if (min < 10) {
          min = "0" + min;
        } else {
          min = min;
        }
        if (sec < 10) {
          sec = "0" + sec;
        } else {
          sec = sec;
        }
        this.rocallTime = min + ":" + sec;
        let that = this;
        if (min >= 0 && sec >= 0) {
          if (min === 0 && sec === 0) {
            this.timeout = true;
            clearTimeout(this.times);
            return;
          }
          this.times = setTimeout(function () {
            that.runTimeBack();
          }, 1000);
        }
      }
    },
  },
};
</script>
<style src="./../common/Navbar/navbar.scss" lang="scss" scoped></style>
<style src="./../order/style.scss" lang="scss"></style>
<style>
</style>

<style lang="scss" scoped>
.customer-box { background: #F5F8F4; min-height: 100%; }
.mock-cashier { margin: 28rpx 24rpx; padding: 32rpx; padding-bottom: calc(32rpx + env(safe-area-inset-bottom)); background: #fff; border: 1rpx solid #EADFD1; border-radius: 16rpx; }
.mock-cashier__title { color: #302522; font-size: 36rpx; font-weight: 600; }
.mock-cashier__notice { margin-top: 16rpx; padding: 16rpx 20rpx; color: #6B4316; background: #FFF1D6; border-radius: 8rpx; font-size: 28rpx; }
.mock-cashier__description { margin: 20rpx 0; color: #756B62; font-size: 26rpx; line-height: 1.65; }
.mock-cashier button { width: 100%; min-height: 88rpx; margin-top: 20rpx; padding: 12rpx 20rpx; line-height: 64rpx; font-size: 30rpx; border-radius: 12rpx; }
.mock-cashier button::after { border: none; }
.mock-cashier__primary { color: #F5F8F4; background: #164B3B; }
.mock-cashier__secondary { color: #164B3B; background: #F5F8F4; border: 1rpx solid #EADFD1; }
.mock-cashier button[disabled] { color: #756B62; background: #F0EBE5; }
.mock-cashier__recovery { margin-top: 36rpx; padding-top: 28rpx; border-top: 1rpx solid #EADFD1; }
.mock-cashier__recovery-title { color: #302522; font-size: 28rpx; font-weight: 600; }
</style>
