import assert from 'node:assert/strict'
import { test } from 'node:test'
import axios, { AxiosHeaders } from 'axios'
import { createDashboardApi } from '../src/api/dashboard.ts'
import { createDashboardResource, dashboardCount, dashboardMoney, dashboardRate, dashboardOrderLinks } from '../src/utils/dashboard.ts'

test('dashboard overview uses the four original workspace GET paths without date parameters', async () => {
  const calls: unknown[] = []
  const api = createDashboardApi(axios.create({ adapter: async config => {
    calls.push([config.method, config.url, config.params, config.data])
    return { data: { code: 1, data: {} }, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config }
  } }))
  await api.getBusinessData(); await api.getOrdersOverview(); await api.getDishesOverview(); await api.getSetmealsOverview()
  assert.deepEqual(calls, ['/workspace/businessData', '/workspace/overviewOrders', '/workspace/overviewDishes', '/workspace/overviewSetmeals'].map(url => ['get', url, undefined, undefined]))
})
test('dashboard distinguishes unavailable figures from real zero and formats ratios as the original percent', () => {
  assert.equal(dashboardCount(0), '0'); assert.equal(dashboardCount(undefined), '—')
  assert.equal(dashboardMoney('120.1'), '￥120.10'); assert.equal(dashboardMoney(null), '—')
  assert.equal(dashboardRate(0.856), '86%'); assert.equal(dashboardRate(0), '0%')
  assert.equal(dashboardRate('NaN'), '—'); assert.equal(dashboardCount(''), '—')
  assert.deepEqual(dashboardOrderLinks.map(item => [item.key, item.status]), [['waitingOrders', 2], ['deliveredOrders', 3], ['completedOrders', 5], ['cancelledOrders', 6], ['allOrders', 0]])
})
test('dashboard resource retries failures and only displays the latest successful request', async () => {
  type Result = { data: { code: number; msg?: string; data: { sold: number } } }
  const pending: ((result: Result) => void)[] = []
  const resource = createDashboardResource(() => new Promise<Result>(resolve => pending.push(resolve)))
  const old = resource.load(); const latest = resource.load()
  pending[1]!({ data: { code: 1, data: { sold: 8 } } }); await latest
  pending[0]!({ data: { code: 1, data: { sold: 3 } } }); await old
  assert.equal(resource.state.data?.sold, 8)
  const failed = resource.load(); pending[2]!({ data: { code: 0, msg: '暂不可用', data: { sold: 0 } } }); await failed
  assert.equal(resource.state.error, '暂不可用'); assert.equal(resource.state.data, null)
  const retry = resource.load(); pending[3]!({ data: { code: 1, data: { sold: 0 } } }); await retry
  assert.equal(resource.state.error, ''); assert.deepEqual(resource.state.data, { sold: 0 })
})
test('disposed dashboard sections ignore pending requests and never issue another read', async () => {
  let finish!: (result: { data: { code: number; data: { sold: number } } }) => void
  let calls = 0
  const resource = createDashboardResource<{ sold: number }>(() => { calls++; return new Promise(resolve => { finish = resolve }) })
  const pending = resource.load(); resource.dispose(); finish({ data: { code: 1, data: { sold: 5 } } }); await pending; await resource.load()
  assert.equal(calls, 1); assert.equal(resource.state.data, null); assert.equal(resource.state.loading, false)
})
