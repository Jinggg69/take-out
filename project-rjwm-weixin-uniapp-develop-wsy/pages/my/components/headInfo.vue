<!--我的头部-->
<template>
    <view class="my_info">
        <!-- 头像部分 -->
        <view class="head">
          <image class="head_image" mode="aspectFill" :src="psersonUrl"></image>
        </view>
        <!-- 姓名及手机号 -->
        <view class="phone_name"><view class="brand-member-label">均州鱼庄 · 我的账户</view>
          <!-- 姓名 -->
          <view class="name">
            <text class="name_text">{{ nickName }}</text>
            <image
              v-if="gender === 2"
              class="name_type"
              src="../../../static/girl.png"
            ></image>
            <image
              v-if="gender === 1"
              class="name_type"
              src="../../../static/boy.png"
            ></image>
          </view>
          <!-- 电话号 -->
          <view class="phone">
            <text class="phone_text">{{ phoneNumber | getPhoneNum }}</text>
          </view>
        </view>
      </view>
</template>
<script>
export default {
  // 获取父级传的数据
  props: {
    // 头像
    psersonUrl: {
      type: String,
      default: '',
    },
    // 姓名
    nickName: {
      type: String,
      default: '',
    },
    // 性别
    gender: {
      type: String,
      default: '',
    },
    // 电话
    phoneNumber: {
      type: String,
      default: '',
    },
    // 电话
    getPhoneNum: {
      type: String,
      default: '',
    }
  },
};
</script>
<style lang="scss" scoped>
.my_info { width: 100%; min-height: 196rpx; padding: 28rpx 24rpx; box-sizing: border-box; display: flex; align-items: center; background: linear-gradient(120deg,#9E2026,#7D191F); color: #FFF8EE; border-bottom: 4rpx solid #C9A45C; }
.head { width: 136rpx; flex-shrink: 0; }
.head_image { width: 108rpx; height: 108rpx; border: 3rpx solid #C9A45C; border-radius: 50%; background: #FFF8EE; }
.phone_name { flex: 1; min-width: 0; }
.brand-member-label { font-size: 24rpx; color: #EADFD1; margin-bottom: 8rpx; }
.name_text { font-size: 34rpx; font-weight: 600; overflow-wrap: anywhere; }
.name_type { width: 32rpx; height: 32rpx; margin-left: 12rpx; vertical-align: middle; }
.phone_text { font-size: 26rpx; color: #FFF8EE; }
</style>