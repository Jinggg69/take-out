const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
const root = path.resolve(__dirname, '..')
function component(file, dependencies = {}) {
  const context = vm.createContext({ module: { exports: {} }, statusWord: status => ({ 1: '待付款', 6: '已取消' })[status], ...dependencies })
  const helper = path.join(root, 'utils/order-refund.js')
  if (fs.existsSync(helper)) vm.runInContext(fs.readFileSync(helper, 'utf8').replace(/export /g, ''), context)
  const script = fs.readFileSync(path.join(root, file), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  vm.runInContext(script.replace(/^import[\s\S]*?from\s*["'][^"']+["'];?[^\n]*/gm, '').replace('export default', 'module.exports ='), context)
  return context.module.exports
}
const cases = [
  ['REQUESTED', 1, '退款处理中'], ['PROCESSING', 3, '退款处理中'], ['VERIFYING', 2, '退款待核验'],
  ['SUCCESS', 2, '退款成功'], ['CLOSED', 1, '退款已关闭，请联系商家'], ['ABNORMAL', 4, '退款异常，请联系商家'],
  [null, 2, '退款待核验'], [null, 3, '退款处理中'], [null, 4, '退款异常，请联系商家'], [null, 0, '已取消'],
]
for (const file of ['pages/details/components/status.vue', 'pages/historyOrder/historyOrder.vue', 'pages/my/components/orderList.vue']) {
  for (const [refundStatus, payStatus, label] of cases) {
    test(`${file} 展示 ${refundStatus}/${payStatus} 不推断退款成功`, () => {
      const view = component(file, { mapMutations: () => ({}), Empty: {}, ReachBottom: {} })
      const order = { status: 6, refundStatus, payStatus }
      assert.equal(view.methods.statusWord.call({ $emit() {} }, order), label)
    })
  }
}
test('退款页保留分页并调用兼容查询，不把退款状态当订单status7', async () => {
  const requests = []
  const view = component('pages/historyOrder/historyOrder.vue', {
    mapMutations: () => ({}), Empty: {}, getOrderPage: () => {},
    queryOrdersCheckStatus: async params => { requests.push(params); return { code: 1, data: { records: [{ id: 7, refundStatus: 'PROCESSING', payStatus: 3 }], total: 1 } } },
    uni: { showLoading() {}, hideLoading() {} }, setTimeout: fn => fn(),
  })
  const page = Object.assign(view.data(), view.methods)
  page.changeTab(2)
  await Promise.resolve()
  assert.deepEqual(JSON.parse(JSON.stringify(requests)), [{ pageSize: 10, page: 1, payStatus: 2 }])
  assert.equal(page.recentOrdersList[0].refundStatus, 'PROCESSING')
})
