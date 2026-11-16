const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const test = require('node:test')
const { loadStore } = require('./core-harness.cjs')
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { resolve, promise } }
function setup(overrides = {}) {
  const { store } = loadStore(); const events = []
  const dependencies = { getAppStore: () => store, getMerchant: async id => ({ data: { merchantId: id } }), getStoreContext: async id => ({ data: { merchantId: '7', storeId: String(id) } }), resolveDining: async () => ({ data: { storeId: '2' } }), joinDining: async () => { events.push('join'); return { data: { id: 'table', currentSessionId: 'session' } } }, uni: { navigateTo: o => { events.push(o.url); o.success?.({}) }, switchTab: o => { events.push(o.url); o.success?.({}) }, scanCode: o => o.success({ result: 'JZ:' + 'a'.repeat(32) }) }, ...overrides }
  let source = fs.readFileSync(path.join(__dirname, '../services/context.js'), 'utf8').replace(/^import[^\n]*\n/gm, '').replace(/export /g, '')
  const contextExports = {}; vm.runInNewContext(source + '\nObject.assign(exports, { restorePendingContext, enterDining, scanDining, goToMenu });', { ...dependencies, exports: contextExports })
  source = fs.readFileSync(path.join(__dirname, '../services/launch.js'), 'utf8').replace(/^import[^\n]*\n/gm, '').replace(/export /g, '')
  const exports = {}; vm.runInNewContext(source + '\nexports.handleLaunch = handleLaunch;', { ...dependencies, ...contextExports, exports })
  store.ensureLogin = async () => {}
  return { ...exports, ...contextExports, store, events }
}
test('launch without query leaves normal first-time selection empty', async () => {
  const s = setup(); await s.handleLaunch(); assert.equal(s.store.selectedStoreId, null); assert.equal(s.events.length, 0)
})
test('launch store cannot claim a different merchant', async () => {
  const s = setup(); await assert.rejects(s.handleLaunch({ query: { storeId: '2', merchantId: '8' } }), e => e.msg.includes('不匹配')); assert.equal(s.store.selectedStoreId, null)
})
for (const kind of ['store', 'identity']) test(`late table entry cannot overwrite changed ${kind} context`, async () => {
  const wait = deferred(); const s = setup({ getStoreContext: () => wait.promise }); s.store.setSession({ token: 'a', userId: 'u' })
  const p = s.handleLaunch({ query: { token: 'a'.repeat(32) } }); await new Promise(r => setImmediate(r))
  if (kind === 'store') s.store.selectContext({ storeId: '9', merchantId: '9' }); else s.store.setSession({ token: 'b', userId: 'v' })
  wait.resolve({ data: { storeId: '2', merchantId: '7' } }); await assert.rejects(p, e => kind === 'store' ? e.staleStore : e.staleIdentity)
  assert.equal(s.events.length, 0); assert.equal(s.store.diningContext, null)
})
test('same warm table launch can join again after prior launch completed', async () => {
  const s = setup(); const options = { query: { token: 'a'.repeat(32) } }; await s.handleLaunch(options); await s.handleLaunch(options)
  assert.equal(s.events.filter(e => e === 'join').length, 2)
})
for (const payment of [false, true]) test(`cold launch restores original store for unresolved ${payment ? 'payment' : 'submission'}`, async () => {
  const s = setup(); s.store.setSession({ token: 'a', userId: 'u' })
  if (payment) s.store.setPaymentInFlight({ storeId: '2', orderId: 'order9' }); else s.store.setPendingSubmission({ storeId: '2', payload: { clientRequestId: 'req' } })
  await s.handleLaunch(); assert.equal(s.store.selectedStoreId, '2')
  assert.equal(s.events[0], payment ? '/pages/pay/index?orderId=order9' : '/pages/order/index')
})
test('scan cannot overwrite user selection changed while native scanner is open', async () => {
  let scanned; const s = setup({ uni: { scanCode: o => { scanned = o } } }); const p = s.scanDining(); await new Promise(r => setImmediate(r))
  s.store.selectContext({ merchantId: '9', storeId: '9' }); scanned.success({ result: 'JZ:' + 'a'.repeat(32) })
  await assert.rejects(p, e => e.staleStore); assert.equal(s.store.selectedStoreId, '9')
})


test('restored user pending checkout takes back its original store from an existing selection', async () => {
  const s = setup(); s.store.selectContext({ merchantId: '9', storeId: '9' }); s.store.setSession({ token: 'a', userId: 'u' }); s.store.setPendingSubmission({ storeId: '2', payload: { clientRequestId: 'req' } })
  await s.handleLaunch({ query: { storeId: '8' } }); assert.equal(s.store.selectedStoreId, '2'); assert.equal(s.events[0], '/pages/order/index')
})
for (const phase of ['resolve', 'join']) test(`table ${phase} result cannot mutate a newer store selection`, async () => {
  const wait = deferred(); const s = setup(phase === 'resolve' ? { resolveDining: () => wait.promise } : { joinDining: () => wait.promise })
  const p = s.handleLaunch({ query: { token: 'a'.repeat(32) } }); await new Promise(r => setImmediate(r)); s.store.selectContext({ storeId: '9', merchantId: '9' })
  wait.resolve({ data: phase === 'resolve' ? { storeId: '2' } : { id: 'old-table' } }); await assert.rejects(p, e => e.staleStore)
  assert.equal(s.store.selectedStoreId, '9'); assert.equal(s.store.diningContext, null)
})

test('table join always starts in selected target store even with queued user selection change', async () => {
  let joinedIn
  const s = setup({ joinDining: async () => { joinedIn = s.store.selectedStoreId; return { data: { id: 'old-table' } } } })
  const select = s.store.selectContext
  s.store.selectContext = context => { select(context); if (context.storeId === '2') queueMicrotask(() => select({ merchantId: '9', storeId: '9' })) }
  await assert.rejects(s.handleLaunch({ query: { token: 'a'.repeat(32) } }), e => e.staleStore)
  assert.ok(joinedIn === undefined || joinedIn === '2'); assert.equal(s.store.diningContext, null)
})

test('menu navigation cannot change the mode of a selection changed during recovery check', async () => {
  const s = setup(); s.store.selectContext({ merchantId: '7', storeId: '2' })
  const p = s.goToMenu('DINE_IN'); s.store.selectContext({ merchantId: '9', storeId: '9' })
  await assert.rejects(p, e => e.staleStore); assert.equal(s.store.fulfillmentType, 'DELIVERY'); assert.equal(s.events.length, 0)
})
