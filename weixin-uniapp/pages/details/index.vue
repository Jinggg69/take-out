<template>
  <view>
    <!-- 导航 -->
    <uni-nav-bar @clickLeft="goBack" left-icon="back" leftIcon="arrowleft" title="订单详情" statusBar="true" fixed="true"
      color="#ffffff" backgroundColor="#164B3B"></uni-nav-bar>
    <!-- end -->
    <view class="order_content orderDetail">
      <view class="order_content_box" scroll-y="true" scroll-top="0rpx">
        <!-- 支付状态 -->
        <status ref="status" :timeout="timeout" :orderDetailsData="orderDetailsData" :rocallTime="rocallTime"
          @statusWord="statusWord" @paymentTime="paymentTime" @handlePay="handlePay" @handleReminder="handleReminder"
          @handleRefund="handleRefund" @handleCancel="handleCancel" @oneMoreOrder="oneMoreOrder"></status>
        <!-- end -->
        <view v-if="orderDetailsData.fulfillmentType === 'PICKUP'" class="box boxPad">
          <view>到店自取 · {{ orderDetailsData.fulfillmentStatus === 'READY' ? '待取餐，请到店出示凭证' : orderDetailsData.fulfillmentStatus === 'PREPARING' ? '制作中，请等待商家备餐' : orderDetailsData.fulfillmentStatus === 'COMPLETED' ? '已取餐' : orderDetailsData.fulfillmentStatus === 'CANCELLED' ? '已取消' : '等待支付或商家接单' }}</view>
          <view>取餐门店：{{ pickupStore.name || '—' }}</view>
          <view>门店地址：{{ pickupStore.address || '请联系商家确认' }}</view>
          <view>取餐号：{{ orderDetailsData.pickupNumber || '—' }}</view>
          <view>联系人：{{ orderDetailsData.contactName }} {{ orderDetailsData.contactPhone }}</view>
          <view v-if="orderDetailsData.pickupToken">核销凭证（请仅向店员出示）</view>
          <text v-if="orderDetailsData.pickupToken" selectable>{{ orderDetailsData.pickupToken }}</text>
          <button v-if="orderDetailsData.pickupToken" @click="copyPickupToken">复制核销凭证</button>
          <button @click="getBaseData(orderDetailsData.id)">刷新取餐状态</button>
        </view>
        <view v-if="orderDetailsData.fulfillmentType === 'DINE_IN'" class="box boxPad">
          <view>堂食 · {{ pickupStore.name }} · {{ orderDetailsData.tableSnapshot }}</view>
          <view>桌次：{{ orderDetailsData.diningSessionId || '历史订单' }}</view>
          <view>本单独立支付及退款；加菜请返回首页继续点餐，或重新扫码确认当前桌次。</view>
        </view>
        <!-- 订单详情 -->
        <order-detail :orderDataes="orderDataes" :orderDetailsData="orderDetailsData"
          :showDisplay="showDisplay" @toggle="showDisplay = !showDisplay"></order-detail>
        <!-- end -->
        <!-- 联系商家 -->
        <view class="box contactMerchant">
          <button @click="handlePhone('bottom', orderDetailsData.shopTelephone)">
            <view class="phoneIcon"></view>
            联系商家
          </button>
          <!-- 4 派送中 5 派送中 -->
          <button class="call-rider" v-if="(!orderDetailsData.fulfillmentType || orderDetailsData.fulfillmentType === 'DELIVERY') && [4, 5].includes(orderDetailsData.status)"
            @click="handlePhone('bottom', orderDetailsData.courierTelephone)">
            <view class="phoneIcon"></view>
            联系骑手
          </button>
        </view>
        <!-- end -->
        <!-- 配送信息 -->
        <delivery-info v-if="(!orderDetailsData.fulfillmentType || orderDetailsData.fulfillmentType === 'DELIVERY')" :orderDetailsData="orderDetailsData"></delivery-info>
        <!-- end -->
        <!-- 订单信息 -->
        <order-info :orderDetailsData="orderDetailsData"></order-info>
        <!-- end -->
      </view>
      <!-- 联系商家弹层 -->
      <uni-popup ref="commonPopup" class="comPopupBox">
        <view class="popup-content">
          <view class="text">{{ textTip }}</view>
          <view class="btn" v-if="showConfirm">
            <view @click="closePopupInfo">确认</view>
          </view>
          <view class="btn" v-else>
            <view @click="closePopupInfo">先等等</view>
            <view @click="handlePhone('bottom')">拨打电话</view>
          </view>
        </view>
      </uni-popup>
      <!-- 拨打电话弹层 -->
      <view class="container phoneCon">
        <uni-popup ref="phone" @change="change" class="popupBox">
          <view class="popup-content">
            <view>{{ phone }}</view>
            <view @click="call">呼叫</view>
            <view @click="closePopup" class="closePopup">取消</view>
          </view>
        </uni-popup>
      </view>
      <!-- end -->
    </view>
  </view>
</template>
<script src="./index.js"></script>
<style src="./../common/Navbar/navbar.scss" lang="scss" scoped></style>
<style src="../order/style.scss" lang="scss"></style>

<style lang="scss" scoped>
.orderDetail { background: #F5F8F4; }
</style>
