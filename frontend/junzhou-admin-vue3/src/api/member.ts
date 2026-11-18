import type { AxiosInstance } from 'axios'
import type { ApiResult } from './types.ts'

export type MemberRow = Record<string, unknown> & { id: string; name?: string; status?: number; version?: number }
export interface MemberPage { records: MemberRow[]; total: number }
export interface MemberDetail { member: MemberRow; orders: MemberRow[]; points: MemberRow[]; wallet: MemberRow[]; coupons: MemberRow[] }
export type MemberResource = 'levels' | 'tags' | 'recharge-plans' | 'coupons'
export type MemberLedger = 'points' | 'wallet' | 'recharges'
export type MemberBody = Record<string, unknown>
export function createMemberApi(client: AxiosInstance) {
  const idPath = (id: string) => encodeURIComponent(id)
  return {
    page: (params: MemberBody) => client.get<ApiResult<MemberPage>>('/member/page', { params }),
    detail: (id: string) => client.get<ApiResult<MemberDetail>>(`/member/${idPath(id)}`),
    saveMember: (id: string | undefined, body: MemberBody) => id ? client.put<ApiResult<MemberRow>>(`/member/${idPath(id)}`, body) : client.post<ApiResult<MemberRow>>('/member', body),
    status: (id: string, status: number, version?: number) => client.post<ApiResult<MemberRow>>(`/member/${idPath(id)}/status`, { status, version }),
    adjust: (id: string, kind: 'points' | 'balance', body: MemberBody) => client.post<ApiResult<MemberRow>>(`/member/${idPath(id)}/${kind}`, body),
    list: (kind: MemberResource) => client.get<ApiResult<MemberRow[]>>(`/member/${kind}`),
    save: (kind: MemberResource, id: string | undefined, body: MemberBody) => id ? client.put<ApiResult<MemberRow>>(`/member/${kind}/${idPath(id)}`, body) : client.post<ApiResult<MemberRow>>(`/member/${kind}`, body),
    ledger: (kind: MemberLedger, params: MemberBody) => client.get<ApiResult<MemberPage>>(`/member/${kind}`, { params }),
    settings: () => client.get<ApiResult<MemberBody>>('/member/settings'),
    saveSettings: (body: MemberBody) => client.put<ApiResult<MemberBody>>('/member/settings', body),
    issue: (id: string, memberIds: string[], requestId: string) => client.post<ApiResult<unknown>>(`/member/coupons/${idPath(id)}/issue`, { memberIds, requestId }),
    refund: (id: string, reason: string, requestId: string) => client.post<ApiResult<MemberRow>>(`/member/recharges/${idPath(id)}/refund`, { reason, requestId }),
  }
}
