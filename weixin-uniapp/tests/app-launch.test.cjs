const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
function app(handleLaunch) {
  const text = fs.readFileSync(path.join(__dirname, '../App.vue'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  const ctx = { handleLaunch, uni: { showToast() {} }, module: { exports: {} } }
  vm.runInNewContext(text.replace(/^import.*$/gm, '').replace('export default', 'module.exports ='), ctx)
  return { ...ctx.module.exports, $appStore: { pendingSubmission: null, paymentInFlight: null } }
}
test('冷启动与首次前台事件合并，之后相同桌码仍可再次进入', async () => {
  let calls = 0, finish
  const component = app(() => { calls++; return new Promise(resolve => { finish = resolve }) })
  const options = { query: { token: 'a'.repeat(32) } }
  const first = component.onLaunch(options)
  const foreground = component.onShow(options)
  assert.equal(calls, 1)
  finish(); await Promise.all([first, foreground])
  const later = component.onShow(options)
  assert.equal(calls, 2); finish(); await later
})
test('普通前台恢复和支付未决时不重复导航或中断原交易', () => {
  let calls = 0
  const component = app(async () => { calls++ })
  component.onShow({})
  component.$appStore.paymentInFlight = { storeId: '11' }
  component.onShow({ query: { storeId: '22' } })
  assert.equal(calls, 0)
})
