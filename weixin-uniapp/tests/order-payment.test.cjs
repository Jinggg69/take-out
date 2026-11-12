const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')

function setup({ confirm, prepay, cashierError, cashier } = {}) {
  const toasts = [], routes = [], requests = [], timers = new Map()
  let timerId = 0, paymentCalls = 0, confirmations = 0
  const script = fs.readFileSync(path.join(__dirname, '../pages/pay/index.vue'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  const context = vm.createContext({
    module: { exports: {} }, console,
    mapState: () => ({}),
    paymentOrder: async params => { requests.push(params); return prepay ? prepay() : { code: 1, data: { packageStr: 'prepay_id=test' } } },
    confirmOrderPayment: async id => { confirmations++; return confirm ? confirm(confirmations, id) : { code: 1, data: { status: 1, payStatus: 0 } } },
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
