<!--提交订单-->
<template>
  <view>
    <!-- 导航 -->
    <uni-nav-bar
      @clickLeft="goBack"
      left-icon="back"
      leftIcon="arrowleft"
      title="提交订单"
      statusBar="true"
      fixed="true"
      color="#ffffff"
      backgroundColor="#9E2026"
    ></uni-nav-bar>
    <!-- end -->
    <view class="order_content" @touchstart="touchstart">
      <view class="order_content_box">
        <!-- 地址 -->
        <address-pop
          :address="address"
          :tagLabel="tagLabel"
          :addressLabel="addressLabel"
          :nickName="nickName"
          :gender="gender"
          :phoneNumber="phoneNumber"
          :arrivalTime="arrivalTime"
          :popleft="popleft"
          :weeks="weeks"
          :newDateData="newDateData"
          :tabIndex="tabIndex"
          :selectValue="selectValue"
          @change="change"
          @goAddress="goAddress"
          @dateChange="dateChange"
          @timeClick="timeClick"
        ></address-pop>
        <!-- end -->
        <!-- 订单明细 -->
        <view class="order_list_cont">
          <view v-if="orderPriceError" class="boxPad" role="status">{{ orderPriceError }}</view>
          <!-- 菜品详情 -->
          <dish-detail
            :orderDataes="orderDataes"
            :showDisplay="showDisplay"
            :orderDishNumber="orderDishNumber"
            :orderListDataes="orderListDataes"
            :orderDishPrice="orderDishPrice"
            :deliveryFee="orderDeliveryFee"
          ></dish-detail>
          <!-- end -->
          <view class="boxPad">
            <!-- 备注、餐数数量、发票 -->
            <dish-info
              ref="dishinfo"
              :remark="remark"
              :tablewareData="tablewareData"
              :radioGroup="radioGroup"
              :activeRadio="activeRadio"
              :baseData="baseData"
              @goRemark="goRemark"
              @openPopuos="openPopuos"
              @change="change"
              @closePopup="closePopup"
              @handlePiker="handlePiker"
              @changeCont="changeCont"
              @handleRadio="handleRadio"
            ></dish-info>
            <!-- end -->
          </view>
        </view>
        <!-- end -->
      </view>
      <!-- 底部购物车、去支付 -->
      <view class="footer_order_buttom order_form">
        <view class="order_number">
          <image
            src="../../static/brand/cart-active.png"
            class="order_number_icon"
            mode="aspectFit"
          ></image>
          <view class="order_dish_num"> {{ orderDishNumber }} </view>
        </view>
        <view class="order_price">
          <text class="ico">￥ </text> {{ orderDishPrice.toFixed(2) }}
        </view>
        <view class="order_but">
          <view v-if="isHandlePy" class="order_but_rit">正在处理…</view>
          <view v-else class="order_but_rit" @click="payOrderHandle()">
            {{ pendingSubmission ? '重试确认订单' : quoteReady ? '去支付' : '刷新价格' }}
          </view>
        </view>
      </view>
      <!-- end -->
    </view>
  </view>
</template>
<script src="./index.js"></script>
<style src="./../common/Navbar/navbar.scss" lang="scss" scoped></style>
<style src="./style.scss" lang="scss" scoped></style>
