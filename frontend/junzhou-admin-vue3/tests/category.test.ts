import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createServer } from 'node:http'
import type { AddressInfo } from 'node:net'
import { createHttpClient } from '../src/utils/http.ts'
import { createCategoryApi } from '../src/api/category.ts'
import type { CategoryRecord } from '../src/api/category.ts'
import { categoryCreatePayload, categoryEditPayload, categoryFormFromRow, categoryResultMessage, categoryTypeLabel, createCategoryForm, nextCategoryStatus, validateCategory } from '../src/utils/category.ts'

const row: CategoryRecord = { id: 7, name: '招牌鲜鱼', type: 1, sort: 0, status: 1 }
test('category dialog defaults reset both original fields and editing copies row sort without changing its type', () => {
  assert.deepEqual(createCategoryForm(), { name: '', sort: '' })
  assert.deepEqual(categoryFormFromRow(row), { name: '招牌鲜鱼', sort: 0 })
  const form = categoryFormFromRow(row); form.name = '改名'
  assert.equal(row.name, '招牌鲜鱼')
})
test('category name keeps Chinese/ASCII letters and minimum two, with maxlength20 left to the original input', () => {
  for (const name of ['鲜鱼', 'AB', '鲜鱼Fish', 'a'.repeat(20), 'a'.repeat(21)]) assert.equal(validateCategory({ name, sort: 0 }).name, undefined)
  for (const name of ['', '鱼', '鱼1', ' 鲜鱼', '鱼-汤', 'café', '鱼🍲']) assert.ok(validateCategory({ name, sort: 0 }).name)
  assert.equal(validateCategory({ name: '', sort: 0 }, '新增套餐分类').name, '新增套餐分类不能为空')
})
test('category sort keeps 0-99 digit strings, zero and leading zeros, without trimming or numeric coercion', () => {
  for (const sort of [0, 99, '0', '00', '0099']) assert.equal(validateCategory({ name: '鲜鱼', sort }).sort, undefined)
  for (const sort of ['', -1, 100, '100', '1.5', ' 1', '1 ', 'abc']) assert.ok(validateCategory({ name: '鲜鱼', sort }).sort)
})
test('create sends name/type/sort while edit sends id/name/sort and does not introduce type/status/UI metadata', () => {
  const form = { name: '鲜鱼', sort: '00', title: 'extra', status: 0 }
  assert.deepEqual(categoryCreatePayload(form, '1'), { name: '鲜鱼', type: '1', sort: '00' })
  assert.deepEqual(categoryCreatePayload(form, '2'), { name: '鲜鱼', type: '2', sort: '00' })
  assert.deepEqual(categoryEditPayload(form, 7), { id: 7, name: '鲜鱼', sort: '00' })
})
test('category labels and enable/disable handle both original numeric and string wire values', () => {
  assert.equal(categoryTypeLabel(1), '菜品分类'); assert.equal(categoryTypeLabel('1'), '菜品分类')
  assert.equal(categoryTypeLabel(2), '套餐分类'); assert.equal(categoryTypeLabel('2'), '套餐分类')
  for (const status of [0, '0']) assert.equal(nextCategoryStatus(status), 1)
  for (const status of [1, '1']) assert.equal(nextCategoryStatus(status), 0)
})
test('category save errors preserve original desc-before-msg preference and useful fallback', () => {
  assert.equal(categoryResultMessage({ desc: '分类已存在', msg: '请求失败' }, '失败'), '分类已存在')
  assert.equal(categoryResultMessage({ msg: '分类有关联菜品' }, '失败'), '分类有关联菜品')
  assert.equal(categoryResultMessage({}, '失败'), '失败')
})

const requests: { method: string; url: string; body: string; token?: string }[] = []
let failNext = false
const server = createServer(async (req, res) => {
  let body = ''; for await (const chunk of req) body += chunk.toString()
  requests.push({ method: req.method!, url: req.url!, body, token: req.headers.token as string })
  res.setHeader('Content-Type', 'application/json')
  if (failNext) { failNext = false; res.statusCode = 500; res.end('{"code":0,"msg":"暂时不可用","data":null}'); return }
  res.end(JSON.stringify({ code: 1, data: req.url?.startsWith('/admin/category/page') ? { records: [row], total: '1' } : null }))
})
let api: ReturnType<typeof createCategoryApi>
before(async () => {
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  api = createCategoryApi(createHttpClient({ baseURL: `http://127.0.0.1:${(server.address() as AddressInfo).port}/admin`, getToken: () => 'category-token' }))
})
after(async () => { server.closeAllConnections(); await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())) })
test('category page keeps raw filter values, numeric type, omitted empty filters and the full Axios response', async () => {
  const result = await api.getPage({ page: 3, pageSize: 20, name: ' 鱼&汤 ', type: 2 })
  assert.equal(requests.at(-1)!.url, '/admin/category/page?page=3&pageSize=20&name=%20%E9%B1%BC%26%E6%B1%A4%20&type=2')
  assert.equal(requests.at(-1)!.token, 'category-token'); assert.equal(result.status, 200); assert.equal(result.data.data.total, '1')
  await api.getPage({ page: 1, pageSize: 10, name: undefined, type: undefined })
  assert.equal(requests.at(-1)!.url, '/admin/category/page?page=1&pageSize=10')
})
test('category POST and PUT preserve the exact original payload fields and scalar types', async () => {
  await api.add({ name: '鱼汤', type: '2', sort: '00' })
  assert.deepEqual(requests.at(-1), { method: 'POST', url: '/admin/category', body: '{"name":"鱼汤","type":"2","sort":"00"}', token: 'category-token' })
  await api.edit({ id: 7, name: '鲜鱼', sort: 0 })
  assert.deepEqual(requests.at(-1), { method: 'PUT', url: '/admin/category', body: '{"id":7,"name":"鲜鱼","sort":0}', token: 'category-token' })
})
test('category delete and status requests keep query.id and have no request body', async () => {
  await api.remove(7)
  assert.deepEqual(requests.at(-1), { method: 'DELETE', url: '/admin/category?id=7', body: '', token: 'category-token' })
  await api.setStatus('8', 1)
  assert.deepEqual(requests.at(-1), { method: 'POST', url: '/admin/category/status/1?id=8', body: '', token: 'category-token' })
})
test('rapid repeated category queries share an in-flight read without persisting completed results', async () => {
  const start = requests.length; const query = { page: 1, pageSize: 10 }
  const results = await Promise.all([api.getPage(query), api.getPage(query)])
  assert.equal(requests.length - start, 1); assert.equal(results[0], results[1])
  await api.getPage(query); assert.equal(requests.length - start, 2)
})
test('a failed category read releases its shared request so retry can reach the server', async () => {
  failNext = true
  await assert.rejects(api.getPage({ page: 1, pageSize: 10 }))
  const result = await api.getPage({ page: 1, pageSize: 10 }); assert.equal(result.data.code, 1)
})
