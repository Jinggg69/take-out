const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')
const dayjs = require('../utils/lib/dayjs.min.js')

const source = fs.readFileSync(path.join(__dirname, '../pages/order/index.js'), 'utf8')
  .replace(/^import[\s\S]*?from\s*["'][^"']+["'];?[^\n]*/gm, '')
  .replace('export default', 'module.exports =')

function createPage({ now = '2026-10-19T13:14:00', estimate, shop = { shopId: 1 } } = {}) {
  let estimateRequests = 0
  const arrivals = []
  const context = vm.createContext({
    module: { exports: {} },
    mapState: () => ({}), mapMutations: () => ({}),
    Pikers: {}, AddressPop: {}, DishDetail: {}, DishInfo: {},
    dayjs: value => dayjs(value === undefined ? now : value),
    getEstimatedDeliveryTime: async params => {
      estimateRequests++
      return estimate ? estimate(params) : { code: 1, data: '2026-10-19 14:10:00' }
    },
    getAddressBookDefault: () => Promise.reject({ code: 0, msg: '没有查询到默认地址' }),
    queryAddressBookList: () => Promise.resolve({ code: 1, data: [] }),
    getLableVal: () => '',
    uni: { getSystemInfoSync: () => ({ platform: 'ios' }), showToast() {} },
  })
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../utils/order-pricing.js'), 'utf8').replace(/export /g, ''), context)
  vm.runInContext(source, context)
  const component = context.module.exports
  const page = Object.assign(component.data(), component.methods)
  Object.assign(page, {
    $store: { state: { baseUserInfo: {} } },
    shopInfo: () => shop,
    addressData: () => ({}), remarkData: () => '',
    setArrivalTime: value => arrivals.push(value), setGender() {},
    orderListDataes: [], deliveryFee: () => 0,
    address: '测试地址',
  })
  Object.defineProperty(page, 'orderDeliveryFee', { get() { return component.computed.orderDeliveryFee.call(page) } })
  context.page = page
  return { page, component, arrivals, get estimateRequests() { return estimateRequests },
    run: code => vm.runInContext(code, context, { timeout: 200 }) }
}

test('没有预计时间时生成今天的时间列表，不能阻塞线程', () => {
  const fixture = createPage()
  fixture.run('page.getDateDate()')
  assert.equal(fixture.page.newDateData[0], '立即派送')
  assert.equal(fixture.page.newDateData[1], '13:30')
  assert.equal(fixture.page.newDateData.at(-1), '23:00')
  assert.equal(fixture.page.newDateData.length, 21)
})

test('无效时间下反复切换明天和今天也能返回', () => {
  const fixture = createPage()
  fixture.page.orderTime = 'not-a-date'
  fixture.run('for (let i = 0; i < 3; i++) { page.dateChange(1); page.dateChange(0) }')
  assert.equal(fixture.page.tabIndex, 0)
  assert.equal(fixture.page.isTomorrow, false)
  assert.equal(fixture.page.newDateData[1], '13:30')
})

test('预计送达接口 404 时仍可选择立即派送和预约时间', async () => {
  const fixture = createPage({ estimate: () => Promise.reject({ status: 404, error: 'Not Found' }) })
  await fixture.page.getEstimatedDeliveryTime()
  assert.equal(fixture.page.arrivalTime, '立即派送')
  fixture.run('page.getDateDate()')
  assert.equal(fixture.page.newDateData[1], '13:30')
})

test('缺少商户 ID 时不发送含 undefined 的请求', async () => {
  const fixture = createPage({ shop: {} })
  await fixture.page.getEstimatedDeliveryTime()
  assert.equal(fixture.estimateRequests, 0)
  assert.equal(fixture.page.arrivalTime, '立即派送')
})

test('接口成功时使用真实预计时间生成后续时段', async () => {
  const fixture = createPage()
  await fixture.page.getEstimatedDeliveryTime()
  fixture.run('page.getDateDate()')
  assert.equal(fixture.page.arrivalTime, '14:10')
  assert.equal(fixture.page.newDateData[1], '14:30')
  assert.equal(fixture.page.newDateData.at(-1), '23:00')
})

test('深夜没有今天的预约时段时仍能结束生成', () => {
  const fixture = createPage({ now: '2026-10-19T23:45:00' })
  fixture.run('page.getDateDate()')
  assert.deepEqual(Array.from(fixture.page.newDateData), ['立即派送'])
})

test('没有默认地址时仍完成订单页时间初始化', async () => {
  const fixture = createPage({ shop: {} })
  await fixture.component.onLoad.call(fixture.page, {})
  assert.equal(fixture.page.newDateData[1], '13:30')
  assert.equal(fixture.arrivals.at(-1), '立即派送')
})
