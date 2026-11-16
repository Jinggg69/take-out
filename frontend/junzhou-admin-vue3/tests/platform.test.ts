import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createAuthGuard } from '../src/router/guard.ts'
import { validatePlatformForm } from '../src/views/platform/form.ts'
import { createPlatformList } from '../src/views/platform/list-state.ts'
import { createHttpClient } from '../src/utils/http.ts'
import { createPlatformApi, createPlatformAuthApi } from '../src/api/platform.ts'
import { createAuthApi } from '../src/api/auth.ts'
import { createUserStore } from '../src/stores/user.ts'
import { createSessionStorage } from '../src/utils/cookies.ts'
import { createPinia } from 'pinia'
import { validatePasswordChange } from '../src/utils/auth-validation.ts'

test('platform client sends shared token without merchant store header and uses exact CRUD endpoints', async () => {
  const calls: { url?: string; method?: string; data: unknown; token: unknown; store: unknown }[] = []
  const client = createHttpClient({ baseURL: '/platform', getToken: () => 'platform-token' })
  client.defaults.adapter = async config => {
    calls.push({ url: config.url, method: config.method, data: config.data, token: config.headers.get('token'), store: config.headers.get('X-Store-Id') })
    return { data: { code: 1, data: null }, status: 200, statusText: 'OK', headers: {}, config }
  }
  const api = createPlatformApi(client, 'tenants')
  await api.getPage({ page: 1, pageSize: 10, keyword: '商户' })
  await api.create({ code: 't', name: '商户' }); await api.update('9007199254740993', { code: 't', name: '更新' })
  await api.setStatus('9007199254740993', 0); await api.remove('9007199254740993')
  assert.equal(calls[0]?.url, '/tenants?page=1&pageSize=10&keyword=%E5%95%86%E6%88%B7')
  assert.equal(calls[2]?.url, '/tenants/9007199254740993')
  assert.equal(calls[3]?.data, '{"status":0}')
  assert.equal(calls[4]?.method, 'delete')
  assert.ok(calls.every(call => call.token === 'platform-token' && call.store === undefined))
})

test('platform reload dispatches password/logout to platform and preserves legacy validation', async () => {
  const values = new Map([['token', 'platform-token'], ['user_info', '{"accountType":"PLATFORM"}']])
  const storage = createSessionStorage({ get: key => values.get(key), set: (key, value) => values.set(key, value), remove: key => values.delete(key) })
  const calls: string[] = []
  const client = createHttpClient({ baseURL: '/platform', getToken: storage.getToken })
  client.defaults.adapter = async config => {
    calls.push(config.url!); return { data: { code: 1, data: null }, status: 200, statusText: 'OK', headers: {}, config }
  }
  const make = () => createUserStore(createAuthApi(client), storage, createPlatformAuthApi(client))(createPinia())
  await make().changePassword({ oldPassword: '!x', newPassword: 'new password!' })
  assert.equal(calls[0], '/auth/password'); assert.equal(values.has('token'), false)
  values.set('token', 'platform-token'); values.set('user_info', '{"accountType":"PLATFORM"}')
  await make().logout(); assert.equal(calls[1], '/auth/logout')
  assert.deepEqual(validatePasswordChange({ oldPassword: '!x', newPassword: 'new password!', affirmPassword: 'new password!' }, true), {})
  assert.ok(validatePasswordChange({ oldPassword: 'abc123', newPassword: 'short', affirmPassword: 'short' }, true).newPassword)
})

test('401 expires current platform session; stale 401 cannot revoke a newer session', async () => {
  let token = 'first', revoked = 0
  const client = createHttpClient({ baseURL: '/platform', getToken: () => token, onUnauthorized: () => revoked++ })
  let reply: (() => void) | undefined
  client.defaults.adapter = config => new Promise(resolve => { reply = () => resolve({ data: { status: 401 }, status: 200, statusText: 'OK', headers: {}, config }) })
  const old = client.get('/auth/me'); await new Promise(resolve => setImmediate(resolve)); token = 'second'; reply!()
  await assert.rejects(old, /会话已切换/); assert.equal(revoked, 0)
  const current = client.get('/auth/me'); await new Promise(resolve => setImmediate(resolve)); reply!(); await current
  assert.equal(revoked, 1)
})

test('platform reload and both cross-shell paths route by stored identity', () => {
  const platform = createAuthGuard(() => 'token', () => 'PLATFORM')
  const employee = createAuthGuard(() => 'token', () => 'EMPLOYEE')
  assert.equal(platform({ meta: { accountType: 'EMPLOYEE' } }), '/platform/tenants')
  assert.equal(platform({ meta: { accountType: 'PLATFORM' } }), true)
  assert.equal(employee({ meta: { accountType: 'PLATFORM' } }), '/dashboard')
  assert.equal(createAuthGuard(() => undefined, () => 'PLATFORM')({ meta: { accountType: 'PLATFORM' } }), '/login')
})

test('tenant creation requires administrator and first store; edits do not reset credentials', () => {
  const form = { code: 'new', name: '新商户', adminUsername: 'shop_admin', adminName: '管理员', adminPassword: 'Asecure123', store: { code: 'first', name: '首店', address: '', latitude: null, longitude: null } }
  assert.deepEqual(validatePlatformForm(form, 'tenants', false), {})
  assert.ok(validatePlatformForm({ ...form, adminUsername: 'PLATFORM_admin', adminPassword: '', store: { ...form.store, name: '' } }, 'tenants', false).adminUsername)
  assert.ok(validatePlatformForm({ ...form, store: { ...form.store, latitude: 91 } }, 'tenants', false)['store.latitude'])
  assert.deepEqual(validatePlatformForm({ code: 'new', name: '改名' }, 'tenants', true), {})
  assert.ok(validatePlatformForm({ code: 's', name: '门店' }, 'stores', false).tenantId)
})

test('platform lists reject business errors, keep latest response and ignore disposed responses', async () => {
  const pending: ((value: unknown) => void)[] = []
  const list = createPlatformList({ getPage: () => new Promise(resolve => pending.push(resolve)) } as never)
  const old = list.load(); const latest = list.load()
  pending[1]!({ data: { code: 1, data: { records: [{ id: '2' }], total: 1 } } }); await latest
  pending[0]!({ data: { code: 1, data: { records: [{ id: '1' }], total: 1 } } }); await old
  assert.equal(list.state.records[0]?.id, '2')
  const fail = list.load(); pending[2]!({ data: { code: 0, msg: '会话失效' } }); await fail
  assert.equal(list.state.error, '会话失效')
  const disposed = list.load(); list.dispose(); pending[3]!({ data: { code: 1, data: { records: [], total: 0 } } }); await disposed
  assert.equal(list.state.records[0]?.id, '2')
})
