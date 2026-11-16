<template>
  <view class="customer-box">
    <uni-nav-bar
      @clickLeft="goBack"
      left-icon="back"
      leftIcon="arrowleft"
      title="订单备注"
      statusBar="true"
      fixed="true"
      color="#ffffff"
      backgroundColor="#164B3B"
    ></uni-nav-bar>
    <view class="wrap">
      <view class="box">
        <view class="contion">
          <view class="order_list">
            <view class="uni-textarea">
              <textarea
                class="beizhu_text"
                :class="{ beizhu_text_ios: platform === 'ios' }"
                placeholder-class="textarea-placeholder"
                v-model="remark"
                placeholder="无接触配送，将商品挂家门口或放前台，地址封闭管理时请电话联系"
                maxlength="100" @input="limitRemark"
                ></textarea
              >
              <text class="numText"
                ><text :class="numVal === 0 ? 'tip' : ''">{{ numVal }}</text
                >/50</text
              >
            </view>
          </view>
        </view>
      </view>
      <view class="btnBox">
        <button
          class="add_btn"
          type="primary"
          plain="true"
          @click="handleSaveRemark"
        >
          完成
        </button>
      </view>
    </view>
  </view>
</template>

<script>

export default {
  data() {
    return {
      remark: "",
    };
  },
  computed: {
    numVal() { return Math.floor(this.validateTextLength(this.remark)); },
  },
  onLoad() { this.remark = this.$appStore.remarkData || ''; },
  methods: {
    limitRemark() { while (this.validateTextLength(this.remark) > 50) this.remark = this.remark.slice(0, -1); },
    setRemark(...args) { return this.$appStore.setRemark(...args) },
    remarkData() { return this.$appStore.remarkData },
    goBack() {
      uni.navigateBack({ delta: 1 });
    },
    // 保存返回订单页
    handleSaveRemark() {
      this.setRemark(this.remark);
      uni.navigateBack({ delta: 1 });
    },
    validateTextLength(value) {
      // 中文、中文标点、全角字符按1长度，英文、英文符号、数字按0.5长度计算
      let cnReg = /([\u4e00-\u9fa5]|[\u3000-\u303F]|[\uFF00-\uFF60])/g;
      let mat = value.match(cnReg);
      let length;
      if (mat) {
        length = mat.length + (value.length - mat.length) * 0.5;
        return length;
      } else {
        return value.length * 0.5;
      }
    },
  },
};
</script>

<style src="./../common/Navbar/navbar.scss" lang="scss" scoped></style>
<style src="./../order/style.scss" lang="scss"></style>

<style lang="scss" scoped>
.wrap .box { border-radius: 20rpx; }
</style>
