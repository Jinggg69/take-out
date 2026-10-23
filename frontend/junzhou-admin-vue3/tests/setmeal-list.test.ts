import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AxiosHeaders } from 'axios'
import type { AxiosResponse } from 'axios'
import type { SetmealPage, SetmealQuery, SetmealRecord, SetmealResult } from '../src/api/setmeal.ts'
import { createSetmealList } from '../src/views/setmeal/list-state.ts'

const row: SetmealRecord = { id: 7, name: '鲜鱼', categoryId: 3, price: 28, image: '/fish.png', status: 0 }
function response(records: SetmealRecord[] = [row], total: number | string = 35): AxiosResponse<SetmealResult<SetmealPage>> {
  return { data: { code: 1, data: { records, total } }, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config: { headers: new AxiosHeaders() } }
}
test('setmeal Enter resets page while query keeps the current page and clears/capacity reset it; zero status is sent', async () => {
  const calls: SetmealQuery[] = []
  const list = createSetmealList({ getPage: async params => { calls.push({ ...params }); return response() } })
  await list.setPage(3); list.state.name = ' 鱼汤 '; list.state.categoryId = 3; list.state.status = 0
  await list.load(); assert.deepEqual(calls.at(-1), { page: 3, pageSize: 10, name: ' 鱼汤 ', categoryId: 3, status: 0 })
  await list.setPageSize(20); assert.equal(calls.at(-1)!.page, 1)
  await list.clearName(); assert.equal(calls.at(-1)!.page, 1); assert.equal(calls.at(-1)!.status, 0)
  await list.search(); assert.equal(calls.at(-1)!.page, 1)
  await list.clearFilters(); assert.equal(calls.at(-1)!.status, '')
})
test('setmeal stale responses cannot overwrite the latest filter results', async () => {
  const resolves: ((r: ReturnType<typeof response>) => void)[] = []
  const list = createSetmealList({ getPage: () => new Promise(resolve => resolves.push(resolve)) })
  const first = list.load(); list.state.name = '新'; const second = list.search()
  resolves[1]!(response([{ ...row, name: '新鲜鱼' }], 1)); await second
  resolves[0]!(response()); await first
  assert.equal(list.state.records[0]!.name, '新鲜鱼'); assert.equal(list.state.total, 1)
})
test('setmeal code0 and invalid response are recoverable errors without false empty success', async () => {
  let mode = 0
  const list = createSetmealList({ getPage: async () => { if (mode === 0) return { ...response(), data: { code: 0, msg: '暂不可用', data: { records: [], total: 0 } } }; if (mode === 1) return response([], 'bad'); return response() } })
  await list.load(); assert.match(list.state.error, /暂不可用/)
  mode = 1; await list.load(); assert.ok(list.state.error)
  mode = 2; await list.load(); assert.equal(list.state.error, ''); assert.equal(list.state.records.length, 1)
})
test('deleting the last setmeal on a page returns to an available page even when pagination will hide', async () => {
  const calls: number[] = []
  const list = createSetmealList({ getPage: async p => { calls.push(p.page); return p.page === 2 ? response([], 10) : response([row], 10) } })
  await list.setPage(2); assert.deepEqual(calls, [2, 1]); assert.equal(list.state.page, 1)
})
test('disposed setmeal list ignores pending responses and further requests', async () => {
  let complete!: (r: ReturnType<typeof response>) => void; let calls = 0
  const list = createSetmealList({ getPage: () => { calls++; return new Promise(resolve => { complete = resolve }) } })
  const pending = list.load(); list.dispose(); complete(response()); await pending; await list.load()
  assert.equal(calls, 1); assert.equal(list.state.records.length, 0); assert.equal(list.state.loading, false)
})
