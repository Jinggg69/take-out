import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readClientEnvironment } from '../src/config/environment.ts'
import { createDevProxy } from '../build/proxy.ts'

test('production string false does not grant delete permissions', () => {
  const result = readClientEnvironment({
    VITE_BASE_API: '/api', VITE_SOCKET_URL: '/ws/', VITE_DELETE_PERMISSIONS: 'false',
  })
  assert.equal(result.deletePermissions, false)
  assert.equal(result.socketUrl, '/ws/')
})

test('development and UAT string true preserve the existing configuration flag', () => {
  assert.equal(readClientEnvironment({ VITE_DELETE_PERMISSIONS: 'true' }).deletePermissions, true)
})

test('an omitted permission flag stays false rather than granting access', () => {
  assert.equal(readClientEnvironment({}).deletePermissions, false)
  assert.equal(readClientEnvironment({}).baseApi, '/api')
})

test('API proxy preserves admin prefix, encoded values, and the query string', () => {
  const proxy = createDevProxy('http://localhost:8080/admin/')
  assert.equal(proxy['/api'].target, 'http://localhost:8080')
  assert.equal(proxy['/api'].rewrite?.('/api/dish/page?name=%E9%B1%BC&status=0'),
    '/admin/dish/page?name=%E9%B1%BC&status=0')
  assert.equal(proxy['/api'].rewrite?.('/api/employee/login'), '/admin/employee/login')
})

test('WebSocket proxy keeps ws outside the admin API namespace', () => {
  const proxy = createDevProxy('http://localhost:8080/admin')
  assert.equal(proxy['/ws'].target, 'http://localhost:8080')
  assert.equal(proxy['/ws'].ws, true)
  assert.equal(proxy['/ws'].rewrite, undefined)
})

test('an invalid proxy target fails early with a useful error', () => {
  assert.throws(() => createDevProxy('not-a-url'), /API_PROXY_TARGET/)
  assert.throws(() => createDevProxy('file:///admin'), /API_PROXY_TARGET/)
})
