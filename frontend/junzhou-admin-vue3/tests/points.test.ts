import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createPointsApi, ledgerExportError, pointsGrantPayload, setPointsSelection, type PointsAccount } from '../src/api/points.ts'
import { sidebarNavigation } from '../src/router/navigation.ts'
import { canVisit, workspace } from '../src/stores/workspace.ts'

const member = (id: string): PointsAccount => ({ id, memberNo: id, status: 1, availablePoints: '0', frozenPoints: 0, pointsDebt: '0' })

test('积分管理须有商户级会员读取权限', () => {
  const store = { id: '1', name: '门店', permissions: ['member:read'] }
  try {
    workspace.load([store])
    assert.equal(canVisit('/points?tab=ledger'), false)
    workspace.load([store], undefined, ['member:read'])
    assert.equal(canVisit('/points?tab=ledger'), true)
    assert.ok(sidebarNavigation(canVisit).some(item => item.path === '/points' && !item.children))
    assert.ok(!sidebarNavigation(canVisit).some(item => item.children?.map(child => String(child.path)).includes('/member/points')))
  } finally { workspace.reset() }
})

test('跨页选择支持去重和取消，拒绝停用会员及第101名会员', () => {
  const selected = new Map(Array.from({ length: 99 }, (_, index) => [String(index), member(String(index))]))
  const full = setPointsSelection(selected, member('99'), true)
  assert.equal(full.size, 100)
  assert.equal(setPointsSelection(full, member('99'), true).size, 100)
  assert.equal(setPointsSelection(full, member('99'), false).size, 99)
  assert.throws(() => setPointsSelection(full, member('100'), true), /100/)
  assert.throws(() => setPointsSelection(selected, { ...member('100'), status: 0 }, true), /启用/)
})

test('批量赠分校验整数和原因，重试使用确定的名单顺序', () => {
  assert.deepEqual(pointsGrantPayload(['2', '1'], '40', ' 赠送原因 ', 'test-1'), {
    memberIds: ['1', '2'], points: 40, reason: '赠送原因', requestId: 'test-1',
  })
  for (const points of ['0', '-1', '1.5', '9007199254740991']) assert.throws(() => pointsGrantPayload(['1', '2'], points, '原因', 'test-1'), /整数/)
  assert.throws(() => pointsGrantPayload(['1'], '1', '   ', 'test-1'), /原因/)
})

test('流水导出最多包含31个自然日', () => {
  assert.equal(ledgerExportError({ startDate: '2026-10-01', endDate: '2026-10-31' }), '')
  assert.match(ledgerExportError({ startDate: '2026-10-01', endDate: '2026-11-01' }), /31/)
  assert.match(ledgerExportError({}), /日期/)
})

test('积分导出按所选筛选条件下载全部结果，批量赠分传递请求号', async () => {
  const calls: unknown[][] = []
  const client = { get: (...args: unknown[]) => calls.push(args), post: (...args: unknown[]) => calls.push(args) }
  const api = createPointsApi(client as unknown as Parameters<typeof createPointsApi>[0])
  await api.export({ businessNo: 'B2026', startDate: '2026-10-01', endDate: '2026-10-31' })
  await api.grant({ memberIds: ['1'], points: 1, reason: '原因', requestId: 'test-1' })
  assert.deepEqual(calls, [
    ['/member/points/export', { params: { businessNo: 'B2026', startDate: '2026-10-01', endDate: '2026-10-31' }, responseType: 'blob' }],
    ['/member/points/batch', { memberIds: ['1'], points: 1, reason: '原因', requestId: 'test-1' }],
  ])
})
