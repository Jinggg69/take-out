import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createServer } from 'node:http'
import type { AddressInfo } from 'node:net'
import { createHttpClient } from '../src/utils/http.ts'
import { createSetmealApi } from '../src/api/setmeal.ts'
import { createSetmealForm, setmealFormFromDetail, setmealPayload, validateSetmeal, toggleSetmealDish, searchSetmealDishes } from '../src/utils/setmeal.ts'

test('setmeal form preserves yuan amounts and exact association fields', () => {
  const form = { ...createSetmealForm(), name: '鲜鱼套餐', categoryId: 3, price: '28.50', image: '/fish.png', dishes: [{ dishId: 8, name: '鲜鱼', price: '20.50', copies: 2 }] }
  assert.deepEqual(validateSetmeal(form), {})
  assert.equal(setmealPayload(form, false).price, '28.50')
  assert.equal(setmealPayload(form, false).status, 0)
  assert.deepEqual(setmealPayload(form, false).setmealDishes, form.dishes)
  for (const price of ['0', '01', '1.001', '1000000', ' 1']) assert.ok(validateSetmeal({ ...form, price }).price)
  for (const name of ['鱼', '鲜鱼!', 'a'.repeat(21)]) assert.ok(validateSetmeal({ ...form, name }).name)
  assert.ok(validateSetmeal({ ...form, categoryId: '' }).categoryId)
  assert.ok(validateSetmeal({ ...form, image: '' }).image)
  assert.ok(validateSetmeal({ ...form, dishes: [] }).dishes)
  assert.ok(validateSetmeal({ ...form, dishes: [{ ...form.dishes[0]!, copies: 100 }] }).dishes)
})
test('setmeal edit and selection retain quantities, identify by dishId and never mutate detail', () => {
  const detail = { id: 9, name: '鲜鱼套餐', price: 28.5, categoryId: 3, image: '/fish.png', status: '1', updateTime: 'before', setmealDishes: [{ id: 2, dishId: 8, name: '鲜鱼', price: 20, copies: 3 }] }
  const form = setmealFormFromDetail(detail)
  form.dishes[0]!.copies = 4
  assert.equal(detail.setmealDishes[0]!.copies, 3)
  assert.deepEqual(setmealPayload(form, true).setmealDishes, [{ dishId: 8, name: '鲜鱼', price: 20, copies: 4 }])
  assert.equal(setmealPayload(form, true).status, 1)
  assert.equal(setmealPayload(form, true).updateTime, undefined)
  const setmeal = { id: 10, name: '鲜鱼', price: 12, categoryId: 1, image: '', status: 1 }
  const chosen = toggleSetmealDish(form.dishes, setmeal, true)
  assert.equal(chosen.length, 2); assert.equal(chosen[0]!.copies, 1); assert.equal(chosen[1]!.copies, 4)
  assert.equal(toggleSetmealDish(chosen, setmeal, false).length, 1)
})

const requests: { method: string; url: string; body: string; token?: string; contentType?: string }[] = []
let failNext = false
const server = createServer(async (req, res) => {
  let body = ''; for await (const chunk of req) body += chunk.toString()
  requests.push({ method: req.method!, url: req.url!, body, token: req.headers.token as string, contentType: req.headers['content-type'] })
  res.setHeader('Content-Type', 'application/json')
  if (failNext) { failNext = false; res.statusCode = 500; res.end('{"code":0,"msg":"上传失败","data":null}'); return }
  res.end(JSON.stringify({ code: 1, data: req.url?.startsWith('/admin/setmeal/page') ? { records: [], total: '0' } : req.url === '/admin/common/upload' ? '/uploaded.png' : null }))
})
let api: ReturnType<typeof createSetmealApi>
before(async () => {
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  api = createSetmealApi(createHttpClient({ baseURL: `http://127.0.0.1:${(server.address() as AddressInfo).port}/admin`, getToken: () => 'setmeal-token' }))
})
after(async () => { server.closeAllConnections(); await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())) })

test('setmeal query keeps raw name, category ID and explicit zero or empty status', async () => {
  await api.getPage({ page: 3, pageSize: 20, name: ' 鱼&汤 ', categoryId: 5, status: 0 })
  assert.equal(requests.at(-1)!.url, '/admin/setmeal/page?page=3&pageSize=20&name=%20%E9%B1%BC%26%E6%B1%A4%20&categoryId=5&status=0')
  assert.equal(requests.at(-1)!.token, 'setmeal-token')
  await api.getPage({ page: 1, pageSize: 10, status: '' })
  assert.equal(requests.at(-1)!.url, '/admin/setmeal/page?page=1&pageSize=10&status=')
})

