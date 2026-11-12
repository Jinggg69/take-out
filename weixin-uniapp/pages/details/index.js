
import {
	getOrderDetail,
	getMyOrderDetail,
	repetitionOrder,
	reminderOrder,
	cancelOrder,
} from '../api/api.js'

import { baseUrl } from '../../utils/env'
import { call } from '@/utils/index.js'
import { selectStoreContext } from '../../services/context.js'
import Status from './components/status.vue'//订单状态
import OrderDetail from "./components/orderDetail.vue" //菜品详情
import DeliveryInfo from "./components/deliveryInfo.vue" //配送信息
import OrderInfo from "./components/orderInfo.vue" //订单信息

export default {
	data() {
		return {
			showDisplay: false,
            historyMerchantId: null,
			rocallTime: '',
			textTip: '',
			showConfirm: false,
			orderDetailsData: {},
			timeout: false,
			isOvertime: false,
			orderId: null,
			activeRadio: 0,
			time: null,
			isPayment: false,
			times: null,
			phone: ""
		}
	},

	computed: {
        pickupStore() {
            try { return JSON.parse(this.orderDetailsData.storeSnapshot || '{}') || {} } catch (error) { return {} }
        },
		orderListDataes: function () {
			return this.orderDetailsData.orderDetailList || []
		},

		// // 处理订单详情列表
		orderDataes: function () {
			let testList = []
			if (this.showDisplay === false) {
				if (this.orderListDataes.length > 2) {
					for (var i = 0; i < 2; i++) {
						testList.push(this.orderListDataes[i])
					}
				} else {
					testList = this.orderListDataes
				}
				return testList
			} else {
				return this.orderListDataes
			}
		}
	},
	//   组件
	components: { Status, OrderDetail, DeliveryInfo, OrderInfo },
	onLoad(options) {
		this.historyMerchantId = options.merchantId || null
		this.getBaseData(options.orderId)
	},

	onUnload() { clearTimeout(this.times) },
	methods: {
        async prepareOrderStore() {
            const order = this.orderDetailsData
            if (order.storeAvailable === false || !order.storeId) { uni.showToast({ title: '该门店已停用，仅可查看历史订单', icon: 'none' }); return false }
            try {
                await selectStoreContext(order.storeId)
                return this.canWriteOrder()
            } catch (error) { uni.showToast({ title: error.msg || '原门店暂不可用', icon: 'none' }); return false }
        },
        canWriteOrder() {
            const order = this.orderDetailsData
            if (order.storeAvailable === false || !order.storeId || String(order.storeId) !== String(this.$appStore.selectedStoreId) || (this.historyMerchantId && String(this.historyMerchantId) !== String(this.$appStore.selectedMerchantId))) {
                uni.showToast({ title: '请先选择订单原门店后操作', icon: 'none' }); return false
            }
            return true
        },
        copyPickupToken() {
            if (this.orderDetailsData.pickupToken) uni.setClipboardData({ data: this.orderDetailsData.pickupToken })
        },
		setOrderData(...args) { return this.$appStore.setOrderData(...args) },
		shopInfo() { return this.$appStore.shopInfo }, orderListData() { return this.$appStore.orderListData }, shopPhone() { return this.$appStore.shopPhone }, orderData() { return this.$appStore.orderData },
		// 获取订单详情
		getBaseData(id) {
			return (this.historyMerchantId ? getMyOrderDetail(id, { merchantId: this.historyMerchantId }) : getOrderDetail(id)).then(res => {
				if (res.code === 1) {
					const data = res.data
                    this.orderDetailsData = this.historyMerchantId ? {
                        ...data, id: data.orderId, number: data.orderNumber,
                        orderDetailList: (data.orderDetails || []).map(item => ({ ...item, amount: item.unitPrice == null || item.unitPrice === '' ? null : Number(item.unitPrice) })),
                        pricingSnapshot: JSON.stringify({ amount: data.amount, packFee: data.packFee, deliveryFee: data.deliveryFee }),
                        storeSnapshot: JSON.stringify({ name: data.storeName }),
                        shopName: data.storeName, address: data.deliveryAddress, phone: data.contactPhone,
                        consignee: data.contactName, tableSnapshot: data.tableName
                    } : data
					// Keep historical lines local to this order.
					if (this.orderDetailsData.status === 1) {
						if (!this.historyMerchantId) this.runTimeBack(this.orderDetailsData.orderTime)
					}
				}
			}).catch(error => { uni.showToast({ title: error.msg || '订单加载失败', icon: 'none' }) })
		},

		// 催单
		async handleReminder(val) {
            if (!await this.prepareOrderStore()) return
			reminderOrder(val.id).then(res => {
				if (res.code === 1) {
					this.showConfirm = true
					this.textTip = '您的催单信息已发出！'
					this.$refs.commonPopup.open(val.type)
					this.orderId = val.id
				}
			})
		},
		// 取消订单接口
		async cancel(type, obj) {
            if (!await this.prepareOrderStore()) return
			cancelOrder(obj.id).then(res => {
				if (res.code === 1) {
					this.isPayment = true
					this.showConfirm = true
					this.textTip = '您的订单已取消！'
					this.$refs.commonPopup.open(type)
					this.orderId = obj.id
				}
			})
		},
		// 取消订单
		handleCancel(val) {
			if (val.obj.status === 1 || val.obj.status === 2) {
				this.cancel(val.type, val.obj)
			} else {
				this.showConfirm = false
				this.$refs.commonPopup.open(val.type)
				this.textTip = '请联系商家进行取消！'
			}
		},
		// 再来一单
		async oneMoreOrder(id) {
			if (!await this.prepareOrderStore()) return
			repetitionOrder(id).then(res => {
				if (res.code === 1) {
					uni.switchTab({
						url: '/pages/menu/index'
					})
				}
			})
		},
		// 处理状态
		statusWord(status) {
			if (this.timeout && status === 1 || this.orderDetailsData.status === 6) {
				return '订单已取消'
			}
			switch (status) {
				case 2:
					return '等待商户接单'
				case 3:
					return '商家已接单'
				case 4:
					return '订单派送中'
				case 5:
					return '订单已完成'
			}
		},
		// 订单倒计时
		runTimeBack(time) {
			const end = Date.parse(time.replace(/-/g, "/"))

			const now = Date.parse(new Date())
			const m15 = 15 * 60 * 1000
			const msec = m15 - (now - end)
			if (msec < 0) {
				this.timeout = true
				clearTimeout(this.times)
				// Expiry is handled by the server. //超时的时候取消订单
			} else {
				let min = parseInt(msec / 1000 / 60 % 60)
				let sec = parseInt(msec / 1000 % 60)
				if (min < 10) {
					min = '0' + min
				} else {
					min = min
				}
				if (sec < 10) {
					sec = '0' + sec
				} else {
					sec = sec
				}
				this.rocallTime = min + ':' + sec
				let that = this
				if (min >= 0 && sec >= 0) {
					if (min === 0 && sec === 0) {
						this.timeout = true
						clearTimeout(this.times)
						// Expiry is handled by the server. //超时的时候取消订单
						return
					}
					this.times = setTimeout(function () {
						that.runTimeBack(time)
					}, 1000)
				}
			}
		},

		// 重新拼装image
		getNewImage(image) {
			return `${baseUrl}/common/download?name=${image}`
		},
		// 返回上一级
		goBack() {
			uni.switchTab({
				url: '/pages/orders/index'
			})
		},
		openPopuos(type) {
			this.$refs.commonPopup.open(type)
		},
		// 联系商家进行退款弹层
		handleRefund(type) {
			this.showConfirm = false
			this.openPopuos(type)
			this.textTip = '请联系商家进行退款！'
		},
		// 拨打电话弹层
		handlePhone(type, phone) {
			// 暂时关闭打电话
			this.$refs.phone.open(type)

			this.phone = phone;
		},
		// 关闭弹层
		closePopup(type) {
			this.$refs.phone.close(type)
		},
		// closePopupInfo
		closePopupInfo(type) {
			this.$refs.commonPopup.close(type)
			this.getBaseData(this.orderId)
		},
		// 立即支付
		async handlePay(id) {
            if (!await this.prepareOrderStore()) return
			const obj = {
				orderNumber: this.orderDetailsData.number,
				orderAmount: this.orderDetailsData.amount,
                fulfillmentType: this.orderDetailsData.fulfillmentType,
                storeId: this.orderDetailsData.storeId,
				orderTime: this.orderDetailsData.orderTime
			}
			this.setOrderData(obj)
			uni.redirectTo({
				url: '/pages/pay/index?orderId=' + id
			})
		},
		call() {
			call(this.phone)
		},
	}
}