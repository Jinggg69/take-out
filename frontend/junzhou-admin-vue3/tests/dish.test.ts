import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createServer } from 'node:http'
import type { AddressInfo } from 'node:net'
import { createHttpClient } from '../src/utils/http.ts'
import { createDishApi } from '../src/api/dish.ts'
import { createDishForm, dishFormFromDetail, dishPayload, validateDish, imageUploadError, nextDishStatus, formatDishPrice, flavorOptions } from '../src/utils/dish.ts'

test('dish validation retains original name and yuan price boundaries without trim or cents conversion', () => {
  const valid = { ...createDishForm(), name: '鲜鱼01', categoryId: 3, price: '28.50', image: '/fish.png' }
  assert.deepEqual(validateDish(valid), {})
  for (const name of ['鱼', 'a'.repeat(21), ' 鲜鱼', '鲜鱼汤!', '鱼🍲']) assert.ok(validateDish({ ...valid, name }).name)
  for (const price of ['', '0', '0.00', '-1', '01', '1.', '.5', '1.001', '1000000', ' 1']) assert.ok(validateDish({ ...valid, price }).price)
  for (const price of ['0.01', '1', '999999.99']) assert.equal(validateDish({ ...valid, price }).price, undefined)
  assert.ok(validateDish({ ...valid, categoryId: '' }).categoryId)
  assert.ok(validateDish({ ...valid, image: '' }).image)
})

test('add payload preserves original fields, string price, default disabled status and JSON flavor values', () => {
  const form = { ...createDishForm(), name: '鲜鱼', categoryId: 3, price: '28.50', image: '/fish.png', description: '现做', flavors: [{ name: '辣度', value: ['微辣', '中辣'] }] }
  assert.deepEqual(dishPayload(form, false), { name: '鲜鱼', price: '28.50', code: '', image: '/fish.png', description: '现做', status: 0, categoryId: 3, flavors: [{ name: '辣度', value: '["微辣","中辣"]' }] })
  assert.deepEqual(createDishForm().flavors, [])
})

test('edit round-trip retains detail metadata and flavor IDs, removes only original excluded timestamps', () => {
  const detail = { id: 9, name: '鲜鱼', price: 28.5, categoryId: 3, image: '/fish.png', description: '现做', status: '1', updateTime: 'yesterday', createTime: 'before', categoryName: '鱼类', flavors: [{ id: 11, dishId: 9, name: '辣度', value: '["微辣"]' }] }
  const form = dishFormFromDetail(detail)
  assert.equal(form.price, '28.5'); assert.equal(form.status, true)
  form.flavors[0]!.value.push('中辣')
  assert.equal(detail.flavors[0]!.value, '["微辣"]')
  assert.deepEqual(dishPayload(form, true), { id: 9, name: '鲜鱼', price: '28.5', categoryId: 3, image: '/fish.png', description: '现做', status: 1, categoryName: '鱼类', flavors: [{ id: 11, dishId: 9, name: '辣度', value: '["微辣","中辣"]' }] })
  assert.equal(dishPayload(dishFormFromDetail({ ...detail, status: '0' }), true).status, 0)
})

test('malformed flavor data rejects the detail instead of silently overwriting saved flavors', () => {
  const base = { id: 9, name: '鲜鱼', price: 28, categoryId: 3, image: '/fish.png', status: 0 }
  assert.deepEqual(dishFormFromDetail(base).flavors, [])
  for (const value of ['bad', '{}', '[1]']) assert.throws(() => dishFormFromDetail({ ...base, flavors: [{ name: '辣度', value }] }))
})

test('flavor presets are copied per use, omit selected names and do not introduce custom flavor operations', () => {
  const options = flavorOptions([{ name: '辣度', value: [] }])
  assert.deepEqual(options.map(f => f.name), ['甜味', '温度', '忌口'])
  assert.deepEqual(options[0]!.value, ['无糖', '少糖', '半糖', '多糖', '全糖'])
  options[0]!.value.pop()
  assert.equal(flavorOptions([])[0]!.value.length, 5)
})

