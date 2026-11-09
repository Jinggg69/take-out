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
        <view class="box boxPad">
          <button :disabled="isHandlePy || !!pendingSubmission || !!quotePromise" :aria-pressed="fulfillmentType === 'DELIVERY'" @click="changeFulfillment('DELIVERY')">外卖配送</button>
          <button :disabled="isHandlePy || !!pendingSubmission || !!quotePromise" :aria-pressed="fulfillmentType === 'PICKUP'" @click="changeFulfillment('PICKUP')">到店自取</button>
          <button v-if="$store.state.diningContext" :disabled="isHandlePy || !!pendingSubmission || !!quotePromise" @click="changeFulfillment('DINE_IN')">堂食点餐</button>
          <view v-if="fulfillmentType === 'DINE_IN'">{{ $store.state.diningContext.storeName }} · {{ $store.state.diningContext.name }}，仅商品金额，无打包及配送费。请核对桌号后支付。</view>
          <view>当前方式：{{ fulfillmentType === 'DINE_IN' ? '堂食' : fulfillmentType === 'PICKUP' ? '到店自取，无配送费' : '外卖配送' }}</view>
        </view>
        <view v-if="fulfillmentType === 'PICKUP'" class="box boxPad">
          <view>到店自取 · 制作完成后凭核销凭证取餐</view>
          <label>取餐联系人<input v-model="contactName" maxlength="32" placeholder="请填写取餐联系人" :disabled="isHandlePy || !!pendingSubmission" /></label>
          <label>联系手机号<input v-model="contactPhone" type="number" maxlength="11" placeholder="请填写11位手机号" :disabled="isHandlePy || !!pendingSubmission" /></label>
        </view>
        <!-- 地址 -->
        <address-pop v-if="fulfillmentType === 'DELIVERY'"
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
            :packFee="packFee"
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
