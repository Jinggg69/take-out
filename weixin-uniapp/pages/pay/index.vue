<!--购买页-->
<template>
  <view class="customer-box">
    <view class="wrap">
      <view class="contion">
        <view class="orderPay"><image class="brand-pay-logo" src="../../static/brand/junzhou-logo.png" mode="aspectFit"></image><view class="brand-pay-name">均州鱼庄</view>
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
      <view class="box payBox">
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
                      color="#9E2026"
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
      <view class="bottomBox btnBox">
        <button class="add_btn" type="primary" plain="true" :disabled="paying" :loading="paying" @click="handleSave">
          {{ paying ? paymentMessage : (needsConfirmation ? "查询支付结果" : "确认支付") }}
        </button>
      </view>
    </view>
  </view>
</template>

<script>
import { mapState } from "vuex";
import { paymentOrder, confirmOrderPayment } from "@/pages/api/api.js";
export default {
  data() {
    return {
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
      orderDataInfo: {},
      activeRadio: 0,
      payMethodList: ["微信支付"],
      times: null,
    };
  },
  created() {
    this.orderDataInfo = this.orderData();
  },
  mounted() {
    this.runTimeBack();
  },
  onLoad(options) {
    this.orderId = options.orderId;
  },
  onShow() {
    this.pageActive = true;
    if (this.needsConfirmation && !this.paying) this.handleSave();
    else if (!this.paying && this.orderDataInfo.orderTime) this.runTimeBack();
  },
  onHide() { this.stopPaymentWork(); },
  onUnload() { this.stopPaymentWork(); },
  methods: {
    ...mapState(["orderData", "shopInfo"]),
    isCurrentPayment(run) {
      return this.pageActive && run === this.paymentRun;
    },
    stopPaymentWork() {
      this.pageActive = false;
      this.paymentRun++;
      this.paying = false;
      clearTimeout(this.times);
      clearTimeout(this.confirmTimer);
      if (this.confirmWait) this.confirmWait();
      this.confirmWait = null;
    },
    async handleSave() {
      if (this.paying || !this.pageActive || this.paymentFinished) return;
      this.paying = true;
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
          this.needsConfirmation = true;
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
              this.needsConfirmation = false;
              this.paymentFinished = true;
              uni.showToast({ title: "支付成功", icon: "success" });
              uni.redirectTo({ url: "/pages/success/index?orderId=" + this.orderId });
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
      this.needsConfirmation = false;
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
.customer-box { background: #FFF8EE; min-height: 100%; }
</style>
