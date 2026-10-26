import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import type { AddressInfo } from 'node:net'
import { after, before, test } from 'node:test'
import { createPinia } from 'pinia'
import { createHttpClient } from '../src/utils/http.ts'
import { createAuthApi } from '../src/api/auth.ts'
import { createSessionStorage } from '../src/utils/cookies.ts'
import { createUserStore } from '../src/stores/user.ts'

const requests: { method: string; url: string; body: string; token?: string }[] = []
let baseURL = ''
let logoutFails = false
let passwordFails = false
let logoutBusinessFails = false
const user = { id: 1, name: '张师傅', userName: 'admin', token: 'fixture-token' }
const server = createServer(async (req, res) => {
  let body = ''
  for await (const chunk of req) body += chunk.toString()
  requests.push({ method: req.method ?? '', url: req.url ?? '', body, token: req.headers.token as string })
  res.setHeader('Content-Type', 'application/json')
  if (req.url === '/api/employee/logout' && logoutFails) {
    res.statusCode = 500; res.end('{"code":0,"msg":"退出失败"}'); return
  }
  if (req.url === '/api/employee/logout' && logoutBusinessFails) {
    res.end('{"code":0,"msg":"会话撤销失败"}'); return
  }
  if (req.url === '/api/employee/editPassword' && passwordFails) {
    res.end('{"code":0,"msg":"原密码错误"}'); return
  }
  if (req.url === '/api/employee/login') {
    const payload = JSON.parse(body)
    res.end(JSON.stringify(payload.password === 'wrong123'
      ? { code: 0, data: null, msg: '账号或密码错误' }
      : { code: '1', data: user }))
  } else {
    res.end(JSON.stringify({ code: 1, data: req.url === '/api/shop/status' ? 0 : null }))
  }
})
before(async () => {
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  baseURL = `http://127.0.0.1:${(server.address() as AddressInfo).port}/api`
})
after(async () => {
  server.closeAllConnections()
  await new Promise<void>((resolve, reject) => server.close(e => e ? reject(e) : resolve()))
})
function setup(initial: Record<string, string> = {}) {
  const values = new Map(Object.entries(initial))
  const storage = createSessionStorage({
    get: name => values.get(name),
    set: (name, value) => { values.set(name, value) },
    remove: name => { values.delete(name) },
  })
  const api = createAuthApi(createHttpClient({ baseURL, getToken: storage.getToken }))
  const store = createUserStore(api, storage)(createPinia())
  return { values, storage, api, store }
}
test('login trims only username and persists the original cookie names and JSON user info', async () => {
  const { store, values } = setup()
  await store.login({ username: ' admin ', password: ' 123456 ' })
  assert.deepEqual(JSON.parse(requests.at(-1)?.body ?? ''), { username: 'admin', password: ' 123456 ' })
  assert.equal(values.get('token'), 'fixture-token')
  assert.equal(values.get('username'), 'admin')
  assert.deepEqual(JSON.parse(values.get('user_info') ?? ''), user)
  assert.equal(values.has('userInfo'), false)
  assert.equal(store.displayName, '张师傅')
  assert.deepEqual(store.roles, [])
})
test('refresh restores the session without calling a new user-info or roles API', () => {
  const count = requests.length
  const { store } = setup({ token: 'legacy-token', username: 'employee', user_info: JSON.stringify(user) })
  assert.equal(store.token, 'legacy-token')
  assert.equal(store.displayName, '张师傅')
  assert.equal(requests.length, count)
})
test('failed login retains legacy username persistence without creating a token', async () => {
  const { store, values } = setup()
  await assert.rejects(store.login({ username: ' employee ', password: 'wrong123' }), /账号或密码错误/)
  assert.equal(values.get('username'), 'employee')
  assert.equal(values.has('token'), false)
})
test('malformed user info does not crash the layout or silently revoke a valid token', () => {
  const { store } = setup({ token: 'legacy-token', username: 'employee', user_info: '{broken' })
  assert.equal(store.token, 'legacy-token')
  assert.equal(store.displayName, 'employee')
})
test('successful logout clears login cookies but keeps unrelated legacy preferences', async () => {
  const { store, values } = setup({
    token: 'legacy-token', username: 'employee', user_info: JSON.stringify(user), userInfo: '{}',
    storeId: '7', sidebar_status: 'closed', print: '{}', new: '2',
  })
  await store.logout()
  assert.equal(requests.at(-1)?.url, '/api/employee/logout')
  assert.equal(requests.at(-1)?.method, 'POST')
  assert.equal(requests.at(-1)?.body, '')
  for (const name of ['token', 'username', 'user_info', 'userInfo']) assert.equal(values.has(name), false)
  for (const name of ['storeId', 'sidebar_status', 'print', 'new']) assert.equal(values.has(name), true)
  assert.equal(store.token, '')
})
test('transport failure during logout leaves the existing session intact', async () => {
  const { store, values } = setup({ token: 'legacy-token', username: 'employee' })
  logoutFails = true
  try {
    await assert.rejects(store.logout())
    assert.equal(store.token, 'legacy-token')
    assert.equal(values.get('token'), 'legacy-token')
  } finally { logoutFails = false }
})
test('shop and password endpoints keep scalar status and the original password field names', async () => {
  const { api } = setup({ token: 'legacy-token' })
  assert.equal((await api.getStatus()).data.data, 0)
  await api.setStatus(0)
  assert.deepEqual(requests.at(-1), { method: 'PUT', url: '/api/shop/0', body: '0', token: 'legacy-token' })
  await api.editPassword({ oldPassword: 'abc123', newPassword: 'def456' })
  assert.equal(requests.at(-1)?.url, '/api/employee/editPassword')
  assert.deepEqual(JSON.parse(requests.at(-1)?.body ?? ''), { oldPassword: 'abc123', newPassword: 'def456' })
})

test('password change clears revoked credentials and user state after confirmed success', async () => {
  const { store, values } = setup({ token: 'old-session-token', username: 'employee', user_info: JSON.stringify(user), sidebar_status: 'closed' })
  await store.changePassword({ oldPassword: 'abc123', newPassword: 'def456' })
  assert.equal(requests.at(-1)?.url, '/api/employee/editPassword')
  assert.deepEqual(JSON.parse(requests.at(-1)?.body ?? ''), { oldPassword: 'abc123', newPassword: 'def456' })
  assert.equal(store.token, '')
  assert.equal(store.username, '')
  assert.deepEqual(store.userInfo, {})
  for (const key of ['token', 'username', 'user_info', 'userInfo']) assert.equal(values.has(key), false)
  assert.equal(values.get('sidebar_status'), 'closed')
})

test('rejected password change preserves the still-valid session for retry', async () => {
  const { store, values } = setup({ token: 'old-session-token', username: 'employee' })
  passwordFails = true
  try {
    await assert.rejects(store.changePassword({ oldPassword: 'wrong123', newPassword: 'def456' }), /原密码错误/)
    assert.equal(store.token, 'old-session-token')
    assert.equal(values.get('token'), 'old-session-token')
  } finally { passwordFails = false }
})

test('business failure to revoke logout does not report a cleared session', async () => {
  const { store, values } = setup({ token: 'old-session-token', username: 'employee' })
  logoutBusinessFails = true
  try {
    await assert.rejects(store.logout(), /会话撤销失败/)
    assert.equal(store.token, 'old-session-token')
    assert.equal(values.get('token'), 'old-session-token')
  } finally { logoutBusinessFails = false }
})
