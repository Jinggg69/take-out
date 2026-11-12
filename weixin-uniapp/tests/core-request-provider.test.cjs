const assert = require('node:assert/strict')
const test = require('node:test')
test('production request provider resolves the actual Pinia instance and current store headers', async () => {
  const provider = await import('../utils/request-store.js').catch(() => null)
  assert.ok(provider, 'request provider must be available independently of the store module')
  assert.throws(() => provider.getRequestStore(), /初始化/)
  const { default: getAppStore } = await import('../store/index.js')
  const { request } = await import('../utils/request.js')
  const app = getAppStore()
  assert.equal(provider.getRequestStore(), app)
  assert.equal(app.$id, 'app')
  assert.equal(app.state, undefined)
  const headers = []
  global.uni = { setStorageSync() {}, getStorageSync() {}, request: options => { headers.push(options.header); options.success({ statusCode: 200, data: { code: 1, data: 'ok' } }) } }
  try {
    await request({ scope: 'public' })
    assert.equal(headers[0]['X-Store-Id'], undefined)
    app.setSession({ token: 'real-token', userId: 'u1' }); app.selectContext({ merchantId: '7', storeId: '2' })
    await request({ url: '/cart' })
    assert.equal(headers[1]['X-Store-Id'], '2'); assert.equal(headers[1].authentication, 'real-token')
    app.selectContext({ merchantId: '7', storeId: '3' }); await request({ url: '/cart' })
    assert.equal(headers[2]['X-Store-Id'], '3')
  } finally { delete global.uni }
})
