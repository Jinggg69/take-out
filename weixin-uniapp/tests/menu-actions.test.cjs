const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
function menu(store, helpers, calls) {
  const source = fs.readFileSync(require('node:path').join(__dirname, '../pages/menu/index.vue'), 'utf8').match(/<script setup>([\s\S]*?)<\/script>/)[1]
  const ctx = { ...helpers, useAppStore: () => store, ref: value => ({ value }), computed: get => ({ get value() { return get() } }),
    onShow() {}, onHide() {}, onUnload() {}, openMerchantPicker() {}, scanDining() {},
    newAddShoppingCartAdd: async p => calls.push(p), getShoppingCartList: async () => ({ data: [{ dishId: '5', number: 1, amount: 12 }] }),
    uni: { showToast() {}, navigateTo: p => calls.push(p) } }
  vm.createContext(ctx)
  vm.runInContext(source.replace(/^import .*$/gm, '') + '\nglobalThis.actions = { addProduct, checkout };', ctx)
  return ctx.actions
}
test('登录等待期间切换门店后，旧菜品不能添加到新店', async () => {
  const helpers = await import('../utils/menu-cart.js')
  let finishLogin
  const calls = []
  const store = { selectedMerchantId: '1', selectedStoreId: '11', storeGeneration: 1, orderListData: [],
    ensureLogin: () => new Promise(resolve => { finishLogin = resolve }), initdishListMut(list) { this.orderListData = list } }
  const actions = menu(store, helpers, calls)
  const adding = actions.addProduct({ id: '5', type: 1 })
  store.selectedStoreId = '22'; store.selectedMerchantId = '2'; store.storeGeneration++
  finishLogin(); await adding
  assert.equal(calls.length, 0)
})
test('同店加菜保留完整规格并重新读取真实购物车', async () => {
  const helpers = await import('../utils/menu-cart.js')
  const calls = []
  const store = { token: 't', selectedMerchantId: '1', selectedStoreId: '11', storeGeneration: 1, orderListData: [],
    ensureLogin: async () => {}, initdishListMut(list) { this.orderListData = list } }
  await menu(store, helpers, calls).addProduct({ id: '5', type: 1 }, ['微辣', '少盐'])
  assert.equal(calls.length, 1)
  assert.equal(calls[0].dishFlavor, '微辣,少盐')
  assert.equal(store.orderListData.length, 1)
})
