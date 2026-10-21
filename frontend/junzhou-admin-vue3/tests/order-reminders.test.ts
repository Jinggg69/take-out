import assert from 'node:assert/strict'
import { test } from 'node:test'
import { buildOrderSocketUrl, parseOrderMessage, connectOrderSocket } from '../src/services/order-reminders.ts'

test('absolute development and same-origin production WebSocket addresses keep the ws path', () => {
  assert.equal(buildOrderSocketUrl('ws://localhost:8080/ws/', 'http://localhost:8889', 'abc'), 'ws://localhost:8080/ws/abc')
  assert.equal(buildOrderSocketUrl('/ws/', 'https://example.com', 'abc'), 'wss://example.com/ws/abc')
  assert.equal(buildOrderSocketUrl('/ws/', 'http://example.com', 'abc'), 'ws://example.com/ws/abc')
})
test('new order and reminder messages retain raw text and order IDs while malformed messages are ignored', () => {
  assert.deepEqual(parseOrderMessage('{"type":1,"orderId":123,"content":"新订单"}'), { type: 1, orderId: 123, content: '新订单' })
  assert.equal(parseOrderMessage('{"type":2,"orderId":"456","content":"<b>催单</b>"}')?.content, '<b>催单</b>')
  for (const value of ['bad json', 'null', '{"type":9}', '{}']) assert.equal(parseOrderMessage(value), null)
})
test('socket cleanup removes listeners and closes the connection exactly once', () => {
  class TestSocket extends EventTarget { closes = 0; close() { this.closes += 1 } }
  const socket = new TestSocket()
  let orders = 0
  let failures = 0
  const cleanup = connectOrderSocket('ws://fixture/ws/abc', {
    onOrder: () => { orders += 1 }, onError: () => { failures += 1 },
  }, () => socket)
  socket.dispatchEvent(new MessageEvent('message', { data: '{"type":2,"orderId":1,"content":"催单"}' }))
  socket.dispatchEvent(new Event('error'))
  assert.equal(orders, 1)
  assert.equal(failures, 1)
  cleanup(); cleanup()
  socket.dispatchEvent(new MessageEvent('message', { data: '{"type":1,"orderId":2,"content":"新单"}' }))
  assert.equal(orders, 1)
  assert.equal(socket.closes, 1)
})
