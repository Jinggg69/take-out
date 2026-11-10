import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AxiosHeaders } from 'axios'
import type { AxiosResponse } from 'axios'
import type { DishId, DishRecord, DishResult } from '../src/api/dish.ts'
import { createDishDetail } from '../src/views/dish/detail-state.ts'

function response(overrides: Partial<DishRecord> = {}): AxiosResponse<DishResult<DishRecord>> {
  return { data: { code: 1, data: { id: 7, name: '清蒸草鱼', categoryId: 3, categoryName: '招牌菜',
    price: '68.50', image: '/fish.png', description: '现点现做', status: 0, updateTime: '2026-11-10 14:26',
    flavors: [{ name: '辣度', value: '["不辣","微辣"]' }], ...overrides } },
  status: 200, statusText: 'OK', headers: new AxiosHeaders(), config: { headers: new AxiosHeaders() } }
}

test('dish detail loads full information and decodes saved flavor choices without altering the response', async () => {
  const ids: DishId[] = []
  const source = response()
  const detail = createDishDetail({ getById: async id => { ids.push(id); return source } })
  await detail.load('7')
  assert.deepEqual(ids, ['7'])
  assert.equal(detail.state.record?.name, '清蒸草鱼')
  assert.equal(detail.state.record?.price, '68.50')
  assert.equal(detail.state.record?.status, 0)
  assert.equal(detail.state.record?.description, '现点现做')
  assert.equal(detail.state.record?.categoryName, '招牌菜')
  assert.deepEqual(detail.state.flavors, [{ name: '辣度', value: ['不辣', '微辣'] }])
  assert.equal(source.data.data.flavors?.[0]?.value, '["不辣","微辣"]')
  assert.equal(detail.state.loading, false)
  assert.equal(detail.state.error, '')
})

test('switching dishes clears previous details and ignores earlier successes and failures', async () => {
  const tasks: { resolve: (value: ReturnType<typeof response>) => void; reject: (cause: Error) => void }[] = []
  const detail = createDishDetail({ getById: () => new Promise((resolve, reject) => tasks.push({ resolve, reject })) })
  const first = detail.load(7); tasks[0]!.resolve(response()); await first
  const second = detail.load(8)
  assert.equal(Boolean(detail.state.record), false)
  assert.deepEqual(detail.state.flavors, [])
  assert.equal(detail.state.loading, true)
  const third = detail.load(9)
  tasks[2]!.resolve(response({ id: 9, name: '新菜品' })); await third
  tasks[1]!.resolve(response({ id: 8, name: '旧菜品' })); await second
  assert.equal(detail.state.record?.name, '新菜品')
  const fourth = detail.load(10)
  const fifth = detail.load(11)
  tasks[4]!.resolve(response({ id: 11, name: '最新菜品' })); await fifth
  tasks[3]!.reject(new Error('旧请求失败')); await fourth
  assert.equal(detail.state.record?.name, '最新菜品')
  assert.equal(detail.state.error, '')
  assert.equal(detail.state.loading, false)
})

test('missing flavors and a valid empty flavor array show no flavor choices', async () => {
  for (const flavors of [undefined, null, []]) {
    const detail = createDishDetail({ getById: async () => response({ flavors }) })
    await detail.load(7)
    assert.ok(detail.state.record)
    assert.deepEqual(detail.state.flavors, [])
    assert.equal(detail.state.error, '')
  }
})

test('invalid detail or flavor data is recoverable and never displayed as valid empty content', async () => {
  const base = response().data.data
  const bodies: unknown[] = [null, {}, { ...base, name: null }, { ...base, flavors: {} },
    { ...base, flavors: [null] }, { ...base, flavors: [{ name: '辣度', value: 'bad json' }] },
    { ...base, flavors: [{ name: '辣度', value: '[1]' }] }]
  let body: unknown
  const detail = createDishDetail({ getById: async () => ({ ...response(), data: { code: 1, data: body as DishRecord } }) })
  for (body of bodies) {
    await detail.load(7)
    assert.ok(detail.state.error)
    assert.equal(detail.state.record, null)
    assert.deepEqual(detail.state.flavors, [])
    assert.equal(detail.state.loading, false)
  }
  body = base
  await detail.load(7)
  assert.ok(detail.state.record)
  assert.equal(detail.state.error, '')
})

test('business and network failures support retry without leaving stale details', async () => {
  let mode = 0
  const detail = createDishDetail({ getById: async () => {
    if (mode === 0) return { ...response(), data: { ...response().data, code: 0, msg: '菜品不存在' } }
    if (mode === 1) throw new Error('网络断开')
    return response()
  } })
  await detail.load(7); assert.equal(detail.state.error, '菜品不存在')
  mode = 1; await detail.load(7); assert.equal(detail.state.error, '网络断开')
  mode = 2; await detail.load(7)
  assert.equal(detail.state.error, '')
  assert.ok(detail.state.record)
})

test('closing the detail ignores late responses and prevents more loads', async () => {
  let resolve!: (value: ReturnType<typeof response>) => void
  let calls = 0
  const detail = createDishDetail({ getById: () => { calls++; return new Promise(done => { resolve = done }) } })
  const pending = detail.load(7)
  detail.dispose(); resolve(response()); await pending; await detail.load(8)
  assert.equal(calls, 1)
  assert.equal(detail.state.record, null)
  assert.deepEqual(detail.state.flavors, [])
  assert.equal(detail.state.loading, false)
})
