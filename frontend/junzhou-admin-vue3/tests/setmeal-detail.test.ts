import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AxiosHeaders } from 'axios'
import type { AxiosResponse } from 'axios'
import type { SetmealId, SetmealRecord, SetmealResult } from '../src/api/setmeal.ts'
import { createSetmealDetail } from '../src/views/setmeal/detail-state.ts'

function response(overrides: Partial<SetmealRecord> = {}): AxiosResponse<SetmealResult<SetmealRecord>> {
  return {
    data: { code: 1, data: { id: 7, name: '草鱼套餐', categoryId: 3, price: 198, image: '', status: 1,
      setmealDishes: [{ dishId: 9, name: '清蒸草鱼', price: '68.50', copies: 2 }], ...overrides } },
    status: 200, statusText: 'OK', headers: new AxiosHeaders(), config: { headers: new AxiosHeaders() },
  }
}

test('detail loads the selected setmeal including its saved dish prices and copies', async () => {
  const ids: SetmealId[] = []
  const detail = createSetmealDetail({ getById: async id => { ids.push(id); return response() } })
  await detail.load('7')
  assert.deepEqual(ids, ['7'])
  assert.equal(detail.state.name, '草鱼套餐')
  assert.deepEqual(detail.state.dishes, [{ dishId: 9, name: '清蒸草鱼', price: '68.50', copies: 2 }])
  assert.equal(detail.state.loading, false)
  assert.equal(detail.state.error, '')
})

test('switching setmeals clears old details and ignores an earlier response', async () => {
  const resolves: ((value: ReturnType<typeof response>) => void)[] = []
  const detail = createSetmealDetail({ getById: () => new Promise(resolve => resolves.push(resolve)) })
  const first = detail.load(7)
  resolves[0]!(response()); await first
  const second = detail.load(8)
  assert.equal(detail.state.loading, true)
  assert.equal(detail.state.name, '')
  assert.deepEqual(detail.state.dishes, [])
  const third = detail.load(9)
  resolves[2]!(response({ id: 9, name: '新套餐', setmealDishes: [] })); await third
  resolves[1]!(response({ id: 8, name: '旧套餐' })); await second
  assert.equal(detail.state.name, '新套餐')
  assert.deepEqual(detail.state.dishes, [])
  assert.equal(detail.state.loading, false)
})

test('late failures cannot replace the current setmeal detail', async () => {
  let rejectFirst!: (cause: Error) => void
  const detail = createSetmealDetail({ getById: id => id === 7
    ? new Promise((_resolve, reject) => { rejectFirst = reject })
    : Promise.resolve(response({ id: 8, name: '新套餐' })) })
  const first = detail.load(7)
  await detail.load(8)
  rejectFirst(new Error('旧请求失败')); await first
  assert.equal(detail.state.name, '新套餐')
  assert.equal(detail.state.error, '')
})

test('business and network failures can be retried without retaining old dishes', async () => {
  let mode = 0
  const detail = createSetmealDetail({ getById: async () => {
    if (mode === 0) return { ...response(), data: { ...response().data, code: 0, msg: '套餐不存在' } }
    if (mode === 1) throw new Error('网络断开')
    return response()
  } })
  await detail.load(7); assert.equal(detail.state.error, '套餐不存在')
  assert.deepEqual(detail.state.dishes, [])
  mode = 1; await detail.load(7); assert.equal(detail.state.error, '网络断开')
  mode = 2; await detail.load(7)
  assert.equal(detail.state.error, '')
  assert.equal(detail.state.dishes.length, 1)
})

test('an empty dish array is valid while missing or malformed detail is an error', async () => {
  const base = response().data.data
  const bodies: unknown[] = [null, {}, { ...base, setmealDishes: null }, { ...base, setmealDishes: {} },
    { ...base, setmealDishes: [null] }, { ...base, setmealDishes: [{ dishId: 9 }] }]
  let body: unknown
  const detail = createSetmealDetail({ getById: async () => ({ ...response(), data: { code: 1, data: body as SetmealRecord } }) })
  for (body of bodies) {
    await detail.load(7)
    assert.ok(detail.state.error)
    assert.deepEqual(detail.state.dishes, [])
    assert.equal(detail.state.loading, false)
  }
  body = response({ setmealDishes: [] }).data.data
  await detail.load(7)
  assert.equal(detail.state.error, '')
  assert.deepEqual(detail.state.dishes, [])
})

test('disposing on close ignores pending responses and prevents further requests', async () => {
  let resolve!: (value: ReturnType<typeof response>) => void
  let calls = 0
  const detail = createSetmealDetail({ getById: () => { calls++; return new Promise(done => { resolve = done }) } })
  const pending = detail.load(7)
  detail.dispose()
  resolve(response()); await pending
  await detail.load(8)
  assert.equal(calls, 1)
  assert.equal(detail.state.name, '')
  assert.deepEqual(detail.state.dishes, [])
  assert.equal(detail.state.loading, false)
})
