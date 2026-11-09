const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
const root = path.resolve(__dirname, '..')
const strip = value => value.replace(/^import[\s\S]*?from\s*["'][^"']+["'];?[^\n]*/gm, '')
function loadStore(persisted) {
  const writes = []
  const context = vm.createContext({ module: { exports: {} }, DELIVERY_FEE: 6,
    Vue: { use() {} }, Vuex: { Store: function(options) { return options } },
    uni: { getStorageSync: () => persisted, setStorageSync: (key, value) => writes.push([key, value]) } })
  vm.runInContext(strip(fs.readFileSync(path.join(root, 'store/index.js'), 'utf8')).replace('export default store', 'module.exports = store'), context)
  const store = context.module.exports
  store.commit = (key, value) => store.mutations[key](store.state, value)
  return { store, writes }
}

test('selected store defaults to one, persists and reloads without reusing local cart', () => {
  const { store, writes } = loadStore('')
  assert.equal(store.state.selectedStoreId, 1)
  store.state.orderListData = [{ id: 7 }]
  store.commit('selectStore', 9)
  assert.equal(store.state.storeGeneration, 1)
  assert.equal(store.state.orderListData.length, 0)
  assert.equal(writes[0][1], 9)
  store.commit('selectStore', '9')
  assert.equal(writes.length, 1)
  assert.equal(loadStore(writes[0][1]).store.state.selectedStoreId, 9)
})

test('index store switch clears menu and dialogs, reloads without cleaning server cart', () => {
  const { store } = loadStore(1)
  let reloads = 0, serverClears = 0
  const context = vm.createContext({ module: { exports: {} }, mapState: () => ({}), mapMutations: () => ({}),
    navBar: {}, Phone: {}, popMask: {}, popCart: {}, dishDetail: {}, delShoppingCart: () => serverClears++ })
  vm.runInContext(strip(fs.readFileSync(path.join(root, 'pages/index/index.js'), 'utf8')).replace('export default', 'module.exports ='), context)
  const component = context.module.exports
  const page = Object.assign(component.data(), component.methods, { $store: store, init: () => reloads++ })
  page.stores = [{ id: 1 }, { id: 2 }]
  page.dishListItems = [{ id: 10 }]; page.openMoreNormPop = true
  page.switchStore({ detail: { value: 1 } })
  assert.equal(store.state.selectedStoreId, 2)
  assert.equal(page.dishListItems.length, 0)
  assert.equal(page.openMoreNormPop, false)
  assert.equal(page.shopStatus, null)
  assert.equal(reloads, 1)
  assert.equal(serverClears, 0)
})

const context = vm.createContext({})
vm.runInContext(fs.readFileSync(path.join(root, 'utils/order-snapshot.js'), 'utf8').replace(/export /g, ''), context)
test('historical decimal fees and store name use immutable snapshots', () => {
  const order = { packAmount: 2, deliveryFee: 6, amount: 30,
    pricingSnapshot: JSON.stringify({ packFee: 3.5, deliveryFee: 0, amount: 23.5 }),
    storeSnapshot: JSON.stringify({ name: '二店' }) }
  assert.equal(context.orderSnapshotMoney(order, 'packFee', order.packAmount), 3.5)
  assert.equal(context.orderSnapshotMoney(order, 'deliveryFee', order.deliveryFee), 0)
  assert.equal(context.orderSnapshotMoney(order, 'amount', order.amount), 23.5)
  assert.equal(context.orderSnapshotStoreName(order), '二店')
})
test('legacy or malformed order snapshots fall back to stored legacy values', () => {
  for (const pricingSnapshot of [null, '', '{bad', '[]', '{"packFee":null}', '{"packFee":-1}']) {
    assert.equal(context.orderSnapshotMoney({ pricingSnapshot }, 'packFee', 2), 2)
  }
  assert.equal(context.orderSnapshotStoreName({ storeSnapshot: 'bad', shopName: '原店' }), '原店')
})
