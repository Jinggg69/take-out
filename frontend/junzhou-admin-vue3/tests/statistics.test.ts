import assert from 'node:assert/strict'
import { test } from 'node:test'
import axios, { AxiosHeaders, AxiosError } from 'axios'
import { createStatisticsApi } from '../src/api/statistics.ts'
import { presetRange, chartRows, rankingRows, lineSegments, exportWorkbook } from '../src/utils/statistics.ts'

test('statistics preserves four report GET paths, date-only bounds and binary export without filter params', async () => {
  const calls: { url?: string; params: unknown; responseType?: string }[] = []
  const workbook = new Blob(['xlsx'], { type: 'application/octet-stream' })
  const api = createStatisticsApi(axios.create({ adapter: async config => {
    calls.push({ url: config.url, params: config.params, responseType: config.responseType })
    return { data: config.responseType === 'blob' ? workbook : { code: 1, data: {} }, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config }
  } }))
  const params = { begin: '2026-10-16', end: '2026-10-22' }
  await api.getTurnover(params); await api.getUsers(params); await api.getOrders(params); await api.getTop10(params)
  assert.deepEqual(calls.map(c => [c.url, c.params]), ['/report/turnoverStatistics', '/report/userStatistics', '/report/ordersStatistics', '/report/top10'].map(url => [url, params]))
  assert.equal((await api.export()).data, workbook)
  assert.deepEqual(calls[4], { url: '/report/export', params: undefined, responseType: 'blob' })
})

test('presets retain default seven completed days, leap year/month/year bounds and original Sunday week behavior', () => {
  const now = new Date(2026, 9, 23, 15)
  assert.deepEqual(presetRange('yesterday', now), { begin: '2026-10-22', end: '2026-10-22' })
  assert.deepEqual(presetRange('7', now), { begin: '2026-10-16', end: '2026-10-22' })
  assert.deepEqual(presetRange('30', new Date(2024, 2, 1)), { begin: '2024-01-31', end: '2024-02-29' })
  assert.deepEqual(presetRange('month', new Date(2024, 1, 20)), { begin: '2024-02-01', end: '2024-02-29' })
  assert.deepEqual(presetRange('week', new Date(2026, 9, 25)), { begin: '2026-10-26', end: '2026-11-01' })
  assert.deepEqual(presetRange('7', new Date(2026, 0, 1)), { begin: '2025-12-25', end: '2025-12-31' })
})

test('CSV series keep real zero, preserve gaps and aligned dates without invented values', () => {
  assert.deepEqual(chartRows('2026-10-01,2026-10-02,2026-10-03', ['0,,12.5', '1,invalid,3']), [
    { label: '2026-10-01', values: [0, 1] }, { label: '2026-10-02', values: [null, null] }, { label: '2026-10-03', values: [12.5, 3] },
  ])
  assert.deepEqual(chartRows('', ['']), [])
  assert.deepEqual(rankingRows('鱼,虾', '0,5'), [{ label: '鱼', values: [0] }, { label: '虾', values: [5] }])
  assert.equal(lineSegments([{ label: 'a', values: [0] }, { label: 'b', values: [null] }, { label: 'c', values: [3] }], 0, 3).length, 2)
})

test('export rejects JSON business errors instead of saving an Excel file and accepts binary', async () => {
  await assert.rejects(() => exportWorkbook(new Blob(['{"code":0,"msg":"导出未开放"}'], { type: 'application/json' })), /导出未开放/)
  await assert.rejects(() => exportWorkbook(new Blob([])), /空/)
  const blob = new Blob(['PKworkbook'], { type: 'application/octet-stream' })
  assert.equal(await exportWorkbook(blob), blob)
})

// A stale successful response must never replace a newer failed/empty selection.
import { createStatisticsState } from '../src/views/statistics/state.ts'

