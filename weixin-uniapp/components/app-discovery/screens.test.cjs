const { test } = require('node:test')
const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '../..')
const ref = value => ({ value })
const plain = value => JSON.parse(JSON.stringify(value))
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const pagination = readFileSync(path.join(__dirname, 'pagination.js'), 'utf8').replace(/^import .*$/mg, '').replace('export function', 'function')
function screen(file, names, mocks = {}) {
  const hooks = {}, navigation = []
  const app = { selectedMerchantId: '7', selectedStoreId: '71', selectedMerchant: { merchantName: '当前商家' }, token: 'token', storeGeneration: 0, storeContext: {}, lastSelection: null, assertCanSelect() {}, ...mocks.app }
  const context = { ref, computed: get => ({ get value() { return get() } }), useAppStore: () => app, onShow: fn => hooks.show = fn, onLoad: fn => hooks.load = fn, onReachBottom: fn => hooks.bottom = fn, uni: { switchTab: options => navigation.push(options), navigateTo: options => navigation.push(options) }, ...mocks }
  context.app = app
  vm.createContext(context)
  const source = readFileSync(path.join(root, file), 'utf8').split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/mg, '')
  vm.runInContext(pagination + '\n' + source + '\nglobalThis.exposed = { ' + names + ' }', context)
  return { ...context.exposed, app, hooks, navigation }
}
test('merchant single-store choice validates before entering home', async () => {
  let validated
  const page = screen('pages/merchants/index.vue', 'choose, actionError', {
    app: { selectContext: value => validated = value.storeId }, getMerchants: async () => ({}), getMerchantStores: async id => { assert.equal(id, '7'); return { code: 1, data: { total: 1, records: [{ storeId: '71' }] } } }, getStoreContext: async id => ({ code: 1, data: { merchantId: '7', storeId: id } })
  })
  await page.choose({ merchantId: '7' })
  assert.equal(validated, '71'); assert.equal(page.navigation[0].url, '/pages/index/index'); assert.equal(page.actionError.value, '')
})
test('multiple stores require explicit store choice and preserve merchant ID', async () => {
  let chosen
  const page = screen('pages/merchants/index.vue', 'choose', { app: { selectMerchant: value => chosen = value }, getMerchants: async () => ({}), getMerchantStores: async () => ({ code: 1, data: { total: 2, records: [] } }) })
  await page.choose({ merchantId: '12' })
  assert.equal(chosen.merchantId, '12'); assert.equal(page.navigation[0].url, '/pages/stores/index?merchantId=12')
})
test('pending checkout blocks merchant discovery selection before any request', async () => {
  let calls = 0
  const page = screen('pages/merchants/index.vue', 'choose, actionError', { app: { pendingSubmission: { storeId: '71' } }, getMerchants: async () => ({}), getMerchantStores: async () => calls++ })
  await page.choose({ merchantId: '8' })
  assert.equal(calls, 0); assert.match(page.actionError.value, /提交或支付/); assert.equal(page.navigation.length, 0)
})
test('restoration failure keeps picker visible and reports backend reason', async () => {
  const page = screen('pages/merchants/index.vue', 'resume, restoreError', { getMerchants: async () => ({}), restoreLastSelection: async () => { throw { msg: '门店已停用' } } })
  await page.resume(); assert.equal(page.restoreError.value, '门店已停用'); assert.equal(page.navigation.length, 0)
})
test('pending checkout can resume exactly its original store for recovery', async () => {
  let selected
  const page = screen('pages/merchants/index.vue', 'resume, restoreError', { app: { pendingSubmission: { storeId: '71' }, lastSelection: { storeId: '71', merchantId: '7' }, selectContext: value => selected = value }, getMerchants: async () => ({}), restoreLastSelection: async () => ({ storeId: '71', merchantId: '7' }) })
  await page.resume(); assert.equal(selected.storeId, '71'); assert.equal(page.restoreError.value, '')
})
test('store ownership is verified before mutating selected context', async () => {
  let changes = 0
  const page = screen('pages/stores/index.vue', 'choose, merchantId, actionError', { app: { selectContext: () => changes++ }, getStoreContext: async () => ({ code: 1, data: { storeId: '71', merchantId: '8' } }) })
  page.merchantId.value = '7'; await page.choose({ storeId: '71' })
  assert.equal(changes, 0); assert.match(page.actionError.value, /归属已变更/)
})
test('order filters keep merchant scope and never select a store', async () => {
  const queries = []
  const page = screen('pages/orders/index.vue', 'load, storeOptions, storeIndex, activeTab', { getMyOrders: async params => { queries.push(plain(params)); return { code: 1, data: { total: 0, records: [] } } } })
  page.storeOptions.value.push({ storeId: '72', storeName: '第二店' }); page.storeIndex.value = 1; page.activeTab.value = 'refund'
  await page.load(true)
  assert.deepEqual(queries[0], { page: 1, pageSize: 10, merchantId: '7', storeId: '72', refundOnly: true }); assert.equal(page.app.selectedStoreId, '71')
})
test('order detail carries original order store and current merchant', () => {
  const page = screen('pages/orders/index.vue', 'detail')
  page.detail({ orderId: '99', storeId: '72' })
  assert.equal(page.navigation[0].url, '/pages/details/index?orderId=99&merchantId=7&storeId=72'); assert.equal(page.app.selectedStoreId, '71')
})
test('missing merchant never fetches cross-platform orders', async () => {
  let calls = 0
  const page = screen('pages/orders/index.vue', 'load, error', { app: { selectedMerchantId: null }, getMyOrders: async () => calls++ })
  await page.load(true); assert.equal(calls, 0); assert.match(page.error.value, /选择商家/)
})
test('refund outcome labels follow server financial status', () => {
  const page = screen('pages/orders/index.vue', 'orderStatus')
  assert.equal(page.orderStatus({ refundStatus: 'ABNORMAL', status: 6 }), '退款异常')
  assert.equal(page.orderStatus({ refundStatus: 'SUCCESS', status: 6 }), '已退款')
  assert.equal(page.orderStatus({ refundStatus: null, payStatus: 2, status: 6 }), '已退款')
})
test('home fulfillment shortcuts pass real mode and stale categories never appear', async () => {
  const response = deferred(), modes = []
  const page = screen('pages/index/index.vue', 'enter, loadCategories, categories', { goToMenu: async type => modes.push(type), getCategoryList: () => response.promise })
  await page.enter('PICKUP'); assert.deepEqual(modes, ['PICKUP'])
  const flight = page.loadCategories(); page.app.storeGeneration++
  response.resolve({ code: 1, data: [{ id: 'old', name: '旧店分类' }] }); await flight
  assert.equal(page.categories.value.length, 0)
})
test('my address works without selecting merchant or store', async () => {
  const calls = []
  const page = screen('pages/my/my.vue', 'address', { app: { selectedMerchantId: null, selectedStoreId: null, ensureLogin: async () => calls.push('login'), setAddressBackUrl: value => calls.push(value) } })
  await page.address()
  assert.deepEqual(calls, ['login', '/pages/my/my']); assert.equal(page.navigation[0].url, '/pages/address/address?form=my')
})
test('pagination ignores superseded searches and retries failed page without skipping', async () => {
  const a = deferred(), b = deferred(); let calls = 0
  const context = { ref }; vm.createContext(context); vm.runInContext(pagination + '\nglobalThis.usePagination = usePagination', context)
  const model = context.usePagination(() => (++calls === 1 ? a.promise : b.promise))
  const old = model.load(true), latest = model.load(true)
  b.resolve({ code: 1, data: { total: 1, records: [{ merchantId: 'new' }] } }); await latest
  a.resolve({ code: 1, data: { total: 1, records: [{ merchantId: 'old' }] } }); await old
  assert.equal(model.records.value[0].merchantId, 'new'); assert.equal(model.loading.value, false)
  const pages = []; let failure = true
  const retry = context.usePagination(async params => { pages.push(params.page); if (failure) { failure = false; throw { msg: '暂时不可用' } } return { code: 1, data: { total: 1, records: [1] } } })
  await retry.load(true); assert.equal(retry.error.value, '暂时不可用'); await retry.load(); assert.deepEqual(pages, [1, 1]); assert.equal(retry.records.value.length, 1)
})
