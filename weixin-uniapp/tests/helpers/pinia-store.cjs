const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
module.exports = function createStore() {
  let pinia
  try { pinia = require('pinia') } catch { pinia = require('../../../frontend/junzhou-admin-vue3/node_modules/pinia') }
  const storage = new Map()
  const context = vm.createContext({ module: { exports: {} }, ...pinia, ...require('../core-harness.cjs').loadProvider(), DELIVERY_FEE: 6,
    uni: { getStorageSync: key => storage.get(key), setStorageSync: (key,value) => storage.set(key,value), removeStorageSync: key => storage.delete(key) },
  })
  const source = fs.readFileSync(path.join(__dirname, '../../store/index.js'), 'utf8')
    .replace(/^import[^\n]*\n/gm, '').replace('export default getAppStore', 'module.exports = getAppStore')
    .replace(/export /g, '')
  vm.runInContext(source, context)
  const store = context.module.exports()
  store.setSession({ token: 'test', userId: 'user-a' })
  store.selectContext({ merchantId: '1', storeId: '1', merchantName: '商家', storeName: '门店', deliveryFee: 6 })
  return store
}