test('setmeal mutations retain exact endpoints, query.ids vs query.id and empty request bodies', async () => {
  await api.remove('9,10')
  assert.equal(requests.at(-1)!.url, '/admin/setmeal?ids=9,10'); assert.equal(requests.at(-1)!.body, '')
  await api.setStatus(9, 0)
  assert.equal(requests.at(-1)!.url, '/admin/setmeal/status/0?id=9'); assert.equal(requests.at(-1)!.body, '')
  const payload = setmealPayload({ ...createSetmealForm(), name: '鲜鱼', price: '1.20', categoryId: 3, image: '/fish.png' }, false)
  await api.add(payload); assert.equal(requests.at(-1)!.method, 'POST'); assert.deepEqual(JSON.parse(requests.at(-1)!.body), payload)
  await api.edit({ ...payload, id: 9 }); assert.equal(requests.at(-1)!.method, 'PUT')
  await api.getById(9); assert.equal(requests.at(-1)!.url, '/admin/setmeal/9')
  await api.getCategories(); assert.equal(requests.at(-1)!.url, '/admin/category/list?type=2')
})

test('upload uses multipart file field, original name and Token, returns full response and permits retry', async () => {
  const file = new File(['image-content'], 'fish.png', { type: 'image/png' })
  const { data } = await api.upload(file)
  const req = requests.at(-1)!
  assert.equal(req.url, '/admin/common/upload'); assert.equal(req.token, 'setmeal-token')
  assert.match(req.contentType!, /^multipart\/form-data; boundary=/)
  assert.match(req.body, /name="file"; filename="fish.png"/); assert.match(req.body, /image-content/)
  assert.equal(data.data, '/uploaded.png')
  failNext = true; await assert.rejects(api.upload(file)); assert.equal((await api.upload(file)).data.code, 1)
})

test('setmeal in-flight page and detail reads share results but completed data is never cached', async () => {
  const n = requests.length; const query = { page: 1, pageSize: 10, status: '' as const }
  const [a, b] = await Promise.all([api.getPage(query), api.getPage(query)])
  assert.equal(a, b); assert.equal(requests.length - n, 1)
  await api.getPage(query); assert.equal(requests.length - n, 2)
  const start = requests.length; await Promise.all([api.getById(9), api.getById(9)])
  assert.equal(requests.length - start, 1)
})

test('form categories retain original pagination and picker retains both category and name parameters', async () => {
  await api.getCategories(true)
  assert.equal(requests.at(-1)!.url, '/admin/category/list?type=2&page=1&pageSize=1000')
  await api.getDishCategories()
  assert.equal(requests.at(-1)!.url, '/admin/category/list?type=1')
  await api.getDishes({ categoryId: 3 })
  assert.equal(requests.at(-1)!.url, '/admin/dish/list?categoryId=3')
  await api.getDishes({ name: '鲜鱼' })
  assert.equal(requests.at(-1)!.url, '/admin/dish/list?name=%E9%B2%9C%E9%B1%BC')
})
test('name search falls back to category endpoints when backend requires categoryId, without silently ignoring a failed category', async () => {
  const calls: object[] = []
  const record = { id: 8, name: '鲜鱼汤', price: 12, categoryId: 3, image: '', status: 1 }
  let failCategory = false
  const fake = { getDishes: async (params: { name?: string; categoryId?: string | number }) => {
    calls.push(params)
    if (params.name || failCategory) throw new Error('categoryId required')
    return { data: { code: 1, data: [record, { ...record, id: 9, name: '青菜' }] } } as Awaited<ReturnType<typeof api.getDishes>>
  } }
  assert.deepEqual(await searchSetmealDishes(fake, '鲜鱼', [{ id: 3, name: '热菜' }]), [record])
  assert.deepEqual(calls, [{ name: '鲜鱼' }, { categoryId: 3 }])
  failCategory = true
  await assert.rejects(searchSetmealDishes(fake, '鲜鱼', [{ id: 3, name: '热菜' }]))
})