test('image upload preserves original strictly-less-than 2MiB limit and numeric/string status is consistent', () => {
  assert.equal(imageUploadError({ size: 2 * 1024 * 1024 - 1 }), '')
  assert.ok(imageUploadError({ size: 2 * 1024 * 1024 }))
  for (const status of [0, '0']) assert.equal(nextDishStatus(status), 1)
  for (const status of [1, '1']) assert.equal(nextDishStatus(status), 0)
  assert.equal(formatDishPrice('28.50'), '28.50'); assert.equal(formatDishPrice(null), '—')
})

const requests: { method: string; url: string; body: string; token?: string; contentType?: string }[] = []
let failNext = false
const server = createServer(async (req, res) => {
  let body = ''; for await (const chunk of req) body += chunk.toString()
  requests.push({ method: req.method!, url: req.url!, body, token: req.headers.token as string, contentType: req.headers['content-type'] })
  res.setHeader('Content-Type', 'application/json')
  if (failNext) { failNext = false; res.statusCode = 500; res.end('{"code":0,"msg":"上传失败","data":null}'); return }
  res.end(JSON.stringify({ code: 1, data: req.url?.startsWith('/admin/dish/page') ? { records: [], total: '0' } : req.url === '/admin/common/upload' ? '/uploaded.png' : null }))
})
let api: ReturnType<typeof createDishApi>
before(async () => {
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  api = createDishApi(createHttpClient({ baseURL: `http://127.0.0.1:${(server.address() as AddressInfo).port}/admin`, getToken: () => 'dish-token' }))
})
after(async () => { server.closeAllConnections(); await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())) })

test('dish query keeps raw name, category ID and explicit zero or empty status', async () => {
  await api.getPage({ page: 3, pageSize: 20, name: ' 鱼&汤 ', categoryId: 5, status: 0 })
  assert.equal(requests.at(-1)!.url, '/admin/dish/page?page=3&pageSize=20&name=%20%E9%B1%BC%26%E6%B1%A4%20&categoryId=5&status=0')
  assert.equal(requests.at(-1)!.token, 'dish-token')
  await api.getPage({ page: 1, pageSize: 10, status: '' })
  assert.equal(requests.at(-1)!.url, '/admin/dish/page?page=1&pageSize=10&status=')
})

test('dish mutations retain exact endpoints, query.ids vs query.id and empty request bodies', async () => {
  await api.remove('9,10')
  assert.equal(requests.at(-1)!.url, '/admin/dish?ids=9,10'); assert.equal(requests.at(-1)!.body, '')
  await api.setStatus(9, 0)
  assert.equal(requests.at(-1)!.url, '/admin/dish/status/0?id=9'); assert.equal(requests.at(-1)!.body, '')
  const payload = dishPayload({ ...createDishForm(), name: '鲜鱼', price: '1.20', categoryId: 3, image: '/fish.png' }, false)
  await api.add(payload); assert.equal(requests.at(-1)!.method, 'POST'); assert.deepEqual(JSON.parse(requests.at(-1)!.body), payload)
  await api.edit({ ...payload, id: 9 }); assert.equal(requests.at(-1)!.method, 'PUT')
  await api.getById(9); assert.equal(requests.at(-1)!.url, '/admin/dish/9')
  await api.getCategories(); assert.equal(requests.at(-1)!.url, '/admin/category/list?type=1')
})

test('upload uses multipart file field, original name and Token, returns full response and permits retry', async () => {
  const file = new File(['image-content'], 'fish.png', { type: 'image/png' })
  const { data } = await api.upload(file)
  const req = requests.at(-1)!
  assert.equal(req.url, '/admin/common/upload'); assert.equal(req.token, 'dish-token')
  assert.match(req.contentType!, /^multipart\/form-data; boundary=/)
  assert.match(req.body, /name="file"; filename="fish.png"/); assert.match(req.body, /image-content/)
  assert.equal(data.data, '/uploaded.png')
  failNext = true; await assert.rejects(api.upload(file)); assert.equal((await api.upload(file)).data.code, 1)
})

test('dish in-flight page and detail reads share results but completed data is never cached', async () => {
  const n = requests.length; const query = { page: 1, pageSize: 10, status: '' as const }
  const [a, b] = await Promise.all([api.getPage(query), api.getPage(query)])
  assert.equal(a, b); assert.equal(requests.length - n, 1)
  await api.getPage(query); assert.equal(requests.length - n, 2)
  const start = requests.length; await Promise.all([api.getById(9), api.getById(9)])
  assert.equal(requests.length - start, 1)
})
