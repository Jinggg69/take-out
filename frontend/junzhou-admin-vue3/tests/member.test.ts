import assert from 'node:assert/strict'
import { test } from 'node:test'
import type { AxiosInstance } from 'axios'
import { createMemberApi } from '../src/api/member.ts'
import { adjustmentPayload, memberPayload, resourcePayload, serverDate, settingsPayload } from '../src/views/member/forms.ts'
import { sidebarNavigation, breadcrumbsFor } from '../src/router/navigation.ts'

test('membership writes scope IDs in URL and whitelist editable fields', async () => {
  const calls: unknown[][] = []
  const client = { put: async (...args: unknown[]) => { calls.push(args); return { data: { code: 1 } } } } as unknown as AxiosInstance
  await createMemberApi(client).saveMember('9007199254740993', memberPayload({ name: ' 张三 ', phone: '13800138000', tagIds: ['22'], version: 4, tenantId: 'evil', balance: 100 }))
  assert.deepEqual(calls[0], ['/member/9007199254740993', { name: '张三', phone: '13800138000', birthday: '', remark: '', levelId: undefined, tagIds: ['22'], version: 4 }])
})
test('asset adjustments require nonzero precise values, reasons and stable operation ID', () => {
  assert.deepEqual(adjustmentPayload('balance', '-1.20', ' 冲正 ', 'retry-key'), { amount: -1.2, reason: '冲正', requestId: 'retry-key' })
  for (const value of ['0', '1.001', 'Infinity', '1e3']) assert.throws(() => adjustmentPayload('balance', value, '调整', 'key'))
  assert.throws(() => adjustmentPayload('points', '1.5', '调整', 'key'))
  assert.throws(() => adjustmentPayload('points', '10', ' ', 'key'))
})
test('coupon boundaries and server date shape are validated before sending', () => {
  const draft = { name: '测试券', type: 'FIXED', value: 10, minSpend: 20, startTime: '2026-11-17 10:00:00', endTime: '2026-11-18 10:00:00', totalStock: 100, perMemberLimit: 1, pointsCost: 0, storeIds: [], fulfillmentTypes: ['TAKEAWAY'], status: 1 }
  assert.equal(resourcePayload('coupons', draft).value, 10)
  assert.throws(() => resourcePayload('coupons', { ...draft, endTime: draft.startTime }))
  assert.throws(() => resourcePayload('coupons', { ...draft, type: 'DISCOUNT', value: 101 }))
  assert.throws(() => resourcePayload('coupons', { ...draft, perMemberLimit: 0 }))
  assert.throws(() => resourcePayload('recharge-plans', { name: '套餐', amount: 0, giftAmount: 0, status: 1 }))
})
test('configuration edits preserve version but drop tenant, asset and server metadata', () => {
  assert.deepEqual(resourcePayload('levels', { id: '1', tenantId: 'other', name: '银卡', threshold: 500, discountPercent: 100, status: 1, version: 9, balance: 200 }), { name: '银卡', threshold: 500, discountPercent: 100, status: 1, version: 9 })
  assert.equal(serverDate('2026-11-17T10:20'), '2026-11-17 10:20:00')
  assert.deepEqual(settingsPayload({ earnPerYuan: 1, pointsPerYuan: 100, maxDeductionPercent: 10, allowCouponWithPoints: true, allowCouponWithLevel: false, version: 3, tenantId: 'other' }), { earnPerYuan: 1, pointsPerYuan: 100, maxDeductionPercent: 10, allowCouponWithPoints: true, allowCouponWithLevel: false, version: 3 })
  assert.throws(() => settingsPayload({ earnPerYuan: 1, pointsPerYuan: 0, maxDeductionPercent: 10 }))
})
test('membership navigation keeps permitted children without needing list permission', () => {
  const nav = sidebarNavigation(path => path === '/member/coupons')
  assert.deepEqual(nav.map(row => row.label), ['会员管理'])
  assert.deepEqual(nav[0]?.children?.map(row => row.path), ['/member/coupons'])
  assert.deepEqual(breadcrumbsFor('/member/coupons', '优惠券', () => false), [{ label: '会员管理' }, { label: '优惠券' }])
})
test('asset, refund and issue transport use explicit operation IDs without converting long IDs', async () => {
  const calls: unknown[][] = []
  const client = { post: async (...args: unknown[]) => { calls.push(args); return { data: { code: 1 } } } } as unknown as AxiosInstance
  const api = createMemberApi(client)
  await api.adjust('9007199254740993', 'points', adjustmentPayload('points', 12, '补记', 'point-key'))
  await api.issue('9007199254740994', ['9007199254740993'], 'issue-key')
  await api.refund('9007199254740995', '顾客申请', 'refund-key')
  assert.deepEqual(calls, [
    ['/member/9007199254740993/points', { delta: 12, reason: '补记', requestId: 'point-key' }],
    ['/member/coupons/9007199254740994/issue', { memberIds: ['9007199254740993'], requestId: 'issue-key' }],
    ['/member/recharges/9007199254740995/refund', { reason: '顾客申请', requestId: 'refund-key' }],
  ])
})
