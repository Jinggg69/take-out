import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AxiosHeaders } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { EmployeeApi, EmployeeQuery, EmployeePage, EmployeeRecord } from '../src/api/employee.ts'
import type { ApiResult } from '../src/api/types.ts'
import { createEmployeeList } from '../src/views/employee/list-state.ts'
import { createEmployeeApi } from '../src/api/employee.ts'
import { createHttpClient } from '../src/utils/http.ts'

const row: EmployeeRecord = { id: 1, username: 'chef123', name: '张师傅', phone: '13800138000', sex: '1', idNumber: '42038119900101123X', status: 1 }
function response(records: EmployeeRecord[] = [row], total = 80): AxiosResponse<ApiResult<EmployeePage>> {
  return { data: { code: 1, data: { records, total } }, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config: { headers: new AxiosHeaders() } }
}
function setup() {
  const calls: EmployeeQuery[] = []
  const list = createEmployeeList({ getList: async params => { calls.push({ ...params }); return response() } })
  return { list, calls }
}
test('employee list defaults to page1/size10 and keeps the original query-button and Enter page behavior', async () => {
  const { list, calls } = setup()
  await list.load(); assert.deepEqual(calls.at(-1), { page: 1, pageSize: 10, name: undefined })
  list.state.page = 3; list.state.name = ' 张 '
  await list.search(); assert.deepEqual(calls.at(-1), { page: 3, pageSize: 10, name: ' 张 ' })
  await list.search(true); assert.equal(calls.at(-1)!.page, 1)
  assert.deepEqual(list.state.records, [row]); assert.equal(list.state.total, 80)
})
test('clear, page change and page-size change preserve original query parameters', async () => {
  const { list, calls } = setup()
  list.state.page = 3; list.state.name = '张'
  await list.setPageSize(20); assert.deepEqual(calls.at(-1), { page: 3, pageSize: 20, name: '张' })
  await list.setPage(2); assert.equal(calls.at(-1)!.page, 2)
  await list.clearSearch(); assert.deepEqual(calls.at(-1), { page: 2, pageSize: 20, name: undefined })
  assert.equal(list.state.isSearch, false)
})
test('a late list response cannot overwrite a newer employee search or its loading state', async () => {
  const resolvers: ((value: ReturnType<typeof response>) => void)[] = []
  const list = createEmployeeList({ getList: () => new Promise(resolve => resolvers.push(resolve)) })
  const old = list.load(); list.state.name = 'new'; const current = list.search(true)
  resolvers[1]!(response([{ ...row, id: 2, name: 'new' }], 1)); await current
  resolvers[0]!(response()); await old
  assert.equal(list.state.records[0]!.id, 2); assert.equal(list.state.total, 1); assert.equal(list.state.loading, false)
})
test('business and transport failures have a recoverable state and a successful retry clears it', async () => {
  let attempts = 0
  const getList: EmployeeApi['getList'] = async () => {
    attempts += 1
    if (attempts === 1) return { ...response(), data: { code: 0, msg: '暂时不可用', data: { records: [], total: 0 } } }
    if (attempts === 2) throw new Error('网络中断')
    return response([], 0)
  }
  const list = createEmployeeList({ getList })
  await list.load(); assert.equal(list.state.error, '暂时不可用'); assert.equal(list.state.loading, false)
  await list.load(); assert.equal(list.state.error, '网络中断')
  await list.load(); assert.equal(list.state.error, ''); assert.deepEqual(list.state.records, []); assert.equal(list.state.total, 0)
})
test('leaving the employee page makes in-flight list responses inert', async () => {
  let resolve!: (value: ReturnType<typeof response>) => void
  const list = createEmployeeList({ getList: () => new Promise(done => { resolve = done }) })
  const pending = list.load(); list.dispose(); resolve(response()); await pending
  assert.deepEqual(list.state.records, []); assert.equal(list.state.loading, false)
})

function pendingHttpList() {
  const pending: { config: InternalAxiosRequestConfig; resolve: (response: AxiosResponse<ApiResult<EmployeePage>>) => void }[] = []
  const client = createHttpClient({ baseURL: '/api' })
  client.defaults.adapter = config => new Promise(resolve => { pending.push({ config, resolve }) })
  const list = createEmployeeList(createEmployeeApi(client))
  return { list, pending, tick: () => new Promise<void>(resolve => setImmediate(resolve)) }
}
test('repeated Enter during a real HTTP request reuses it rather than stranding the list on a canceled duplicate', async () => {
  const { list, pending, tick } = pendingHttpList()
  const first = list.load(); const repeated = list.search(true)
  await tick(); assert.equal(pending.length, 1)
  pending[0]!.resolve({ ...response(), config: pending[0]!.config })
  await Promise.all([first, repeated])
  assert.equal(list.state.error, ''); assert.deepEqual(list.state.records, [row]); assert.equal(list.state.loading, false)
})
test('returning to an already pending query A after B reuses A and keeps the latest search result', async () => {
  const { list, pending, tick } = pendingHttpList()
  list.state.name = 'A'; const first = list.search(true)
  list.state.name = 'B'; const second = list.search(true)
  list.state.name = 'A'; const latest = list.search(true)
  await tick(); assert.equal(pending.length, 2)
  pending[0]!.resolve({ ...response([{ ...row, name: 'A' }]), config: pending[0]!.config })
  pending[1]!.resolve({ ...response([{ ...row, name: 'B' }]), config: pending[1]!.config })
  await Promise.all([first, second, latest])
  assert.equal(list.state.error, ''); assert.equal(list.state.records[0]!.name, 'A')
})
