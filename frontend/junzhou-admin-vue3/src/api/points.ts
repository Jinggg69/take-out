import type { AxiosInstance } from 'axios'
import type { ApiResult } from './types.ts'
import type { MemberPage, MemberRow } from './member.ts'

type PointValue = string | number
export interface PointsOverview {
  environment: 'MOCK' | 'WECHAT'
  availablePoints: PointValue
  frozenPoints: PointValue
  pointsDebt: PointValue
  creditedPoints: PointValue
  debitedPoints: PointValue
}
export interface PointsAccount extends MemberRow {
  memberNo: string
  status: number
  availablePoints: PointValue
  frozenPoints: PointValue
  pointsDebt: PointValue
}
interface PointsRange { startDate?: string; endDate?: string }
interface SearchPage { page: number; pageSize: number; keyword?: string }
export interface PointsAccountQuery extends SearchPage { levelId?: string; tagId?: string; status?: 0 | 1 }
export const pointsSources = {
  ORDER_EARN: '订单赠分', ORDER_DEDUCT: '订单抵扣', ORDER_REFUND: '订单退款',
  COUPON_EXCHANGE: '积分兑券', MANUAL: '人工调整', BATCH_GRANT: '批量赠分', OTHER: '其他',
} as const
export interface PointsLedgerFilters extends PointsRange {
  keyword?: string
  businessNo?: string
  storeId?: string
  source?: keyof typeof pointsSources
  assetType?: 'POINTS' | 'POINTS_DEBT'
}
export interface PointsGrant { memberIds: string[]; points: number; reason: string; requestId: string }
export interface PointsGrantResult { batchNo: string; memberCount: number; totalPoints: PointValue }

export const POINTS_BATCH_LIMIT = 100

export function setPointsSelection(selection: Map<string, PointsAccount>, member: PointsAccount, checked: boolean) {
  const next = new Map(selection)
  if (!checked) { next.delete(member.id); return next }
  if (member.status !== 1) throw new Error('只能选择启用中的会员')
  if (next.size >= POINTS_BATCH_LIMIT && !next.has(member.id)) throw new Error('每批最多选择 100 名会员')
  next.set(member.id, member)
  return next
}

export function pointsGrantPayload(ids: string[], value: string, reason: string, requestId: string): PointsGrant {
  const points = Number(value)
  if (!ids.length || ids.length > POINTS_BATCH_LIMIT || new Set(ids).size !== ids.length) throw new Error('请选择 1–100 名不同的会员')
  if (!/^\d+$/.test(value.trim()) || !Number.isSafeInteger(points) || points <= 0 || !Number.isSafeInteger(points * ids.length)) {
    throw new Error('请输入有效的正整数积分，发放总数不能超出范围')
  }
  const note = reason.trim()
  if (!note || [...note].length > 500) throw new Error('请填写 1–500 字的赠送原因')
  return { memberIds: [...ids].sort(), points, reason: note, requestId }
}

export function ledgerExportError(range: PointsRange): string {
  if (!range.startDate || !range.endDate) return '请选择需要导出的日期范围'
  const days = (Date.parse(range.endDate) - Date.parse(range.startDate)) / 86_400_000 + 1
  if (!Number.isFinite(days) || days < 1 || days > 31) return '每次最多导出 31 个自然日，请缩小日期范围'
  return ''
}

export function createPointsApi(client: AxiosInstance) {
  const base = '/member/points'
  return {
    overview: (params: PointsRange) => client.get<ApiResult<PointsOverview>>(`${base}/overview`, { params }),
    accounts: (params: PointsAccountQuery) => client.get<ApiResult<{ records: PointsAccount[]; total: number }>>(`${base}/accounts`, { params }),
    ledger: (params: PointsLedgerFilters & SearchPage) => client.get<ApiResult<MemberPage>>(base, { params }),
    export: (params: PointsLedgerFilters) => client.get<Blob>(`${base}/export`, { params, responseType: 'blob' }),
    grant: (body: PointsGrant) => client.post<ApiResult<PointsGrantResult>>(`${base}/batch`, body),
  }
}
