import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import type { AddressInfo } from 'node:net'
import { after, before, test } from 'node:test'
import axios from 'axios'
import { createHttpClient } from '../src/utils/http.ts'

const seen: { url: string; token: string | undefined; body: string }[] = []
let baseURL = ''
let finishHeldRequest: (() => void) | undefined
let heldRequestArrived: (() => void) | undefined
const server = createServer(async (request, response) => {
  let body = ''
  for await (const chunk of request) body += chunk.toString()
  seen.push({ url: request.url ?? '', token: request.headers.token as string | undefined, body })
  if (request.url === '/api/held') {
    heldRequestArrived?.()
    await new Promise<void>((resolve) => { finishHeldRequest = resolve })
  }
  if (request.url === '/api/http-401') response.statusCode = 401
  if (request.url === '/api/export') {
    response.end(Buffer.from([0x50, 0x4b, 0x03, 0x04]))
    return
  }
  response.setHeader('Content-Type', 'application/json')
  response.end(JSON.stringify(request.url === '/api/body-401'
    ? { status: 401, msg: 'NOTLOGIN' }
    : request.url === '/api/business-failure'
      ? { code: 0, msg: '业务失败' }
      : { code: 1, data: { records: [], total: 0 }, msg: null }))
})

before(async () => {
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  baseURL = `http://127.0.0.1:${(server.address() as AddressInfo).port}/api`
})
after(async () => {
  finishHeldRequest?.()
  server.closeAllConnections()
  await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()))
})

test('GET retains zero, false, empty strings, encoded Chinese, and legacy nested parameters', async () => {
  const client = createHttpClient({ baseURL, getToken: () => 'existing-token' })
  const response = await client.get('/dish/page', { params: {
    page: 1, status: 0, name: '鱼 庄', enabled: false, empty: '',
    skip: undefined, nil: null, filter: { type: 1 }, ids: [3, 8],
  } })
  assert.equal(seen.at(-1)?.url,
    '/api/dish/page?page=1&status=0&name=%E9%B1%BC%20%E5%BA%84&enabled=false&empty=&filter%5Btype%5D=1&ids%5B0%5D=3&ids%5B1%5D=8')
  assert.equal(seen.at(-1)?.token, 'existing-token')
  assert.equal(response.status, 200)
  assert.deepEqual(response.data, { code: 1, data: { records: [], total: 0 }, msg: null })
})

test('POST keeps the JSON payload without unwrapping the Axios response', async () => {
  const client = createHttpClient({ baseURL })
  const response = await client.post('/dish', { name: '清蒸鱼', price: '38.50', status: 0 })
  assert.deepEqual(JSON.parse(seen.at(-1)?.body ?? ''), { name: '清蒸鱼', price: '38.50', status: 0 })
  assert.equal(response.data.code, 1)
  assert.equal(seen.at(-1)?.token, undefined)
})

test('business code zero stays available to the page rather than becoming a transport error', async () => {
  const response = await createHttpClient({ baseURL }).get('/business-failure')
  assert.deepEqual(response.data, { code: 0, msg: '业务失败' })
})

test('HTTP 401 notifies the auth boundary and still rejects with the original response', async () => {
  let unauthorized = 0
  const client = createHttpClient({ baseURL, onUnauthorized: () => { unauthorized += 1 } })
  await assert.rejects(client.get('/http-401'), (error: unknown) =>
    axios.isAxiosError(error) && error.response?.status === 401)
  assert.equal(unauthorized, 1)
})

test('response-body status 401 notifies the auth boundary without changing response shape', async () => {
  let unauthorized = 0
  const client = createHttpClient({ baseURL, onUnauthorized: () => { unauthorized += 1 } })
  const response = await client.get('/body-401')
  assert.equal(unauthorized, 1)
  assert.equal(response.data.status, 401)
})

test('duplicate cancellation leaves the original request pending until it finishes', { timeout: 5000 }, async () => {
  const client = createHttpClient({ baseURL })
  const arrived = new Promise<void>((resolve) => { heldRequestArrived = resolve })
  const original = client.get('/held')
  await arrived
  try {
    await assert.rejects(client.get('/held'), (error: unknown) => axios.isCancel(error))
    await assert.rejects(client.get('/held'), (error: unknown) => axios.isCancel(error))
  } finally {
    finishHeldRequest?.()
  }
  assert.equal((await original).data.code, 1)
  assert.equal(seen.filter(item => item.url === '/api/held').length, 1)
  const again = client.get('/held')
  await new Promise<void>((resolve) => { heldRequestArrived = resolve })
  finishHeldRequest?.()
  assert.equal((await again).status, 200)
})

test('binary report downloads retain their bytes', async () => {
  const response = await createHttpClient({ baseURL }).get('/export', { responseType: 'arraybuffer' })
  assert.deepEqual(Buffer.from(response.data), Buffer.from([0x50, 0x4b, 0x03, 0x04]))
})

test('a network failure does not strand the request in the duplicate registry', async () => {
  const client = createHttpClient({ baseURL: 'http://127.0.0.1:1/api' })
  for (let attempt = 0; attempt < 2; attempt += 1) {
    await assert.rejects(client.get('/offline'), (error: unknown) =>
      axios.isAxiosError(error) && !axios.isCancel(error))
  }
})
