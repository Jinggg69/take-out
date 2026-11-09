import assert from 'node:assert/strict'
import { test } from 'node:test'
import { orderBill } from '../src/utils/order.ts'

test('order bill uses the immutable pricing snapshot rather than legacy packing count and fixed delivery', () => {
  assert.deepEqual(orderBill({ amount: 57, packAmount: 3, pricingSnapshot: JSON.stringify({ goodsAmount: 45, packFee: 6, deliveryFee: 0, amount: 51 }) }), { goodsAmount: 45, packFee: 6, deliveryFee: 0, amount: 51 })
})
test('legacy orders and malformed snapshots retain the original fee breakdown', () => {
  for (const pricingSnapshot of [undefined, null, '', '{bad', 'null', '[]', '7']) {
    assert.deepEqual(orderBill({ amount: '30', packAmount: '2', pricingSnapshot }), { goodsAmount: 22, packFee: 2, deliveryFee: 6, amount: 30 })
  }
})
test('valid zero and decimal string fields survive while invalid snapshot fields use fallback', () => {
  assert.deepEqual(orderBill({ amount: 30, packAmount: 2, pricingSnapshot: JSON.stringify({ goodsAmount: '0.00', packFee: '1.50', deliveryFee: 0, amount: '1.50' }) }), { goodsAmount: 0, packFee: 1.5, deliveryFee: 0, amount: 1.5 })
  assert.deepEqual(orderBill({ amount: 30, packAmount: 2, pricingSnapshot: JSON.stringify({ goodsAmount: null, packFee: -1, deliveryFee: false, amount: 'invalid' }) }), { goodsAmount: 22, packFee: 2, deliveryFee: 6, amount: 30 })
  assert.deepEqual(orderBill({ amount: 30, packAmount: 2, pricingSnapshot: JSON.stringify({ packFee: 4, deliveryFee: 0 }) }), { goodsAmount: 26, packFee: 4, deliveryFee: 0, amount: 30 })
})
