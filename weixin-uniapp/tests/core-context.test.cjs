const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
const { loadStore } = require('./core-harness.cjs')
function loadApi() {
  const exports = {}; const calls = []
  const source = fs.readFileSync(path.join(__dirname, '../pages/api/api.js'), 'utf8').replace(/^import[^\n]*\n/gm, '').replace(/export const (\w+) =/g, 'exports.$1 =')
  vm.runInNewContext(source, { exports, request: opts => { calls.push(opts); return Promise.resolve({ data: {} }) } })
  return { api: exports, calls }
}
test('discovery is public; profile, order history, addresses and dining resolve are identity scoped', async () => {
  const { api, calls } = loadApi()
  await api.userLogin({ code: 'code' }); await api.getMerchants({}); await api.getMerchant('7'); await api.getMerchantStores('7', {}); await api.getStoreContext('2')
  assert.ok(calls.every(c => c.scope === 'public'))
  calls.length = 0
  await api.getUserProfile(); await api.getMyOrders({ merchantId: '7' }); await api.getMyOrderDetail('9', { merchantId: '7' }); await api.getOrderFilters({ merchantId: '7' }); await api.queryAddressBookList({}); await api.addAddressBook({}); await api.getAddressBookDefault(); await api.resolveDining('token')
  assert.ok(calls.every(c => c.scope === 'identity'))
  assert.equal(calls[2].url, '/user/me/orders/9'); assert.equal(calls[2].params.merchantId, '7')
})
function service(dependencies) {
  const exports = {}
  const source = fs.readFileSync(path.join(__dirname, '../services/context.js'), 'utf8').replace(/^import[^\n]*\n/gm, '').replace(/export /g, '') + '\nObject.assign(exports, { selectStoreContext, restoreLastSelection, scanDining, goToMenu, openMerchantPicker });'
  vm.runInNewContext(source, { ...dependencies, exports })
  return exports
}
test('restoring validates cached context without silently selecting a store', async () => {
  const { store } = loadStore(); store.lastSelection = { merchantId: '7', storeId: '2' }
  const ctx = { merchantId: '7', storeId: '2' }
  const s = service({ getAppStore: () => store, getStoreContext: async () => ({ data: ctx }), getMerchant: async () => ({ data: { merchantId: '7' } }) })
  assert.equal((await s.restoreLastSelection()).storeId, '2'); assert.equal(store.selectedStoreId, null)
})
test('scan joins only after login, resolution and public context validation then opens menu', async () => {
  const { store } = loadStore(); const events = []
  store.ensureLogin = async () => { events.push('login') }
  const s = service({ getAppStore: () => store, uni: { scanCode: o => o.success({ result: 'JZ:' + 'a'.repeat(32) }), switchTab: o => { events.push(o.url); o.success?.({}) } }, resolveDining: async () => { events.push('resolve'); return { data: { storeId: '2' } } }, getStoreContext: async () => { events.push('context'); return { data: { merchantId: '7', storeId: '2' } } }, joinDining: async () => { assert.equal(store.selectedStoreId, '2'); events.push('join'); return { data: { id: 3, currentSessionId: 's' } } } })
  await s.scanDining()
  assert.deepEqual(events, ['login', 'resolve', 'context', 'join', '/pages/menu/index'])
  assert.equal(store.fulfillmentType, 'DINE_IN'); assert.equal(store.diningContext.currentSessionId, 's')
})
