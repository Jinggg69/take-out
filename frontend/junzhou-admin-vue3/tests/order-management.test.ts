import assert from 'node:assert/strict'
import { test } from 'node:test'
import axios, { AxiosHeaders } from 'axios'
import { createOrderApi } from '../src/api/order.ts'
import { buildOrderQuery, orderActions, orderReason, shouldOpenNextOrder } from '../src/utils/order.ts'
import { createOrderList } from '../src/views/order/list-state.ts'
import { createHttpClient } from '../src/utils/http.ts'

test('order API preserves paths, verbs and exact mutation bodies', async () => {
  const calls: { method?: string; url?: string; data: unknown; params: unknown }[] = []
  const api = createOrderApi(axios.create({ adapter: async config => { calls.push({ method: config.method, url: config.url, data: config.data ? JSON.parse(config.data) : undefined, params: config.params }); return { data: { code: 1, data: null }, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config } } }))
  await api.getPage({ page: 1, pageSize: 10 }); await api.getStatistics(); await api.getDetail('900000000000000001')
  await api.accept(7); await api.reject({ id: 7, rejectionReason: '已打烊' }); await api.cancel({ id: 8, cancelReason: '客户电话取消' }); await api.delivery(9); await api.complete(9)
  assert.deepEqual(calls.map(c => [c.method, c.url, c.data]), [
    ['get', '/order/conditionSearch', undefined], ['get', '/order/statistics', undefined], ['get', '/order/details/900000000000000001', undefined],
    ['put', '/order/confirm', { id: 7 }], ['put', '/order/rejection', { id: 7, rejectionReason: '已打烊' }], ['put', '/order/cancel', { id: 8, cancelReason: '客户电话取消' }], ['put', '/order/delivery/9', undefined], ['put', '/order/complete/9', undefined],
  ])
})
test('order query uses full-day bounds and omits all-status and empty fields', () => {
  assert.deepEqual(buildOrderQuery({ page: 2, pageSize: 20, number: '', phone: '', dates: [], status: 0 }), { page: 2, pageSize: 20, number: undefined, phone: undefined, beginTime: undefined, endTime: undefined, status: undefined })
  assert.deepEqual(buildOrderQuery({ page: 1, pageSize: 10, number: '001', phone: '138', dates: ['2026-10-01', '2026-10-23'], status: 2 }), { page: 1, pageSize: 10, number: '001', phone: '138', beginTime: '2026-10-01 00:00:00', endTime: '2026-10-23 23:59:59', status: 2 })
})
test('order actions retain original status permissions including completed cancellation', () => {
  assert.deepEqual([1, 2, 3, 4, 5, 6].map(orderActions), [['cancel'], ['accept', 'reject'], ['delivery', 'cancel'], ['complete', 'cancel'], ['cancel'], []])
})
test('custom rejection and cancellation reasons are required, trimmed and limited to 20 characters', () => {
  assert.throws(() => orderReason('', '', '取消'), /请选择取消原因/)
  assert.throws(() => orderReason('自定义原因', '   ', '拒绝'), /请输入拒绝原因/)
  assert.throws(() => orderReason('自定义原因', '一'.repeat(21), '取消'), /20/)
  assert.equal(orderReason('自定义原因', ' 客户要求 ', '取消'), '客户要求')
  assert.equal(orderReason('骑手不足无法配送', '', '取消'), '骑手不足无法配送')
})
test('auto-next keeps original remaining-row threshold and only runs from pending detail', () => {
  assert.equal(shouldOpenNextOrder(true, true, 2, 2, 2), true)
  assert.equal(shouldOpenNextOrder(true, true, 2, 2, 1), false)
  assert.equal(shouldOpenNextOrder(true, false, 2, 2, 1), false)
  assert.equal(shouldOpenNextOrder(true, true, 0, 2, 1), false)
  assert.equal(shouldOpenNextOrder(false, true, 2, 2, 1), false)
  assert.equal(shouldOpenNextOrder(true, true, 2, 2, 0), false)
})
function response(records = [{ id: 1, number: '01', status: 2, amount: 30 }], total: number | string = 1) { return { data: { code: 1, data: { records, total } } } }
test('order list keeps latest responses and resets filters/page before a tab request', async () => {
  const pending: ((r: ReturnType<typeof response>) => void)[] = []
  const list = createOrderList({ getPage: () => new Promise(resolve => pending.push(resolve)) })
  const old = list.load(); list.state.number = '02'; const recent = list.search()
  pending[1]!(response([{ id: 2, number: '02', status: 2, amount: 40 }])); await recent
  pending[0]!(response()); await old; assert.equal(list.state.records[0]!.id, 2)
  const tab = list.setStatus(3); assert.equal(list.state.number, ''); assert.equal(list.state.page, 1); pending[2]!(response()); await tab
})
test('order load failures are recoverable and empty high pages return to an available page', async () => {
  let fail = true
  const list = createOrderList({ getPage: async p => fail ? { data: { code: 0, msg: '读取失败', data: { records: [], total: 0 } } } : response(p.page > 1 ? [] : undefined, 1) })
  await list.load(); assert.match(list.state.error, /读取失败/); fail = false
  await list.setPage(3); assert.equal(list.state.page, 1); assert.equal(list.state.error, ''); assert.equal(list.state.records.length, 1)
})
test('shared order reads deduplicate HTTP and refresh a read started before a successful write', async () => {
  const client = createHttpClient({ baseURL: '/api' })
  let getCount = 0
  let finish!: () => void
  client.defaults.adapter = async config => {
    if (config.method === 'get') {
      getCount++
      if (getCount === 1) await new Promise<void>(resolve => { finish = resolve })
    }
    return { data: { code: 1, data: { records: [{ id: 1, status: getCount === 1 ? 2 : 3 }], total: 1 } }, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config }
  }
  const api = createOrderApi(client)
  const first = api.getPage({ page: 1, pageSize: 10 })
  const second = api.getPage({ page: 1, pageSize: 10 })
  assert.equal(first, second)
  await api.accept(1)
  finish()
  assert.equal((await first).data.data.records[0]!.status, 3)
  assert.equal(getCount, 2)
})
test('disposed order list ignores pending reads and stops further requests', async () => {
  let complete!: (r: ReturnType<typeof response>) => void
  let calls = 0
  const list = createOrderList({ getPage: () => { calls++; return new Promise(resolve => { complete = resolve }) } })
  const loading = list.load(); list.dispose(); complete(response()); await loading; await list.load()
  assert.equal(calls, 1); assert.equal(list.state.loading, false); assert.equal(list.state.records.length, 0)
})
