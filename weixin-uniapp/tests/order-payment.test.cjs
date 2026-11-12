const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')

function setup({ confirm, prepay, cashierError, cashier, mockResult, storage = new Map() } = {}) {
  const toasts = [], routes = [], requests = [], timers = new Map()
  let timerId = 0, paymentCalls = 0, confirmations = 0
  const script = fs.readFileSync(path.join(__dirname, '../pages/pay/index.vue'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  const { store } = require('./core-harness.cjs').loadStore(storage)
  store.setSession({ token: 'test', userId: 'user-a' })
  store.selectContext({ merchantId: '1', storeId: '1' })
  const context = vm.createContext({
    module: { exports: {} }, console,
    mapState: () => ({}),
    paymentOrder: async params => { requests.push(params); return prepay ? prepay() : { code: 1, data: { packageStr: 'prepay_id=test' } } },
    confirmOrderPayment: async id => { confirmations++; return confirm ? confirm(confirmations, id) : { code: 1, data: { status: 1, payStatus: 0 } } },
    submitMockPayment: async (id, outcome) => mockResult ? mockResult(id, outcome) : ({ code: 1, data: { status: 1, payStatus: 0 } }),
    cancelOrder: async () => ({ code: 1 }),
    setTimeout: (fn, ms) => {
      const id = ++timerId
      timers.set(id, fn)
      // Polling delays run without wall-clock waits; countdown timers stay pending.
      if (ms === 2000) queueMicrotask(() => { if (timers.delete(id)) fn() })
      return id
    },
    clearTimeout: id => timers.delete(id),
    uni: {
      getStorageSync: key => storage.get(key), setStorageSync: (key,value) => storage.set(key, JSON.parse(JSON.stringify(value))), removeStorageSync: key => storage.delete(key),
      showToast: options => toasts.push(options),
      redirectTo: options => routes.push(options),
      requestPayment: options => {
        paymentCalls++
        if (cashier) return cashier(options)
        if (cashierError) { options.fail?.(cashierError); return Promise.resolve([cashierError]) }
        options.success?.({ errMsg: 'requestPayment:ok' })
        return Promise.resolve([null, {}])
      },
    },
  })
  vm.runInContext(script.replace(/^import[\s\S]*?from\s*["'][^"']+["'];?[^\n]*/gm, '').replace('export default', 'module.exports ='), context)
  const component = context.module.exports
  const page = Object.assign(component.data(), component.methods, {
    $appStore: store,
    orderId: 10, orderDataInfo: { orderNumber: 'order-10', orderTime: '2099-01-01 12:00:00' },
  })
  return { page, component, toasts, routes, requests, timers, get paymentCalls() { return paymentCalls }, get confirmations() { return confirmations } }
}

test('收银台成功但后端未确认时不显示支付成功，有限轮询后提示查询订单', async () => {
  const h = setup()
  await h.page.handleSave()
  assert.equal(h.confirmations, 6)
  assert.ok(!h.toasts.some(x => x.title === '支付成功'))
  assert.ok(h.toasts.some(x => /确认|查询/.test(x.title)))
  assert.ok(!h.routes.some(x => x.url.includes('/success/')))
  assert.equal(h.page.paying, false)
})

test('仅在后端持久状态确认已付款后进入成功页', async () => {
  const h = setup({ confirm: async n => ({ code: 1, data: { status: n < 3 ? 1 : 2, payStatus: n < 3 ? 0 : 1 } }) })
  await h.page.handleSave()
  assert.equal(h.confirmations, 3)
  assert.ok(h.toasts.some(x => x.title === '支付成功'))
  assert.equal(h.routes[0].url, '/pages/success/index?orderId=10')
})

for (const data of [{ status: 6, payStatus: 1 }, { status: 6, payStatus: 3, refundStatus: 'REQUESTED' }]) {
  test(`已取消或退款中的订单不进入支付成功页 ${data.payStatus}`, async () => {
    const h = setup({ confirm: async () => ({ code: 1, data }) })
    await h.page.handleSave()
    assert.ok(!h.toasts.some(x => x.title === '支付成功'))
    assert.ok(h.routes[0].url.includes('/details/'))
  })
}

test('用户取消支付也核查后端，后端仍未付款时明确提示已取消', async () => {
  const h = setup({ cashierError: { errMsg: 'requestPayment:fail cancel' } })
  await h.page.handleSave()
  assert.ok(h.confirmations > 0)
  assert.ok(h.toasts.some(x => /取消/.test(x.title)))
  assert.ok(!h.toasts.some(x => x.title === '支付成功'))
})

test('收银台返回失败但后端已收款时以服务端成功结果为准', async () => {
  const h = setup({ cashierError: { errMsg: 'requestPayment:fail network' }, confirm: async () => ({ code: 1, data: { status: 2, payStatus: 1 } }) })
  await h.page.handleSave()
  assert.ok(h.routes[0].url.includes('/success/'))
})

test('预支付网络异常解锁按钮并恢复倒计时，能够重试', async () => {
  const h = setup({ prepay: async () => { throw { msg: '网络请求失败' } } })
  await h.page.handleSave()
  assert.equal(h.page.paying, false)
  assert.equal(h.paymentCalls, 0)
  assert.ok(h.toasts.some(x => /网络/.test(x.title)))
  assert.ok(h.page.times)
  await h.page.handleSave()
  assert.equal(h.requests.length, 2)
})

test('支付期间重复点击只发起一次预支付', async () => {
  let resolve
  const h = setup({ prepay: () => new Promise(r => { resolve = r }) })
  const first = h.page.handleSave()
  await h.page.handleSave()
  assert.equal(h.requests.length, 1)
  resolve({ code: 1, data: { packageStr: 'prepay_id=test' } })
  await first
})

test('结果查询网络错误重试到上限，不提示支付成功', async () => {
  const h = setup({ confirm: async () => { throw new Error('network') } })
  await h.page.handleSave()
  assert.equal(h.confirmations, 6)
  assert.equal(h.page.paying, false)
  assert.ok(!h.toasts.some(x => x.title === '支付成功'))
})

test('离开页面后停止轮询且异步响应不能跳转', async () => {
  let resolve
  const h = setup({ confirm: () => new Promise(r => { resolve = r }) })
  const work = h.page.handleSave()
  for (let i = 0; i < 10 && !resolve; i++) await Promise.resolve()
  assert.equal(typeof resolve, 'function')
  h.component.onUnload.call(h.page)
  resolve({ code: 1, data: { status: 2, payStatus: 1 } })
  await work
  assert.equal(h.routes.length, 0)
  assert.ok(!h.toasts.some(x => x.title === '支付成功'))
})

test('切后台后返回继续查单，不再次调起收银台', async () => {
  let resolve
  const h = setup({ confirm: n => n === 1 ? new Promise(r => { resolve = r }) : Promise.resolve({ code: 1, data: { status: 2, payStatus: 1 } }) })
  const first = h.page.handleSave()
  for (let i = 0; i < 10 && !resolve; i++) await Promise.resolve()
  h.component.onHide.call(h.page)
  resolve({ code: 1, data: { status: 2, payStatus: 1 } })
  await first
  assert.equal(h.routes.length, 0)
  h.component.onShow.call(h.page)
  for (let i = 0; i < 10; i++) await Promise.resolve()
  assert.equal(h.paymentCalls, 1)
  assert.equal(h.confirmations, 2)
  assert.ok(h.routes[0].url.includes('/success/'))
})

test('离开页面后预支付响应不能调起收银台', async () => {
  let resolve
  const h = setup({ prepay: () => new Promise(r => { resolve = r }) })
  const work = h.page.handleSave()
  h.component.onUnload.call(h.page)
  resolve({ code: 1, data: { packageStr: 'prepay_id=test' } })
  await work
  assert.equal(h.paymentCalls, 0)
  assert.equal(h.routes.length, 0)
})

test('收银台回调未返回时切后台，返回后查单且迟到回调不会重复跳转', async () => {
  let cashier
  const h = setup({ cashier: options => { cashier = options }, confirm: async () => ({ code: 1, data: { status: 2, payStatus: 1 } }) })
  const first = h.page.handleSave()
  for (let i = 0; i < 10 && !cashier; i++) await Promise.resolve()
  assert.ok(cashier)
  h.component.onHide.call(h.page)
  h.component.onShow.call(h.page)
  for (let i = 0; i < 10; i++) await Promise.resolve()
  cashier.success({ errMsg: 'requestPayment:ok' })
  await first
  assert.equal(h.paymentCalls, 1)
  assert.equal(h.confirmations, 1)
  assert.equal(h.routes.length, 1)
})

test('后端指定MOCK时打开模拟收银台，完全不调用微信收银台', async () => {
  const h = setup({ prepay: async () => ({ code: 1, data: { channel: 'MOCK', orderId: 10 } }) })
  await h.page.handleSave()
  assert.equal(h.paymentCalls, 0)
  assert.equal(h.page.mockCashier, true)
  assert.equal(h.page.paying, false)
  assert.equal(h.confirmations, 0)
})

test('模拟成功先提交后台结果并确认持久状态再跳转', async () => {
  const outcomes = []
  const h = setup({ mockResult: async (id, outcome) => { outcomes.push([id, outcome]); return { code: 1, data: { payStatus: 1, status: 2 } } }, confirm: async () => ({ code: 1, data: { payStatus: 1, status: 2 } }) })
  h.page.mockCashier = true
  await h.page.chooseMock('SUCCESS')
  assert.deepEqual(outcomes, [[10, 'SUCCESS']])
  assert.equal(h.paymentCalls, 0)
  assert.equal(h.confirmations, 1)
  assert.ok(h.routes[0].url.includes('/success/'))
})

for (const outcome of ['FAILED', 'CANCELLED']) test(`模拟${outcome}保持待付款并允许重新选择`, async () => {
  const h = setup()
  h.page.mockCashier = true
  await h.page.chooseMock(outcome)
  assert.equal(h.confirmations, 0)
  assert.equal(h.paymentCalls, 0)
  assert.equal(h.page.paying, false)
  assert.equal(h.page.needsConfirmation, false)
  assert.equal(h.routes.length, 0)
})

test('模拟接口结果不确定时保留查单入口，重复选择不会并发提交', async () => {
  let reject
  let calls = 0
  const h = setup({ mockResult: () => { calls++; return new Promise((_, r) => { reject = r }) } })
  h.page.mockCashier = true
  const work = h.page.chooseMock('SUCCESS')
  await h.page.chooseMock('SUCCESS')
  assert.equal(calls, 1)
  reject({ msg: '网络异常' })
  await work
  assert.equal(h.page.needsConfirmation, true)
  assert.equal(h.page.paying, false)
  assert.equal(h.routes.length, 0)
})

test('模拟成功页保留模拟标记，避免看起来像真实扣款', async () => {
  const h = setup({ prepay: async () => ({ code: 1, data: { channel: 'MOCK', orderId: 10 } }), mockResult: async () => ({ code: 1, data: { status: 2, payStatus: 1 } }), confirm: async () => ({ code: 1, data: { status: 2, payStatus: 1 } }) })
  await h.page.handleSave()
  await h.page.chooseMock('SUCCESS')
  assert.ok(h.routes[0].url.includes('mock=1'))
  assert.ok(h.toasts.some(x => x.title === '模拟支付成功'))
})

test('支付请求及隐藏后待核查期间真实Pinia持续阻止切店', async () => {
  let finish
  const h = setup({ prepay: () => new Promise(resolve => { finish = resolve }) })
  const work = h.page.handleSave()
  assert.throws(() => h.page.$appStore.selectContext({ merchantId: '1', storeId: '2' }), e => e.selectionLocked)
  finish({ code: 1, data: { packageStr: 'test' } })
  await work
  h.component.onUnload.call(h.page)
  assert.equal(h.page.$appStore.paymentInFlight.orderId, 10)
  assert.throws(() => h.page.$appStore.selectContext({ merchantId: '1', storeId: '2' }), e => e.selectionLocked)
})

test('支付结果不确定时保留按用户的恢复凭据，重进只查单', async () => {
  const storage = new Map()
  const h = setup({ storage })
  h.page.needsConfirmation = true
  h.page.updatePaymentLock(true)
  assert.equal(h.page.$appStore.paymentInFlight.orderId, 10)
  h.component.onUnload.call(h.page)
  assert.equal(h.page.$appStore.paymentInFlight.orderId, 10)
  assert.equal(storage.get('checkoutRecovery:user-a').paymentInFlight.orderId, 10)
  assert.equal(storage.get('paymentRecovery:user-a').orderId, 10)
  const restored = setup({ storage, confirm: async () => ({ code: 1, data: { status: 2, payStatus: 1 } }) })
  restored.component.onLoad.call(restored.page, { orderId: 10 })
  assert.equal(restored.page.needsConfirmation, true)
  await restored.page.handleSave()
  assert.equal(restored.requests.length, 0)
  assert.equal(restored.paymentCalls, 0)
  assert.equal(restored.confirmations, 1)
  assert.equal(storage.has('paymentRecovery:user-a'), false)
})


test('未知支付冷启动恢复同一用户门店锁，换用户隔离且旧页面不能清锁', async () => {
  const storage = new Map()
  const h = setup({ storage })
  await h.page.handleSave()
  h.component.onHide.call(h.page)
  const { store: cold } = require('./core-harness.cjs').loadStore(storage)
  cold.restoreSession()
  assert.equal(cold.paymentInFlight.orderId, 10)
  assert.equal(cold.paymentInFlight.storeId, '1')
  assert.throws(() => cold.selectContext({ merchantId: '1', storeId: '2' }), e => e.selectionLocked)
  h.page.$appStore.setSession({ token: 'b', userId: 'user-b' })
  assert.equal(h.page.$appStore.paymentInFlight, null)
  h.component.onUnload.call(h.page)
  assert.equal(storage.get('checkoutRecovery:user-a').paymentInFlight.orderId, 10)
  h.page.$appStore.setSession({ token: 'a', userId: 'user-a' })
  assert.equal(h.page.$appStore.paymentInFlight.orderId, 10)
})

for (const result of [{ status: 2, payStatus: 1 }, { status: 6, payStatus: 0 }]) test('服务端确认终态后清除全局支付锁 ' + result.status, async () => {
  const storage = new Map()
  const h = setup({ storage, confirm: async () => ({ code: 1, data: result }) })
  h.page.needsConfirmation = true
  h.page.updatePaymentLock(true)
  await h.page.handleSave()
  h.component.onUnload.call(h.page)
  assert.equal(h.page.$appStore.paymentInFlight, null)
  assert.equal(storage.get('checkoutRecovery:user-a').paymentInFlight, null)
  assert.equal(storage.has('paymentRecovery:user-a'), false)
})

test('预支付请求期间杀进程，从真实Pinia恢复订单号金额且只查单', async () => {
  const storage = new Map()
  let finish
  const h = setup({ storage, prepay: () => new Promise(resolve => { finish = resolve }) })
  h.page.orderDataInfo.orderAmount = 23.5
  h.page.orderDataInfo.fulfillmentType = 'PICKUP'
  const work = h.page.handleSave()
  const disk = new Map([...storage].map(([key, value]) => [key, JSON.parse(JSON.stringify(value))]))
  h.component.onHide.call(h.page)
  finish({ code: 1, data: { packageStr: 'late' } })
  await work
  assert.equal(disk.get('checkoutRecovery:user-a').paymentInFlight.orderDataInfo.orderNumber, 'order-10')
  assert.equal(disk.has('paymentRecovery:user-a'), false)
  const restored = setup({ storage: disk, confirm: async () => ({ code: 1, data: { status: 2, payStatus: 1 } }) })
  restored.component.created.call(restored.page)
  assert.equal(restored.page.orderDataInfo.orderNumber, undefined)
  restored.component.onLoad.call(restored.page, { orderId: 10 })
  assert.equal(restored.page.orderDataInfo.orderNumber, 'order-10')
  assert.equal(restored.page.orderDataInfo.orderAmount, 23.5)
  assert.equal(restored.page.orderDataInfo.fulfillmentType, 'PICKUP')
  assert.equal(restored.page.needsConfirmation, true)
  await restored.page.handleSave()
  assert.equal(restored.requests.length, 0)
  assert.equal(restored.paymentCalls, 0)
  assert.equal(restored.confirmations, 1)
  assert.equal(restored.page.$appStore.paymentInFlight, null)
  assert.equal(restored.page.$appStore.orderData.fulfillmentType, 'PICKUP')
})
