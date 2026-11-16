const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
let piniaRuntime
try { piniaRuntime = require('pinia') } catch { piniaRuntime = require('../../frontend/junzhou-admin-vue3/node_modules/pinia') }
function loadProvider() {
  const sandbox = { module: { exports: {} } }
  const source = fs.readFileSync(path.join(__dirname, '../utils/request-store.js'), 'utf8').replace(/export /g, '')
  vm.runInNewContext(source + '\nmodule.exports = { registerRequestStore, getRequestStore };', sandbox)
  return sandbox.module.exports
}
function loadStore(storage = new Map(), services = {}) {
  const provider = loadProvider()
  const uni = { getStorageSync: key => storage.get(key), setStorageSync: (key, value) => storage.set(key, JSON.parse(JSON.stringify(value))), removeStorageSync: key => storage.delete(key), ...services.uni }
  const sandbox = { ...piniaRuntime, ...provider, DELIVERY_FEE: 6, uni, console, Promise, userLogin: services.userLogin, getUserProfile: services.getUserProfile, module: { exports: {} } }
  let source = fs.readFileSync(path.join(__dirname, '../store/index.js'), 'utf8').replace(/^import[^\n]*\n/gm, '')
  source = source.replace(/export default[^\n]*/g, '').replace(/export (const|function)/g, '$1')
  vm.runInNewContext(source + '\nmodule.exports = { pinia, useAppStore, getAppStore };', sandbox)
  return { ...sandbox.module.exports, store: sandbox.module.exports.getAppStore(), uni, storage }
}
function loadRequest(store, uni) {
  const provider = loadProvider()
  provider.registerRequestStore(() => store)
  const sandbox = { getAppStore: provider.getRequestStore, baseUrl: 'https://test.invalid', uni, Promise, module: { exports: {} } }
  let source = fs.readFileSync(path.join(__dirname, '../utils/request.js'), 'utf8').replace(/^import[^\n]*\n/gm, '').replace('export function', 'function')
  vm.runInNewContext(source + '\nmodule.exports = request;', sandbox)
  return sandbox.module.exports
}
module.exports = { loadStore, loadRequest, loadProvider }
