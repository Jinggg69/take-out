const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
function client(response, transport = false) {
  const context = vm.createContext({ store: { state: {}, commit() {} }, baseUrl: 'https://test.invalid', uni: {
    getStorage() {}, request: options => transport ? options.fail(response) : options.success(response),
  } })
  const source = fs.readFileSync(path.join(__dirname, '../utils/request.js'), 'utf8')
  vm.runInContext(source.replace(/^import[^\n]*/gm, '').replace('export function', 'function'), context)
  return context.request
}
test('HTTP5xx code0 preserves status so checkout cannot treat unknown result as a confirmed business rejection', async () => {
  await assert.rejects(client({ statusCode: 503, data: { code: 0, msg: '服务异常' } })({}), error => error.statusCode === 503 && error.msg === '服务异常')
})
test('HTTP5xx code1 must not navigate as a successful order submission', async () => {
  await assert.rejects(client({ statusCode: 500, data: { code: 1, data: { id: 7 } } })({}), error => error.statusCode === 500)
})
test('HTTP4xx preserves status and useful error message', async () => {
  await assert.rejects(client({ statusCode: 401, data: { code: 0, msg: '请重新登录' } })({}), error => error.statusCode === 401 && error.msg === '请重新登录')
})
test('transport failure preserves a useful message without claiming business code0', async () => {
  await assert.rejects(client({ errMsg: 'request:fail timeout' }, true)({}), error => error.msg.includes('网络') && error.errMsg === 'request:fail timeout' && error.code !== 0)
})
test('HTTP2xx business code0 and successful response keep existing contracts', async () => {
  await assert.rejects(client({ statusCode: 200, data: { code: 0, msg: '价格已变化' } })({}), error => error.code === 0 && error.msg === '价格已变化')
  assert.equal((await client({ statusCode: 200, data: { code: 1, data: { id: 7 } } })({})).data.id, 7)
})
test('empty HTTP2xx body rejects as unknown without throwing from the native success callback', async () => {
  await assert.rejects(client({ statusCode: 200, data: null })({}), error => error.statusCode === 200 && error.code !== 0 && typeof error.msg === 'string')
})
