const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')
const dayjs = require('../utils/lib/dayjs.min.js')

const root = path.resolve(__dirname, '..')
const withoutImports = text => text.replace(/^import[\s\S]*?from\s*["'][^"']+["'];?[^\n]*/gm, '')
function load(file, dependencies = {}) {
  let source = fs.readFileSync(path.join(root, file), 'utf8')
  if (file.endsWith('.vue')) source = source.match(/<script>([\s\S]*?)<\/script>/)[1]
  const context = vm.createContext({ module: { exports: {} }, ...dependencies })
  const pricing = path.join(root, 'utils/order-pricing.js')
  if (fs.existsSync(pricing)) vm.runInContext(fs.readFileSync(pricing, 'utf8').replace(/export /g, ''), context)
  const submission = path.join(root, 'utils/order-submission.js')
  if (fs.existsSync(submission)) vm.runInContext(fs.readFileSync(submission, 'utf8').replace(/export /g, ''), context)
  vm.runInContext(withoutImports(source).replace('export default', 'module.exports ='), context)
  return context.module.exports
}
const mapState = keys => Object.fromEntries(keys.map(key => [key, function () { return this.$store.state[key] }]))
const mapMutations = keys => Object.fromEntries(keys.map(key => [key, function (value) { this.$store.commit(key, value) }]))
function createCheckout(submit = () => Promise.resolve({ code: 1, data: { id: 7 } }),
  quote = () => Promise.resolve({ code: 1, data: {
    items: [{ number: 1, amount: 119 }], goodsAmount: 119, packAmount: 1, deliveryFee: 6, amount: 126,
  } })) {
  const store = load('store/index.js', { Vue: { use() {} }, Vuex: { Store: function (options) { return options } } })
  store.commit = (key, value) => store.mutations[key](store.state, value)
  const requests = [], toasts = [], navigation = [], quoteRequests = []
  const component = load('pages/order/index.js', {
    mapState, mapMutations, Pikers: {}, AddressPop: {}, DishDetail: {}, DishInfo: {}, dayjs,
    submitOrderSubmit: params => { requests.push(params); return submit(params) },
    getOrderQuote: () => { quoteRequests.push({}); return quote() },
    presentFormat: () => '2026-10-19 14:30:00', dateFormat: () => '2026-10-20 09:30:00',
    uni: { showToast: options => toasts.push(options), navigateTo: options => navigation.push(options) },
  })
  const page = Object.assign(component.data(), component.methods, { $store: store })
  for (const [name, get] of Object.entries(component.computed)) {
    Object.defineProperty(page, name, { get() { return get.call(page) } })
  }
  store.state.orderListData = [{ number: 1, amount: 119 }]
  page.address = '测试地址'
  page.addressBookId = 3
  page.arrivalTime = '立即派送'
  page.computOrderInfo()
  // Existing checkout-action tests start after a successful initial quotation.
  page.quoteReady = true
  page.packAmount = 1
  return { page, store, requests, toasts, navigation, quoteRequests }
}

for (const gender of ['0', 0]) {
  test(`地址性别 ${JSON.stringify(gender)} 显示先生，使用地址而非其他全局性别`, () => {
    const component = load('pages/order/components/address.vue')
    const name = component.computed.cryptoName.call({ gender, nickName: '刘博涵', $store: { state: { gender: '1' } } })
    assert.equal(name, '刘先生')
  })
}

test('地址性别为女士时正确显示，未知性别不擅自添加称谓', () => {
  const component = load('pages/order/components/address.vue')
  assert.equal(component.computed.cryptoName.call({ gender: '1', nickName: '刘博涵', $store: { state: { gender: 0 } } }), '刘女士')
  assert.equal(component.computed.cryptoName.call({ gender: null, nickName: '刘博涵', $store: { state: { gender: 0 } } }), '刘博涵')
})

test('登录响应没有配送费时仍按 6 元计费，119 元菜品加 1 元打包费共 126 元', () => {
  const { page, store } = createCheckout()
  store.commit('setDeliveryFee', undefined)
  page.computOrderInfo()
  assert.equal(store.state.deliveryFee, 6)
  assert.equal(page.orderDishPrice, 126)
})

test('登录返回其他费用时仍执行统一 6 元规则', () => {
  const { page, store } = createCheckout()
  store.commit('setDeliveryFee', '3.50')
  page.computOrderInfo()
  assert.equal(store.state.deliveryFee, 6)
  assert.equal(page.orderDishPrice, 126)
})

test('数字字符串和小数单价按分计算总额', () => {
  const { page, store } = createCheckout()
  store.state.orderListData = [{ number: '3', amount: '0.10' }, { number: '1', amount: '0.20' }]
  page.computOrderInfo()
  assert.equal(page.orderDishNumber, 4)
  assert.equal(page.orderDishPrice, 10.5)
})

