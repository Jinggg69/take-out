const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const withoutImports = source => source.replace(/^import[\s\S]*?from\s+["'][^"']+["'];?\s*/gm, '')

function createPage(respond) {
  const requests = [], toasts = [], redirects = []
  const context = vm.createContext({
    module: { exports: {} },
    simpleAddress: {},
    baseUrl: 'http://localhost:8080',
    store: { state: { token: 'test-token' }, commit() {} },
    uni: {
      getStorage() {},
      request(options) { requests.push(options); respond(options) },
      showToast(options) { toasts.push(options) },
      redirectTo(options) { redirects.push(options) },
    },
  })
  vm.runInContext(withoutImports(read('utils/request.js')).replace('export function', 'function'), context)
  vm.runInContext(withoutImports(read('pages/api/api.js')).replace(/export const /g, 'const '), context)
  const script = read('pages/addOrEditAddress/addOrEditAddress.vue').match(/<script>([\s\S]*?)<\/script>/)[1]
  vm.runInContext(withoutImports(script).replace('export default', 'module.exports ='), context)
  const component = context.module.exports
  const page = Object.assign(component.data(), component.methods)
  Object.assign(page.form, {
    name: '测试用户', phone: '13800138000', detail: '测试路8号',
    provinceCode: '42', cityCode: '4201', districtCode: '420111',
  })
  page.address = '湖北省/武汉市/洪山区'
  return { page, requests, toasts, redirects }
}

test('新增地址成功后返回地址列表并提交收货信息', async () => {
  const { page, requests, redirects } = createPage(options => options.success({ data: { code: 1 } }))
  await page.addAddressFun()
  assert.equal(requests.length, 1)
  assert.equal(requests[0].method, 'POST')
  assert.equal(requests[0].url, 'http://localhost:8080/user/addressBook')
  assert.equal(requests[0].data.consignee, '测试用户')
  assert.equal(requests[0].data.districtName, '洪山区')
  assert.equal(redirects[0].url, '/pages/address/address')
})

test('编辑地址使用 PUT 并保留地址 ID', async () => {
  const { page, requests, redirects } = createPage(options => options.success({ data: { code: 1 } }))
  page.showDel = true
  page.form.id = 7
  await page.addAddressFun()
  assert.equal(requests[0].method, 'PUT')
  assert.equal(requests[0].data.id, 7)
  assert.equal(redirects.length, 1)
})

for (const editing of [false, true]) {
  test(`${editing ? '编辑' : '新增'}地址失败时显示后端原因并允许重试`, async () => {
    const { page, toasts, redirects } = createPage(options => options.success({
      data: { code: 0, msg: '登录已过期，请重新登录' },
    }))
    page.showDel = editing
    await page.addAddressFun()
    assert.ok(toasts.some(toast => toast.title.includes('登录已过期')))
    assert.ok(page.saveError.includes('登录已过期'))
    assert.equal(redirects.length, 0)
    assert.equal(page.saving, false)
  })
}

test('网络请求失败时给出保存失败提示', async () => {
  const { page, toasts, redirects } = createPage(options => options.fail({ errMsg: 'request:fail timeout' }))
  await page.addAddressFun()
  assert.ok(toasts.some(toast => /失败|网络/.test(toast.title)))
  assert.equal(redirects.length, 0)
  assert.equal(page.saving, false)
})

test('保存过程中阻止重复请求，失败后可再次提交', async () => {
  const { page, requests } = createPage(() => {})
  const first = page.addAddressFun()
  assert.equal(page.saving, true)
  await page.addAddressFun()
  assert.equal(requests.length, 1)
  requests[0].success({ data: { code: 0, msg: '暂时无法保存' } })
  await first
  assert.equal(page.saving, false)
  const retry = page.addAddressFun()
  assert.equal(requests.length, 2)
  requests[1].success({ data: { code: 1 } })
  await retry
  assert.equal(page.saving, false)
})

test('联系人未填写时提示必填并阻止提交', async () => {
  const { page, requests, toasts } = createPage(() => {})
  page.form.name = ''
  await page.addAddressFun()
  assert.equal(requests.length, 0)
  assert.ok(toasts.some(toast => toast.title.includes('联系人')))
  assert.ok(page.saveError.includes('联系人'))
})
