import assert from 'node:assert/strict'
import { test } from 'node:test'
import type { AxiosInstance } from 'axios'
import { createOrderApi } from '../src/api/order.ts'
import { orderActions, orderPaymentBreakdown, permittedOrderActions } from '../src/utils/order.ts'

test('completed order refund uses POST with the original string ID and reason only', async () => {
  const calls: unknown[][] = []
  const client = { post: async (...args: unknown[]) => { calls.push(args); return { data: { code: 1 } } } } as unknown as AxiosInstance
  await createOrderApi(client).refund('9007199254740993', '顾客申请退款')
  assert.deepEqual(calls, [['/order/9007199254740993/refund', { reason: '顾客申请退款' }]])
})
test('refund action requires a completed paid order without any refund state', () => {
  for (const fulfillmentType of ['DELIVERY', 'PICKUP', 'DINE_IN']) {
    assert.deepEqual(orderActions('5', { fulfillmentType, payStatus: '1' }), ['refund'])
    for (const payStatus of [0, 2, 3, 4]) assert.deepEqual(orderActions(5, { fulfillmentType, payStatus }), [])
    for (const refundStatus of ['REQUESTED', 'PROCESSING', 'VERIFYING', 'SUCCESS', 'CLOSED', 'ABNORMAL']) assert.deepEqual(orderActions(5, { fulfillmentType, payStatus: 1, refundStatus }), [])
  }
  assert.deepEqual(orderActions(1, { payStatus: 0 }), ['cancel'])
  assert.deepEqual(orderActions(2, { payStatus: 1 }), ['accept', 'reject'])
  assert.deepEqual(orderActions(3, { payStatus: 1 }), ['delivery', 'cancel'])
  assert.deepEqual(orderActions(4, { payStatus: 1 }), ['complete', 'cancel'])
  assert.deepEqual(orderActions(6, { payStatus: 2, refundStatus: 'SUCCESS' }), [])
})
test('payment breakdown displays explicit external and wallet split and preserves zero', () => {
  assert.deepEqual(orderPaymentBreakdown({ amount: 20, externalPayAmount: 0, membership: { principalAmount: 15, giftAmount: 5, balanceAmount: 20, pointsUsed: 100, pointsDiscount: 1, couponDiscount: 2, levelDiscount: 0, discountAmount: 3 } }), { externalAmount: 0, balanceAmount: 20, principalAmount: 15, giftAmount: 5, pointsUsed: 100, pointsDiscount: 1, couponDiscount: 2, levelDiscount: 0, discountAmount: 3 })
  assert.equal(orderPaymentBreakdown({ amount: '20' }).externalAmount, 20)
  assert.equal(orderPaymentBreakdown({ amount: 20, pricingSnapshot: JSON.stringify({ member: { externalAmount: 8, balanceAmount: 12, principalAmount: 10, giftAmount: 2 } }) }).externalAmount, 8)
})
test('completed refund is independently permissioned and does not require cancellation permission', () => {
  const row = { id: '1', number: 'N1', status: 5, amount: 20, payStatus: 1 }
  assert.deepEqual(permittedOrderActions(row, action => action === 'order:refund'), ['refund'])
  assert.deepEqual(permittedOrderActions(row, action => action === 'order:cancel'), [])
  assert.deepEqual(permittedOrderActions({ ...row, refundStatus: 'SUCCESS' }, () => true), [])
  assert.deepEqual(permittedOrderActions({ ...row, status: 1, payStatus: 0 }, action => action === 'order:refund'), [])
})
test('successful refund invalidates an in-flight pre-refund order read', async () => {
  let resolveRead!: (value: unknown) => void, gets = 0
  const client = {
    get: async () => { gets++; return gets === 1 ? new Promise(resolve => { resolveRead = resolve }) : { data: { code: 1, data: { id: '1', refundStatus: 'REQUESTED' } } } },
    post: async () => ({ data: { code: 1 } }),
  } as unknown as AxiosInstance
  const api = createOrderApi(client), pending = api.getDetail('1')
  await api.refund('1', '顾客申请')
  resolveRead({ data: { code: 1, data: { id: '1', refundStatus: null } } })
  assert.equal((await pending).data.data.refundStatus, 'REQUESTED')
  assert.equal(gets, 2)
})
