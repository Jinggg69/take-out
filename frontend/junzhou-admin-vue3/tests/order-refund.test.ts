import assert from 'node:assert/strict'
import { test } from 'node:test'
import * as order from '../src/utils/order.ts'

for (const [refundStatus, payStatus, label] of [
  ['REQUESTED', 1, '退款处理中'], ['PROCESSING', 3, '退款处理中'],
  ['VERIFYING', 2, '退款待核验'], ['SUCCESS', 2, '退款成功'],
  ['CLOSED', 1, '退款已关闭，请联系商家'], ['ABNORMAL', 4, '退款异常，请联系商家'],
  [null, 2, '退款待核验'], [null, 3, '退款处理中'], [null, 4, '退款异常，请联系商家'],
  [null, 1, ''], [null, 0, ''], ['UNKNOWN', 2, '退款待核验'],
] as const) {
  test(`refund ${refundStatus}, pay ${payStatus} is represented without assuming money returned`, () => {
    assert.equal(typeof order.orderRefundLabel, 'function')
    assert.equal(order.orderRefundLabel({ refundStatus, payStatus }), label)
  })
}
test('unknown order status does not invent a refund lifecycle', () => {
  assert.equal(order.orderStatusLabel(7), '未知状态')
})
