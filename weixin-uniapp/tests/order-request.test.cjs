const assert = require('node:assert/strict')
const test = require('node:test')
const { loadStore, loadRequest } = require('./core-harness.cjs')
function client() {
  const { store } = loadStore(); const requests = []
  const request = loadRequest(store, { request: options => requests.push(options) })
  return { store, request, requests }
}
const ok = options => options.success({ statusCode: 200, data: { code: 1, data: 'ok' } })
test('public calls omit store and auth; store calls require explicit selection; identity requires login', async () => {
  const c = client()
  await assert.rejects(c.request({}), e => e.missingStore)
  await assert.rejects(c.request({ scope: 'identity' }), e => e.statusCode === 401)
  const p = c.request({ scope: 'public' }); assert.equal(c.requests[0].header['X-Store-Id'], undefined); assert.equal(c.requests[0].header.authentication, undefined); ok(c.requests[0]); await p
  assert.equal(c.requests.length, 1)
})
test('identity requests survive store changes but reject identity epoch changes', async () => {
  const c = client(); c.store.setSession({ token: 'a', userId: 'u' })
  const p = c.request({ scope: 'identity' }); c.store.selectContext({ merchantId: '1', storeId: '2' }); ok(c.requests[0]); await p
  const stale = c.request({ scope: 'identity' }); c.store.clearAuth(); c.store.setSession({ token: 'a', userId: 'u' }); ok(c.requests[1]); await assert.rejects(stale, e => e.staleIdentity)
})
for (const outcome of ['success', 'fail']) test(`store ${outcome} response rejects after switching away and back`, async () => {
  const c = client(); c.store.selectContext({ merchantId: '1', storeId: '2' })
  const p = c.request({}); c.store.selectContext({ merchantId: '1', storeId: '3' }); c.store.selectContext({ merchantId: '1', storeId: '2' })
  if (outcome === 'success') ok(c.requests[0]); else c.requests[0].fail({ errMsg: 'timeout' })
  await assert.rejects(p, e => e.staleStore)
})
test('401 clears session but never replays a write or loses user recovery', async () => {
  const c = client(); c.store.setSession({ token: 'a', userId: 'u' }); c.store.selectContext({ merchantId: '1', storeId: '2' }); c.store.setPendingSubmission({ requestId: 'req' })
  const p = c.request({ method: 'POST' }); c.requests[0].success({ statusCode: 401, data: { code: 0, msg: '重新登录' } })
  await assert.rejects(p, e => e.statusCode === 401)
  assert.equal(c.store.token, ''); assert.equal(c.requests.length, 1)
  c.store.setSession({ token: 'b', userId: 'u' }); assert.equal(c.store.pendingSubmission.requestId, 'req')
})
for (const [status, code] of [[503, 0], [500, 1], [400, 0], [200, 0]]) test(`preserves HTTP ${status}/code ${code} failure`, async () => {
  const c = client(); const p = c.request({ scope: 'public' }); c.requests[0].success({ statusCode: status, data: { code, msg: '失败' } }); await assert.rejects(p, e => e.statusCode === status && e.msg === '失败')
})
test('network and empty-body failures remain unknown, without business code zero', async () => {
  const c = client(); const p = c.request({ scope: 'public' }); c.requests[0].fail({ errMsg: 'request:fail timeout' }); await assert.rejects(p, e => e.code !== 0 && e.errMsg.includes('timeout'))
  const empty = c.request({ scope: 'public' }); c.requests[1].success({ statusCode: 200, data: null }); await assert.rejects(empty, e => e.code !== 0 && e.statusCode === 200)
})

test('late 401 cannot log out a newer session and public rejection leaves login intact', async () => {
  const c = client(); c.store.setSession({ token: 'a', userId: 'u' })
  const old = c.request({ scope: 'identity' }); c.store.setSession({ token: 'b', userId: 'u' })
  c.requests[0].success({ statusCode: 401, data: { code: 0 } }); await assert.rejects(old, e => e.staleIdentity)
  assert.equal(c.store.token, 'b')
  const publicCall = c.request({ scope: 'public' }); c.requests[1].success({ statusCode: 401, data: { code: 0 } }); await assert.rejects(publicCall, e => e.statusCode === 401)
  assert.equal(c.store.token, 'b')
})
