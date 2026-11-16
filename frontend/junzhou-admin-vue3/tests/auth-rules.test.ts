import assert from 'node:assert/strict'
import { test } from 'node:test'
import { validateLogin, validatePasswordChange } from '../src/utils/auth-validation.ts'
import { createAuthGuard } from '../src/router/guard.ts'

test('login retains the original minimum length without restricting employee names or password characters', () => {
  assert.deepEqual(validateLogin({ username: '任意员工', password: '空 格密码!!' }), {})
  const errors = validateLogin({ username: '', password: '12345' })
  assert.ok(errors.username)
  assert.ok(errors.password)
})
test('change-password accepts provisioned old passwords while keeping 6-20 alphanumeric new passwords', () => {
  assert.deepEqual(validatePasswordChange({ oldPassword: 'abc123', newPassword: 'DEF456', affirmPassword: 'DEF456' }), {})
  const errors = validatePasswordChange({ oldPassword: '短密码', newPassword: 'x'.repeat(21), affirmPassword: 'different' })
  assert.equal(errors.oldPassword, undefined)
  assert.ok(errors.newPassword)
  assert.ok(errors.affirmPassword)
})

test('old password accepts symbols and long bootstrap credentials, and remains required', () => {
  for (const oldPassword of ['Safe-password-123!', 'a'.repeat(128), '!']) {
    assert.deepEqual(validatePasswordChange({ oldPassword, newPassword: 'New123456', affirmPassword: 'New123456' }), {})
  }
  assert.ok(validatePasswordChange({ oldPassword: '', newPassword: 'New123456', affirmPassword: 'New123456' }).oldPassword)
})
test('unauthenticated users reach only explicitly public routes', () => {
  const guard = createAuthGuard(() => undefined)
  assert.equal(guard({ meta: {} }), '/login')
  assert.equal(guard({ meta: { notNeedAuth: true } }), true)
})
test('a token permits access without new roles enforcement, including the login route', () => {
  const guard = createAuthGuard(() => 'legacy-token')
  assert.equal(guard({ meta: {} }), true)
  assert.equal(guard({ meta: { notNeedAuth: true } }), true)
})
