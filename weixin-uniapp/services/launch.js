import getAppStore from '../store/index.js'
import { getMerchant, getStoreContext } from '../pages/api/api.js'
import { restorePendingContext, enterDining } from './context.js'

export async function handleLaunch(options = {}) {
  const app = getAppStore()
  await restorePendingContext()
  const pending = app.pendingSubmission || app.paymentInFlight
  if (pending) {
    const url = app.pendingSubmission ? '/pages/order/index' : '/pages/pay/index?orderId=' + encodeURIComponent(pending.orderId)
    uni.navigateTo({ url })
    return
  }
  const query = options.query || {}
  if (!query.merchantId && !query.storeId && !query.scene && !query.token) return
  if (query.token || query.scene) {
    let token
    try { token = decodeURIComponent(query.token || query.scene).replace(/^JZ:/, '') }
    catch { throw { msg: '桌码无效，请重新扫码' } }
    return enterDining(token)
  }
  const generation = app.storeGeneration
  const epoch = app.authEpoch
  const assertCurrent = () => {
    if (generation !== app.storeGeneration) throw { staleStore: true, msg: '门店选择已变化，请重新选择' }
    if (epoch !== app.authEpoch) throw { staleIdentity: true, msg: '登录状态已变化，请重新选择' }
  }
  if (query.storeId) {
    const context = (await getStoreContext(query.storeId)).data
    assertCurrent()
    if (query.merchantId && String(context.merchantId) !== String(query.merchantId)) throw { msg: '商家与门店不匹配，请重新选择' }
    app.selectContext(context)
    uni.switchTab({ url: '/pages/index/index' })
  } else {
    const merchant = (await getMerchant(query.merchantId)).data
    assertCurrent()
    app.selectMerchant(merchant)
    uni.navigateTo({ url: '/pages/stores/index?merchantId=' + encodeURIComponent(merchant.merchantId) })
  }
}
