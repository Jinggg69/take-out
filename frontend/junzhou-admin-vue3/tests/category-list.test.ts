import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AxiosHeaders } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { CategoryApi, CategoryPage, CategoryQuery, CategoryRecord, CategoryResult } from '../src/api/category.ts'
import { createCategoryList } from '../src/views/category/list-state.ts'
import { createCategoryApi } from '../src/api/category.ts'
import { createHttpClient } from '../src/utils/http.ts'

const row: CategoryRecord = { id: 7, name: '鲜鱼', type: 1, sort: 0, status: 1 }
function response(records: CategoryRecord[] = [row], total: number | string = 35): AxiosResponse<CategoryResult<CategoryPage>> {
  return { data: { code: 1, data: { records, total } }, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config: { headers: new AxiosHeaders() } }
}
test('category query, Enter, clearing one filter and size changes retain the original current page', async () => {
  const calls: CategoryQuery[] = []
  const list = createCategoryList({ getPage: async params => { calls.push({ ...params }); return response() } })
  await list.load(); assert.deepEqual(calls.at(-1), { page: 1, pageSize: 10, name: undefined, type: undefined })
  list.state.page = 2; list.state.name = ' 鱼 '; list.state.type = 2
  await list.search(); assert.deepEqual(calls.at(-1), { page: 2, pageSize: 10, name: ' 鱼 ', type: 2 })
  await list.clearName(); assert.deepEqual(calls.at(-1), { page: 2, pageSize: 10, name: undefined, type: 2 })
  list.state.name = '鱼'; await list.clearType(); assert.deepEqual(calls.at(-1), { page: 2, pageSize: 10, name: '鱼', type: undefined })
  await list.setPageSize(20); assert.equal(calls.at(-1)!.pageSize, 20); assert.equal(calls.at(-1)!.page, 2)
  await list.setPage(1); assert.equal(calls.at(-1)!.page, 1)
  await list.clearFilters(); assert.equal(list.state.isSearch, false)
})
test('category total keeps original Number conversion for string totals', async () => {
  const list = createCategoryList({ getPage: async () => response([row], '11') })
  await list.load(); assert.equal(list.state.total, 11); assert.deepEqual(list.state.records, [row])
})
test('deleting the last row on page2 reloads page1 before the <=10 pagination disappears', async () => {
  const calls: CategoryQuery[] = []
  const list = createCategoryList({ getPage: async params => { calls.push({ ...params }); return params.page === 2 ? response([], 10) : response([row], 10) } })
  list.state.page = 2; await list.load()
  assert.deepEqual(calls.map(call => call.page), [2, 1]); assert.equal(list.state.page, 1)
  assert.equal(list.state.total, 10); assert.deepEqual(list.state.records, [row]); assert.equal(list.state.loading, false)
})
test('late category results cannot overwrite a newer name/type search', async () => {
  const resolve: ((value: ReturnType<typeof response>) => void)[] = []
  const list = createCategoryList({ getPage: () => new Promise(done => resolve.push(done)) })
  const first = list.load(); list.state.type = 2; const second = list.search()
  resolve[1]!(response([{ ...row, type: 2 }], 1)); await second
  resolve[0]!(response()); await first
  assert.equal(list.state.records[0]!.type, 2); assert.equal(list.state.total, 1); assert.equal(list.state.loading, false)
})
test('category business/transport errors are recoverable and do not claim an empty result', async () => {
  let attempt = 0
  const getPage: CategoryApi['getPage'] = async () => {
    attempt += 1
    if (attempt === 1) return { ...response(), data: { code: 0, desc: '分类查询失败', data: { records: [], total: 0 } } }
    if (attempt === 2) throw new Error('连接中断')
    return response([], 0)
  }
  const list = createCategoryList({ getPage })
  await list.load(); assert.equal(list.state.error, '分类查询失败')
  await list.load(); assert.equal(list.state.error, '连接中断')
  await list.load(); assert.equal(list.state.error, ''); assert.equal(list.state.total, 0)
})
test('leaving category management makes a pending response inert', async () => {
  let resolve!: (value: ReturnType<typeof response>) => void
  const list = createCategoryList({ getPage: () => new Promise(done => { resolve = done }) })
  const waiting = list.load(); list.dispose(); resolve(response()); await waiting
  assert.deepEqual(list.state.records, []); assert.equal(list.state.loading, false)
})
test('every successful category write refreshes past a pending pre-write snapshot instead of showing stale rows', async () => {
  const mutations: ((api: CategoryApi) => Promise<AxiosResponse<CategoryResult<null>>>)[] = [
    api => api.add({ name: '新分类', type: '1', sort: '0' }),
    api => api.edit({ id: 7, name: '新分类', sort: '0' }),
    api => api.remove(7),
    api => api.setStatus(7, 0),
  ]
  for (const mutate of mutations) {
    const pending: { config: InternalAxiosRequestConfig; resolve: (value: AxiosResponse<CategoryResult<CategoryPage>>) => void }[] = []
    const client = createHttpClient({ baseURL: '/api' })
    client.defaults.adapter = config => config.method === 'get'
      ? new Promise(resolve => pending.push({ config, resolve }))
      : Promise.resolve({ ...response(), config, data: { code: 1, data: null } })
    const api = createCategoryApi(client)
    const list = createCategoryList(api)
    const first = list.load(); await new Promise<void>(resolve => setImmediate(resolve))
    await mutate(api)
    const refresh = list.load()
    pending[0]!.resolve({ ...response([row], 1), config: pending[0]!.config })
    await new Promise<void>(resolve => setImmediate(resolve))
    assert.equal(pending.length, 2, 'a committed write must trigger a new HTTP read after the old one releases its duplicate lock')
    pending[1]!.resolve({ ...response([], 0), config: pending[1]!.config })
    await Promise.all([first, refresh])
    assert.deepEqual(list.state.records, []); assert.equal(list.state.total, 0); assert.equal(list.state.error, '')
  }
})
