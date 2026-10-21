import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createServer } from 'node:http'
import type { AddressInfo } from 'node:net'
import { createHttpClient } from '../src/utils/http.ts'
import { createEmployeeApi } from '../src/api/employee.ts'
import type { EmployeeForm } from '../src/api/employee.ts'
import { canManageEmployee, createEmployeeForm, employeeFormFromDetail, employeePayload, nextEmployeeStatus, validateEmployee } from '../src/utils/employee.ts'

const valid: EmployeeForm = { username: 'chef123', name: '张师傅', phone: '13800138000', sex: '男', idNumber: '42038119900101123X' }
test('employee defaults and save-and-continue reset retain exactly the original five fields', () => {
  assert.deepEqual(createEmployeeForm(), { username: '', name: '', phone: '', sex: '男', idNumber: '' })
  const first = createEmployeeForm(); first.username = 'changed'
  assert.equal(createEmployeeForm().username, '')
})
test('employee username retains lowercase alphanumeric 3-20, without trimming or new name restrictions', () => {
  assert.deepEqual(validateEmployee(valid), {})
  for (const username of ['ab', 'A123', 'a_b', ' chef123 ', 'a'.repeat(21)]) assert.ok(validateEmployee({ ...valid, username }).username)
  for (const username of ['abc', '123', 'a'.repeat(20)]) assert.equal(validateEmployee({ ...valid, username }).username, undefined)
  assert.ok(validateEmployee({ ...valid, name: '' }).name)
  assert.equal(validateEmployee({ ...valid, name: '   ' }).name, undefined)
})
test('employee phone retains the original 13-18 range and ID accepts legacy 15 digits or 18 with X/x', () => {
  for (const phone of ['13000138000', '18800138000']) assert.equal(validateEmployee({ ...valid, phone }).phone, undefined)
  for (const phone of ['', '19800138000', '1380013800', '13800138000 ']) assert.ok(validateEmployee({ ...valid, phone }).phone)
  for (const idNumber of ['123456789012345', '123456789012345678', '12345678901234567X', '12345678901234567x']) assert.equal(validateEmployee({ ...valid, idNumber }).idNumber, undefined)
  for (const idNumber of ['', '1234567890123456', '12345678901234567Y']) assert.ok(validateEmployee({ ...valid, idNumber }).idNumber)
})
test('detail mapping and edit payload preserve returned fields, unchanged text and the original sex strings', () => {
  const detail = { ...valid, id: 7, sex: '0', status: 1, password: '****', updateTime: '2026-10-21 12:00:00' }
  const form = employeeFormFromDetail(detail)
  assert.equal(form.sex, '女')
  assert.equal(detail.sex, '0')
  assert.deepEqual(employeePayload(form), detail)
  assert.equal(employeePayload({ ...valid, name: ' 张师傅 ' }).name, ' 张师傅 ')
  assert.equal(employeePayload(valid).sex, '1')
})
test('admin row protection remains username-based without introducing roles, and both wire status types toggle', () => {
  assert.equal(canManageEmployee({ username: 'admin' }), false)
  assert.equal(canManageEmployee({ username: 'chef123' }), true)
  assert.equal(canManageEmployee({ username: 'Admin' }), true)
  for (const status of [0, '0']) assert.equal(nextEmployeeStatus(status), 1)
  for (const status of [1, '1']) assert.equal(nextEmployeeStatus(status), 0)
})

const requests: { method: string; url: string; body: string; token?: string }[] = []
const server = createServer(async (req, res) => {
  let body = ''; for await (const chunk of req) body += chunk.toString()
  requests.push({ method: req.method!, url: req.url!, body, token: req.headers.token as string })
  res.setHeader('Content-Type', 'application/json')
  res.end(JSON.stringify({ code: 1, data: req.url?.startsWith('/admin/employee/page') ? { records: [], total: 0 } : null }))
})
let api: ReturnType<typeof createEmployeeApi>
before(async () => {
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  api = createEmployeeApi(createHttpClient({ baseURL: `http://127.0.0.1:${(server.address() as AddressInfo).port}/admin`, getToken: () => 'employee-test-token' }))
})
after(async () => { server.closeAllConnections(); await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())) })
test('employee page request keeps page/pageSize/name encoding and auth header without unwrapping the response', async () => {
  const result = await api.getList({ page: 3, pageSize: 20, name: ' 张&李 ' })
  const sent = requests.at(-1)!
  assert.equal(sent.method, 'GET'); assert.equal(sent.token, 'employee-test-token')
  assert.equal(sent.url, '/admin/employee/page?page=3&pageSize=20&name=%20%E5%BC%A0%26%E6%9D%8E%20')
  assert.equal(result.status, 200); assert.deepEqual(result.data.data, { records: [], total: 0 })
  await api.getList({ page: 1, pageSize: 10, name: undefined })
  assert.equal(requests.at(-1)!.url, '/admin/employee/page?page=1&pageSize=10')
})
test('employee detail/add/edit retain method, URL and original spread payload without adding role/password fields', async () => {
  await api.getById('7'); assert.equal(requests.at(-1)!.url, '/admin/employee/7')
  const data = { ...valid, sex: '1' as const }
  await api.add(data)
  assert.deepEqual(requests.at(-1), { method: 'POST', url: '/admin/employee', body: JSON.stringify(data), token: 'employee-test-token' })
  await api.edit({ ...data, id: 7, sex: '0' })
  assert.equal(requests.at(-1)!.method, 'PUT'); assert.deepEqual(JSON.parse(requests.at(-1)!.body), { ...data, id: 7, sex: '0' })
})
test('employee enable/disable keeps POST path status and query id with an empty body', async () => {
  await api.setStatus(7, 0)
  assert.deepEqual(requests.at(-1), { method: 'POST', url: '/admin/employee/status/0?id=7', body: '', token: 'employee-test-token' })
  await api.setStatus('8', 1)
  assert.equal(requests.at(-1)!.url, '/admin/employee/status/1?id=8')
})
test('A to B to A detail navigation shares the original pending HTTP read instead of canceling the latest route', async () => {
  const before = requests.length
  const results = await Promise.allSettled([api.getById(7), api.getById(8), api.getById(7)])
  assert.deepEqual(results.map(result => result.status), ['fulfilled', 'fulfilled', 'fulfilled'])
  assert.equal(requests.slice(before).length, 2)
  if (results[0]!.status === 'fulfilled' && results[2]!.status === 'fulfilled') assert.equal(results[0]!.value, results[2]!.value)
})
