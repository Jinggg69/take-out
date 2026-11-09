import assert from 'node:assert/strict'
import { test } from 'node:test'
import { settingsDraft, settingsPayload, menuPayload } from '../src/utils/store-settings.ts'

const settings = { version: 3, address: '门店地址', latitude: 32.5, longitude: 111.5, opensAt: '00:00', closesAt: '00:00', deliveryRadiusMeters: 5000, minimumOrderAmount: 0, packFeePerItem: 1, deliveryFee: 6, freeDeliveryThreshold: null }
test('settings preserve optimistic version, zero fees and optional free delivery', () => {
  assert.deepEqual(settingsPayload(settingsDraft(settings)), settings)
  assert.equal(settingsPayload({ ...settingsDraft(settings), freeDeliveryThreshold: '0' }).freeDeliveryThreshold, 0)
})
test('coordinates must be paired, in range and provided even for legacy stores', () => {
  const draft = settingsDraft(settings)
  assert.throws(() => settingsPayload({ ...draft, latitude: '', longitude: '' }), /坐标/)
  assert.throws(() => settingsPayload({ ...draft, latitude: '32.1234567' }), /六位/)
  assert.throws(() => settingsPayload({ ...draft, latitude: '' }), /坐标/)
  assert.throws(() => settingsPayload({ ...draft, longitude: '181' }), /经度/)
})
test('invalid times, money and noninteger radius cannot be saved', () => {
  const draft = settingsDraft(settings)
  for (const change of [{ opensAt: '25:00' }, { deliveryFee: '-1' }, { minimumOrderAmount: '1.001' }, { deliveryRadiusMeters: '0' }, { deliveryRadiusMeters: '1.2' }, { address: '   ' }]) {
    assert.throws(() => settingsPayload({ ...draft, ...change }))
  }
})
test('menu empty override inherits catalog price and explicit zero stays zero', () => {
  const row = { id: '1', onSale: 1 as const, soldOut: 0 as const, overridePrice: '', sort: 0 }
  assert.equal(menuPayload(row).overridePrice, null)
  assert.equal(menuPayload({ ...row, overridePrice: '0' }).overridePrice, 0)
  assert.throws(() => menuPayload({ ...row, overridePrice: '-1' }))
  assert.throws(() => menuPayload({ ...row, sort: 1.5 }))
})
