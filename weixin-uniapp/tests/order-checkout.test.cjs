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
  vm.runInContext(withoutImports(source).replace('export default', 'module.exports ='), context)
  return context.module.exports
}
const mapState = keys => Object.fromEntries(keys.map(key => [key, function () { return this.$store.state[key] }]))
const mapMutations = keys => Object.fromEntries(keys.map(key => [key, function (value) { this.$store.commit(key, value) }]))
function createCheckout(submit = () => Promise.resolve({ code: 1, data: { id: 7 } })) {
  const store = load('store/index.js', { Vue: { use() {} }, Vuex: { Store: function (options) { return options } } })
  store.commit = (key, value) => store.mutations[key](store.state, value)
  const requests = [], toasts = [], navigation = []
  const component = load('pages/order/index.js', {
    mapState, mapMutations, Pikers: {}, AddressPop: {}, DishDetail: {}, DishInfo: {}, dayjs,
    submitOrderSubmit: params => { requests.push(params); return submit(params) },
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
  return { page, store, requests, toasts, navigation }
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