test('提交时配送费、总价与页面一致', async () => {
  const { page, requests, navigation } = createCheckout()
  await page.payOrderHandle()
  assert.equal(requests[0].deliveryFee, 6)
  assert.equal(requests[0].amount, 126)
  assert.equal(requests[0].packAmount, 1)
  assert.equal(navigation[0].url, '/pages/pay/index?orderId=7')
})

test('下单失败显示后端原因，按钮可再次提交', async () => {
  const { page, toasts, requests, navigation } = createCheckout(() => Promise.reject({ code: 0, msg: '配送服务暂不可用，请联系商家' }))
  await page.payOrderHandle()
  assert.equal(page.isHandlePy, false)
  assert.ok(toasts.some(item => item.title.includes('配送服务暂不可用')))
  assert.equal(navigation.length, 0)
  await page.payOrderHandle()
  assert.equal(requests.length, 2)
})

test('未选择地址时阻止下单且按钮不会锁死', async () => {
  const { page, requests } = createCheckout()
  page.address = ''
  await page.payOrderHandle()
  assert.equal(requests.length, 0)
  assert.equal(page.isHandlePy, false)
})

test('服务端报价替换旧购物车单价、名称和结算费用', async () => {
  const { page, store, requests } = createCheckout(undefined, async () => ({ code: 1, data: {
    items: [{ name: '新菜名', number: 2, amount: 17.35 }], goodsAmount: 34.70,
    packAmount: 2, deliveryFee: 6, amount: 42.70,
  } }))
  assert.equal(await page.refreshQuote(), true)
  assert.equal(store.state.orderListData[0].name, '新菜名')
  assert.equal(page.orderDishPrice, 42.70)
  assert.equal(page.packAmount, 2)
  assert.equal(page.orderDishNumber, 2)
  assert.equal(page.quoteReady, true)
  assert.equal(requests.length, 0)
})

test('报价失败时拒绝使用旧金额下单并允许重试', async () => {
  let fail = true
  const { page, requests, toasts } = createCheckout(undefined, async () => {
    if (fail) throw { msg: '菜品已停售' }
    return { code: 1, data: { items: [{ number: 1, amount: 119 }], goodsAmount: 119, packAmount: 1, deliveryFee: 6, amount: 126 } }
  })
  assert.equal(await page.refreshQuote(), false)
  await page.payOrderHandle()
  assert.equal(page.quoteReady, false)
  assert.equal(requests.length, 0)
  assert.ok(toasts.some(value => value.title.includes('菜品已停售')))
  fail = false
  await page.payOrderHandle()
  assert.equal(page.quoteReady, true)
  assert.equal(requests.length, 0)
  await page.payOrderHandle()
  assert.equal(requests.length, 1)
})

test('未取得报价时首次点击只更新价格供确认，不自动提交', async () => {
  const { page, requests, quoteRequests, toasts } = createCheckout()
  page.quoteReady = false
  await page.payOrderHandle()
  assert.equal(quoteRequests.length, 1)
  assert.equal(requests.length, 0)
  assert.ok(toasts.some(value => value.title.includes('确认')))
  await page.payOrderHandle()
  assert.equal(requests.length, 1)
  assert.equal(requests[0].amount, 126)
})

test('提交后价格变化则刷新显示，下一次点击才提交新金额', async () => {
  let attempt = 0
  const { page, requests, navigation } = createCheckout(async () => {
    if (++attempt === 1) throw { code: 0, msg: '商品价格或数量已变化，请重新确认订单金额' }
    return { code: 1, data: { id: 8 } }
  }, async () => ({ code: 1, data: {
    items: [{ number: 1, amount: 120 }], goodsAmount: 120, packAmount: 1, deliveryFee: 6, amount: 127,
  } }))
  await page.payOrderHandle()
  assert.equal(requests.length, 1)
  assert.equal(page.orderDishPrice, 127)
  assert.equal(navigation.length, 0)
  await page.payOrderHandle()
  assert.equal(requests.length, 2)
  assert.equal(requests[1].amount, 127)
  assert.equal(navigation.length, 1)
})

for (const data of [null, {}, { items: [], amount: 1 }, {
  items: [{ number: 1, amount: 119 }], goodsAmount: 119, packAmount: 1, deliveryFee: 6, amount: -1,
}, {
  items: [{ number: 1, amount: 119 }], goodsAmount: 119, packAmount: 1, deliveryFee: 6, amount: 125,
}]) {
  test(`异常报价不能恢复可下单状态：${JSON.stringify(data)}`, async () => {
    const { page, requests } = createCheckout(undefined, async () => ({ code: 1, data }))
    assert.equal(await page.refreshQuote(), false)
    await page.payOrderHandle()
    assert.equal(page.quoteReady, false)
    assert.equal(requests.length, 0)
  })
}

test('并发获取报价共享请求，完成后可再次刷新', async () => {
  let resolveQuote
  const pending = new Promise(resolve => { resolveQuote = resolve })
  const { page, quoteRequests } = createCheckout(undefined, () => pending)
  const first = page.refreshQuote()
  const second = page.refreshQuote()
  assert.equal(quoteRequests.length, 1)
  resolveQuote({ code: 1, data: { items: [{ number: 1, amount: 119 }], goodsAmount: 119, packAmount: 1, deliveryFee: 6, amount: 126 } })
  assert.equal(await first, true)
  assert.equal(await second, true)
  await page.refreshQuote()
  assert.equal(quoteRequests.length, 2)
})

