import getAppStore from '../store/index.js'
import { getMerchant, getStoreContext, resolveDining, joinDining } from '../pages/api/api.js'

export async function selectStoreContext(storeId) {
  const app = getAppStore()
  app.assertCanSelect(storeId)
  const generation = app.storeGeneration
  const epoch = app.authEpoch
  const response = await getStoreContext(storeId)
  if (generation !== app.storeGeneration) throw { staleStore: true, msg: '选择已变化，请重新选择门店' }
  if (epoch !== app.authEpoch) throw { staleIdentity: true, msg: '登录状态已变化，请重新选择门店' }
  app.selectContext(response.data)
  return response.data
}

export async function restoreLastSelection() {
  const last = getAppStore().lastSelection
  if (!last?.merchantId || !last?.storeId) return null
  await getMerchant(last.merchantId)
  const response = await getStoreContext(last.storeId)
  if (String(response.data.merchantId) !== String(last.merchantId)) throw { msg: '上次门店已不可用，请重新选择' }
  return response.data
}

export function openMerchantPicker() {
  return new Promise((resolve, reject) => uni.navigateTo({ url: '/pages/merchants/index', success: resolve, fail: reject }))
}

export async function goToMenu(type = 'DELIVERY') {
  const app = getAppStore()
  const stamp = capture(app)
  const pending = app.pendingSubmission || app.paymentInFlight
  if (pending && String(pending.storeId) !== String(app.selectedStoreId)) stamp.generation++
  await restorePendingContext()
  assertCurrent(app, stamp)
  if (!app.selectedStoreId) { await openMerchantPicker(); return false }
  app.setFulfillmentType(type)
  await new Promise((resolve, reject) => uni.switchTab({ url: '/pages/menu/index', success: resolve, fail: reject }))
  return true
}

const capture = app => ({ generation: app.storeGeneration, epoch: app.authEpoch })
const assertCurrent = (app, stamp) => {
  if (stamp.generation !== app.storeGeneration) throw { staleStore: true, msg: '门店选择已变化，请重新扫码' }
  if (stamp.epoch !== app.authEpoch) throw { staleIdentity: true, msg: '登录状态已变化，请重新扫码' }
}

// Unlike lastSelection, unresolved checkout is already bound to an authenticated
// user and its original store. Validate that store before exposing recovery UI.
export async function restorePendingContext() {
  const app = getAppStore()
  const pending = app.pendingSubmission || app.paymentInFlight
  if (!pending) return null
  if (!app.userId || String(pending.userId) !== String(app.userId)) throw { staleIdentity: true, msg: '请先登录原下单用户' }
  if (!pending.storeId) throw { msg: '原订单门店信息缺失，请查询订单' }
  if (String(pending.storeId) === String(app.selectedStoreId)) return null
  return selectStoreContext(pending.storeId)
}

export async function enterDining(token) {
  if (!/^[0-9a-f]{32}$/.test(token)) throw { msg: '请扫描有效的堂食桌码' }
  const app = getAppStore()
  await restorePendingContext()
  const beforeLogin = capture(app)
  const hadIdentity = !!app.token
  await app.ensureLogin()
  if (beforeLogin.generation !== app.storeGeneration) throw { staleStore: true, msg: '门店选择已变化，请重新扫码' }
  if (hadIdentity && beforeLogin.epoch !== app.authEpoch) throw { staleIdentity: true, msg: '登录状态已变化，请重新扫码' }
  let stamp = capture(app)
  const resolved = await resolveDining(token)
  assertCurrent(app, stamp)
  app.assertCanSelect(resolved.data.storeId)
  const context = (await getStoreContext(resolved.data.storeId)).data
  assertCurrent(app, stamp)
  app.selectContext(context)
  stamp = capture(app)
  const joined = await joinDining(token)
  assertCurrent(app, stamp)
  app.setDiningContext({ ...joined.data, token, storeName: resolved.data.storeName || app.storeContext.storeName })
  await goToMenu('DINE_IN')
  return app.diningContext
}

export async function scanDining() {
  const app = getAppStore()
  await restorePendingContext()
  const stamp = capture(app)
  const result = await new Promise((resolve, reject) => uni.scanCode({ onlyFromCamera: false, scanType: ['qrCode'], success: resolve, fail: reject }))
  assertCurrent(app, stamp)
  const match = /^JZ:([0-9a-f]{32})$/.exec(result.result || '')
  if (!match) throw { msg: '请扫描有效的堂食桌码' }
  return enterDining(match[1])
}
