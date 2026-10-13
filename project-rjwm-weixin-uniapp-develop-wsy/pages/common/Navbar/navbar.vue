<template>
	<view class="navBar brand-navbar">
		<!-- 个人中心 -->
		<view @click="myCenterFun" class="person-box" :style="{top:ht + 'px'}">
			<image class="test_image" src="../../../static/brand/person.png" mode="aspectFit"></image>
			<span class="person-title">个人中心</span>
		</view>
		<view class="brand-scenery">
      <view class="brand-dam"></view>
      <view class="brand-water water-back"></view>
      <view class="brand-water water-front"></view>
      <view class="brand-fish"></view>
    </view>
		<!-- <view class="leftNav" >
			<image class="logo" src="/static/logo.png"></image>
		</view> -->
	</view>
</template>

<script>
export default {
	computed: {
		// ht: function () {
		// 	let res = uni.getMenuButtonBoundingClientRect()
		// 	let num = 24
		// 	if(/iPhone.*/.test(uni.getSystemInfoSync().model)){
		// 		 num = res.top * 1.6
		// 	} else {
		// 		num = res.top * 2
		// 	}
		// 	return num
		// }
		ht: function () {
			let res = uni.getMenuButtonBoundingClientRect() 
			return res.top +5
		}
	},
	methods: {
		myCenterFun () {
			uni.navigateTo({
				url: '/pages/my/my'
			})
		}
	}
}
</script>

<style src="./navbar.scss" lang="scss"></style>