test('statistics isolates section errors, ignores stale ranges and permits retry without fabricating data', async () => {
  let resolveOld!: (value: { data: { code: number; data: { dateList: string; turnoverList: string } } }) => void
  let calls = 0
  const api = {
    getTurnover: async () => { calls++; if (calls === 1) return new Promise<{ data: { code: number; data: { dateList: string; turnoverList: string } } }>(resolve => { resolveOld = resolve }); return { data: { code: 1, data: { dateList: '', turnoverList: '' } } } },
    getUsers: async () => ({ data: { code: 0, msg: '服务未开放', data: null } }),
    getOrders: async () => ({ data: { code: 1, data: null } }),
    getTop10: async () => ({ data: { code: 1, data: { nameList: '', numberList: '' } } }),
  }
  const view = createStatisticsState(api)
  const old = view.load({ begin: '2026-10-01', end: '2026-10-02' })
  await view.load({ begin: '2026-10-16', end: '2026-10-22' })
  resolveOld({ data: { code: 1, data: { dateList: '2026-10-01', turnoverList: '99' } } }); await old
  assert.equal(view.state.turnover.data?.dateList, '')
  assert.equal(view.state.users.error, '服务未开放')
  assert.equal(view.state.orders.data, null)
  assert.match(view.state.orders.error, /数据/)
  await view.retry('turnover'); assert.equal(calls, 3)
  view.dispose(); await view.retry('turnover'); assert.equal(calls, 3)
})

test('identical in-flight report reads share the HTTP request and completed reports remain fresh', async () => {
  let finish!: () => void; let count = 0
  const api = createStatisticsApi(axios.create({ adapter: async config => { count++; await new Promise<void>(resolve => { finish = resolve }); return { data: { code: 1, data: {} }, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config } } }))
  const range = { begin: '2026-10-16', end: '2026-10-22' }
  const first = api.getTurnover(range); const duplicate = api.getTurnover(range)
  assert.equal(first, duplicate)
  await new Promise(resolve => setImmediate(resolve)); finish(); await first
  const fresh = api.getTurnover(range); await new Promise(resolve => setImmediate(resolve)); finish(); await fresh
  assert.equal(count, 2)
})

import { createHttpClient } from '../src/utils/http.ts'
test('rapid A to B to A through shared HTTP retains latest A and retries a rejected report', async () => {
  const client = createHttpClient({ baseURL: '/api' })
  const completions = new Map<string, () => void>()
  let calls = 0
  let fail = false
  client.defaults.adapter = async config => {
    calls++
    const url = config.url ?? ''
    if (url.startsWith('/report/turnoverStatistics')) await new Promise<void>(resolve => completions.set(url.includes('begin=2026-10-01') ? 'A' : 'B', resolve))
    if (fail) throw new AxiosError('连接失败', 'ERR_NETWORK', config)
    const data = url.startsWith('/report/turnoverStatistics') ? { dateList: url.includes('begin=2026-10-01') ? '2026-10-01' : '2026-10-02', turnoverList: '0' } : {}
    return { data: { code: 1, data }, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config }
  }
  const api = createStatisticsApi(client)
  const reports = createStatisticsState(api)
  const a = { begin: '2026-10-01', end: '2026-10-01' }
  const b = { begin: '2026-10-02', end: '2026-10-02' }
  const first = reports.load(a); const second = reports.load(b); const third = reports.load(a)
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(calls, 8)
  completions.get('A')!(); completions.get('B')!()
  await Promise.all([first, second, third])
  assert.equal(reports.state.turnover.data?.dateList, '2026-10-01')
  assert.equal(reports.state.turnover.error, '')
  fail = true
  const failed = reports.retry('turnover'); await new Promise(resolve => setImmediate(resolve)); completions.get('A')!(); await failed
  assert.match(reports.state.turnover.error, /连接失败/)
  fail = false
  const recovered = reports.retry('turnover'); await new Promise(resolve => setImmediate(resolve)); completions.get('A')!(); await recovered
  assert.equal(reports.state.turnover.error, '')
  assert.equal(reports.state.turnover.data?.turnoverList, '0')
})

