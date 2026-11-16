const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
function setup({ history, available = true } = {}) {
  const store = require('./helpers/pinia-store.cjs')()
  store.orderListData = [{ id: 99 }]
  const cart = store.orderListData
  const requests = [], selected = [], toasts = []
  const context = vm.createContext({ module: { exports: {} }, Status: {}, OrderDetail: {}, DeliveryInfo: {}, OrderInfo: {},
    uni: { showToast: value => toasts.push(value) },
    selectStoreContext: async id => { selected.push(id); store.selectContext({ merchantId: '1', storeId: id }); },
    getOrderDetail: async id => { requests.push(['store', id]); return { code: 1, data: { id, status: 5, storeId: '1', orderDetailList: [{ id: 1 }] } } },
    getMyOrderDetail: async (id, params) => { requests.push(['identity', id, params]); return { code: 1, data: { orderId: id, orderNumber: 'history-7', merchantId: '1', merchantName: '旧商家', storeId: '2', storeName: '原门店', storeAvailable: available, status: 5, amount: '18.30', packFee: '1.30', deliveryFee: '0.00', orderDetails: [{ name: '历史菜', number: 1, unitPrice: '17.00' }] } } },
  })
  const source = fs.readFileSync(path.join(__dirname, '../pages/details/index.js'), 'utf8').replace(/^import[\s\S]*?from\s*["'][^"']+["'];?[^\n]*/gm, '').replace('export default', 'module.exports =')
  vm.runInContext(source, context)
  const component = context.module.exports
  const page = Object.assign(component.data(), component.methods, { $appStore: store, historyMerchantId: history ? '1' : null })
  return { page, store, cart, requests, selected, toasts }
}
test('reading historical detail never overwrites the live cart', async () => {
  const h = setup()
  await h.page.getBaseData(7)
  assert.equal(h.store.orderListData, h.cart)
  assert.equal(h.page.orderDetailsData.orderDetailList[0].id, 1)
})
test('orders tab reads identity detail across stores and maps frozen line prices and names', async () => {
  const h = setup({ history: true })
  await h.page.getBaseData('7')
  assert.equal(h.requests[0][0], 'identity')
  assert.equal(h.requests[0][2].merchantId, '1')
  assert.equal(h.page.orderDetailsData.id, '7')
  assert.equal(h.page.orderDetailsData.orderDetailList[0].amount, 17)
  assert.equal(h.page.orderDetailsData.merchantName, '旧商家')
  assert.equal(JSON.parse(h.page.orderDetailsData.pricingSnapshot).packFee, '1.30')
  assert.equal(h.store.orderListData, h.cart)
  assert.equal(h.store.selectedStoreId, '1')
})
test('historical write first validates and selects its original store', async () => {
  const h = setup({ history: true })
  await h.page.getBaseData('7')
  assert.equal(await h.page.prepareOrderStore(), true)
  assert.deepEqual(h.selected, ['2'])
  assert.equal(h.store.selectedStoreId, '2')
})
test('inactive historical stores stay readable and cannot be selected for a write', async () => {
  const h = setup({ history: true, available: false })
  await h.page.getBaseData('7')
  assert.equal(await h.page.prepareOrderStore(), false)
  assert.equal(h.selected.length, 0)
  assert.equal(h.store.selectedStoreId, '1')
})

test('历史详情缺失冻结金额显示未知，不能转换为零', () => {
  const context = vm.createContext({ module: { exports: {} }, orderSnapshotMoney: () => 0, orderSnapshotStoreName: () => '', DELIVERY_FEE: 6 })
  const script = fs.readFileSync(path.join(__dirname, '../pages/details/components/orderDetail.vue'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  vm.runInContext(script.replace(/^import[^\n]*\n/gm, '').replace('export default', 'module.exports ='), context)
  const component = context.module.exports
  const page = Object.assign({ orderDetailsData: { pricingSnapshot: JSON.stringify({ amount: null, packFee: null, deliveryFee: null }) } }, component.methods)
  assert.equal(component.computed.packFee.call(page), null)
  assert.equal(component.computed.deliveryFee.call(page), null)
  assert.equal(component.computed.totalAmount.call(page), null)
  assert.equal(page.moneyText(null), '—')
  assert.equal(page.moneyText('0.00'), '0.00')
  assert.equal(page.moneyText('17.30'), '17.30')
})