test('网络结果不明时显式重试复用首次请求，即使购物车已清空、地址备注价格已变', async () => {
  let attempt = 0
  const { page, store, requests, navigation, quoteRequests, toasts } = createCheckout(async () => {
    if (++attempt === 1) throw { errMsg: 'request:fail timeout' }
    return { code: 1, data: { id: 71 } }
  })
  await page.payOrderHandle()
  assert.equal(requests.length, 1)
  assert.match(requests[0].clientRequestId, /^[a-f0-9]{32}$|^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i)
  const firstBody = JSON.stringify(requests[0])
  assert.equal(quoteRequests.length, 0)
  assert.ok(toasts.some(value => value.title.includes('重试')))
  store.state.orderListData = []
  page.computOrderInfo()
  page.quoteReady = false
  page.address = ''
  page.remark = '重试不能修改已发送内容'
  await page.payOrderHandle()
  assert.equal(JSON.stringify(requests[1]), firstBody)
  assert.equal(navigation.length, 1)
  assert.equal(navigation[0].url, '/pages/pay/index?orderId=71')
  assert.equal(page.pendingSubmission, null)
  await page.payOrderHandle()
  assert.equal(requests.length, 2)
  assert.equal(navigation.length, 1)
})

test('HTTP 5xx即使携带code0也保留提交意图，不刷新报价、不自动重试', async () => {
  const { page, requests, quoteRequests } = createCheckout(async () => { throw { statusCode: 503, code: 0, msg: '网关错误' } })
  await page.payOrderHandle()
  const firstBody = JSON.stringify(requests[0])
  assert.equal(quoteRequests.length, 0)
  await page.payOrderHandle()
  assert.equal(JSON.stringify(requests[1]), firstBody)
  assert.equal(requests.length, 2)
})

test('非2xx/4xx响应的code0不证明下单失败，继续保留相同提交意图', async () => {
  const { page, requests, quoteRequests } = createCheckout(async () => { throw { statusCode: 302, code: 0 } })
  await page.payOrderHandle()
  assert.equal(quoteRequests.length, 0)
  await page.payOrderHandle()
  assert.equal(requests[0].clientRequestId, requests[1].clientRequestId)
})

test('价格变化明确失败后放弃旧key，确认新报价创建新提交意图', async () => {
  let attempt = 0
  const { page, requests, quoteRequests } = createCheckout(async () => {
    if (++attempt === 1) return { code: 0, msg: '商品价格已变化' }
    return { code: 1, data: { id: 72 } }
  })
  await page.payOrderHandle()
  assert.equal(page.pendingSubmission, null)
  assert.equal(quoteRequests.length, 1)
  await page.payOrderHandle()
  assert.notEqual(requests[1].clientRequestId, requests[0].clientRequestId)
})

test('快速双击只发送一次，成功后再次点击也不会重建或再次导航', async () => {
  let complete
  let attempt = 0
  const { page, requests, navigation } = createCheckout(() => ++attempt === 1
    ? new Promise(resolve => { complete = resolve }) : Promise.resolve({ code: 1, data: { id: 74 } }))
  const first = page.payOrderHandle()
  await page.payOrderHandle()
  assert.equal(requests.length, 1)
  complete({ code: 1, data: { id: 73 } })
  await first
  await page.payOrderHandle()
  assert.equal(requests.length, 1)
  assert.equal(navigation.length, 1)
})

test('格式异常的成功响应没有订单号时保留原请求，防止误导航或新建订单', async () => {
  let attempt = 0
  const { page, requests, navigation, quoteRequests } = createCheckout(async () => ++attempt === 1
    ? { code: 1, data: null } : { code: 1, data: { id: 74 } })
  await page.payOrderHandle()
  assert.equal(navigation.length, 0)
  assert.equal(quoteRequests.length, 0)
  await page.payOrderHandle()
  assert.equal(requests[0].clientRequestId, requests[1].clientRequestId)
  assert.equal(navigation.length, 1)
})

for (const statusCode of [401, 422]) {
  test(`HTTP${statusCode}明确拒绝后释放旧请求，可修正后新建意图`, async () => {
    let attempt = 0
    const { page, requests, quoteRequests } = createCheckout(async () => {
      if (++attempt === 1) throw { statusCode, msg: '请求被拒绝' }
      return { code: 1, data: { id: 75 } }
    })
    await page.payOrderHandle()
    assert.equal(page.pendingSubmission, null)
    assert.equal(quoteRequests.length, 1)
    page.remark = '修正后提交'
    await page.payOrderHandle()
    assert.notEqual(requests[0].clientRequestId, requests[1].clientRequestId)
    assert.equal(requests[1].remark, '修正后提交')
  })
}
