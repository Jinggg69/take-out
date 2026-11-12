const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const assert = require('node:assert/strict'), test = require('node:test')
test('地址加载网络失败和401都释放遮罩，保留错误并允许显式重试', async () => {
  for (const error of [{ msg: '网络异常' }, { statusCode: 401, msg: '请重新登录' }]) {
    let fail = true, hides = 0, calls = 0
    const toasts = []
    const context = vm.createContext({ module: { exports: {} }, uniNavBar: {}, Empty: {},
      queryAddressBookList: async () => { calls++; if (fail) throw error; return { code: 1, data: [] } },
      uni: { showLoading() {}, hideLoading() { hides++ }, showToast(value) { toasts.push(value) } },
    })
    const script = fs.readFileSync(path.join(__dirname, '../pages/address/address.vue'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
    vm.runInContext(script.replace(/^import[^\n]*\n/gm, '').replace('export default', 'module.exports ='), context)
    const component = context.module.exports, page = Object.assign(component.data(), component.methods)
    await page.getAddressList()
    await new Promise(resolve => setImmediate(resolve))
    assert.equal(hides, 1)
    assert.equal(page.loadError, error.msg)
    assert.equal(toasts[0].title, error.msg)
    fail = false
    await page.getAddressList()
    assert.equal(calls, 2)
    assert.equal(hides, 2)
    assert.equal(page.loadError, '')
    assert.equal(page.isEmpty, true)
  }
})
