import { createPinia, defineStore } from 'pinia'
import { DELIVERY_FEE } from '../utils/order-pricing.js'
import { userLogin, getUserProfile } from '../pages/api/api.js'
import { registerRequestStore } from '../utils/request-store.js'

export const pinia = createPinia()
const read = key => typeof uni === 'undefined' ? null : uni.getStorageSync(key)
const write = (key, value) => { if (typeof uni !== 'undefined') uni.setStorageSync(key, value) }
const remove = key => { if (typeof uni !== 'undefined') uni.removeStorageSync(key) }
const same = (a, b) => String(a ?? '') === String(b ?? '')
const flights = new WeakMap()
const localState = () => ({ fulfillmentType: 'DELIVERY', diningContext: null, storeInfo: {}, shopInfo: '', orderListData: [], lodding: false, sessionId: '', addressBackUrl: '', dishTypeIndex: 0, shopPhone: '', shopStatus: {}, orderData: {}, arrivals: '', remarkData: '', addressData: {}, deliveryFee: DELIVERY_FEE, gender: 0 })
export const useAppStore = defineStore('app', {
  state: () => ({ ...localState(), selectedMerchantId: null, selectedMerchant: null, selectedStoreId: null, storeContext: null, lastSelection: null, storeGeneration: 0, authEpoch: 0, token: '', userId: null, baseUserInfo: '', fulfillmentType: 'DELIVERY', pendingSubmission: null, paymentInFlight: null }),
  actions: {
    restoreSession() {
      this.lastSelection = read('lastSelection') || null
      const session = read('appSession')
      if (session?.token && session?.userId) this.setSession(session)
    },
    setSession(session) {
      const userId = session.userId ?? session.id
      if (!session.token || userId == null) throw { msg: '登录信息不完整' }
      const changedUser = !same(this.userId, userId)
      if (changedUser) Object.assign(this, localState())
      this.authEpoch++
      this.token = session.token
      this.userId = String(userId)
      this.baseUserInfo = session.baseUserInfo || ''
      this.pendingSubmission = null
      this.paymentInFlight = null
      const recovery = read('checkoutRecovery:' + this.userId)
      if (recovery) {
        this.pendingSubmission = recovery.pendingSubmission || null
        this.paymentInFlight = recovery.paymentInFlight || null
      }
      write('appSession', { token: this.token, userId: this.userId, baseUserInfo: this.baseUserInfo })
    },
    ensureLogin() {
      if (this.token && this.userId) return Promise.resolve(this)
      if (flights.has(this)) return flights.get(this)
      const epoch = this.authEpoch
      const login = new Promise((resolve, reject) => uni.login({ provider: 'weixin', success: result => result.code ? resolve(result.code) : reject({ msg: '微信登录未返回code' }), fail: reject }))
        .then(code => userLogin({ code }))
        .then(async response => {
          if (this.authEpoch !== epoch) throw { staleIdentity: true, msg: '登录状态已变化' }
          this.setSession(response.data)
          const sessionEpoch = this.authEpoch
          const profile = await getUserProfile()
          if (sessionEpoch !== this.authEpoch) throw { staleIdentity: true, msg: '登录状态已变化' }
          this.setBaseUserInfo(profile.data)
          return this
        }).finally(() => flights.delete(this))
      flights.set(this, login)
      return login
    },
    clearAuth() {
      this.persistRecovery()
      this.authEpoch++
      this.token = ''; this.userId = null; this.baseUserInfo = ''
      this.pendingSubmission = null; this.paymentInFlight = null
      Object.assign(this, localState())
      remove('appSession')
    },
    assertCanSelect(storeId) {
      const pending = this.pendingSubmission || this.paymentInFlight
      const lockedStoreId = pending?.storeId ?? this.selectedStoreId
      if (pending && !same(storeId, lockedStoreId)) throw { selectionLocked: true, msg: '请先确认当前订单提交或支付结果' }
    },
    selectContext(context) {
      if (!context?.storeId || !context?.merchantId) throw { msg: '门店信息无效' }
      this.assertCanSelect(context.storeId)
      if (!same(this.selectedStoreId, context.storeId) || !same(this.selectedMerchantId, context.merchantId)) {
        Object.assign(this, localState())
        this.storeGeneration++
      }
      this.selectedMerchantId = String(context.merchantId)
      this.selectedMerchant = { merchantId: String(context.merchantId), merchantName: context.merchantName }
      this.selectedStoreId = String(context.storeId)
      this.storeContext = context
      this.deliveryFee = Number(context.deliveryFee || 0)
      this.lastSelection = { merchantId: this.selectedMerchantId, merchantName: context.merchantName, storeId: this.selectedStoreId, storeName: context.storeName }
      write('lastSelection', this.lastSelection)
    },
    selectMerchant(merchant) {
      if (!merchant?.merchantId) throw { msg: '商家信息无效' }
      if (same(merchant.merchantId, this.selectedMerchantId) && !this.selectedStoreId) return
      this.clearSelection()
      this.selectedMerchantId = String(merchant.merchantId)
      this.selectedMerchant = { ...merchant, merchantName: merchant.merchantName || merchant.name }
    },
    clearSelection() {
      this.assertCanSelect(null)
      Object.assign(this, localState())
      this.selectedMerchantId = null; this.selectedMerchant = null; this.selectedStoreId = null; this.storeContext = null
      this.storeGeneration++
    },
    setHistoryMerchant(merchant) { this.selectMerchant(merchant) },
    setDiningContext(value) { this.diningContext = value },
    setFulfillmentType(value) { this.fulfillmentType = value; if (value !== 'DINE_IN') this.diningContext = null },
    persistRecovery() {
      if (this.userId) write('checkoutRecovery:' + this.userId, { pendingSubmission: this.pendingSubmission, paymentInFlight: this.paymentInFlight })
    },
    setPendingSubmission(value) {
      if (value?.userId != null && !same(value.userId, this.userId)) throw { staleIdentity: true, msg: '订单所属用户已变化' }
      this.pendingSubmission = value ? { ...value, userId: this.userId, storeId: value.storeId ?? this.selectedStoreId } : null
      this.persistRecovery()
    },
    setPaymentInFlight(value) {
      if (value?.userId != null && !same(value.userId, this.userId)) throw { staleIdentity: true, msg: '支付所属用户已变化' }
      this.paymentInFlight = value ? { ...value, userId: this.userId, storeId: value.storeId ?? this.selectedStoreId } : null
      this.persistRecovery()
    },
    setStoreInfo(value) { this.storeInfo = value },
    setShopInfo(value) { this.shopInfo = value },
    initdishListMut(value) { this.orderListData = value },
    setBaseUserInfo(value) { this.baseUserInfo = value; if (this.token && this.userId) write('appSession', { token: this.token, userId: this.userId, baseUserInfo: value }) },
    setLodding(value) { this.lodding = value },
    setSessionId(value) { this.sessionId = value },
    setAddressBackUrl(value) { this.addressBackUrl = value },
    setDishTypeIndex(value) { this.dishTypeIndex = value },
    setShopPhone(value) { this.shopPhone = value },
    setShopStatus(value) { this.shopStatus = value },
    setOrderData(value) { this.orderData = value },
    setToken(value) { if (!value) this.clearAuth(); else { this.authEpoch++; this.token = value } },
    setArrivalTime(value) { this.arrivals = value },
    setRemark(value) { this.remarkData = value },
    setAddress(value) { this.addressData = value },
    setDeliveryFee(value) { this.deliveryFee = value != null && Number.isFinite(Number(value)) && Number(value) >= 0 ? Number(value) : DELIVERY_FEE },
    setGender(value) { this.gender = value }
  }
})
export function getAppStore() { return useAppStore(pinia) }
registerRequestStore(getAppStore)
export default getAppStore
