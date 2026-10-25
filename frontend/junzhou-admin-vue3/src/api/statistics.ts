import type { AxiosInstance } from 'axios'
import type { ApiResult } from './types.ts'
export interface StatisticsRange { begin: string; end: string }
export interface TurnoverReport { dateList: string; turnoverList: string }
export interface UserReport { dateList: string; totalUserList: string; newUserList: string }
export interface OrderReport { dateList: string; orderCountList: string; validOrderCountList: string; totalOrderCount: number; validOrderCount: number; orderCompletionRate: number }
export interface RankingReport { nameList: string; numberList: string }
export function createStatisticsApi(client: AxiosInstance) {
  const pending = new Map<string, Promise<unknown>>()
  function read<T>(url: string, params: StatisticsRange) {
    const key = JSON.stringify([url, params.begin, params.end])
    const existing = pending.get(key)
    if (existing) return existing as Promise<{ data: ApiResult<T> }>
    const request = client.get<ApiResult<T>>(url, { params: { ...params } }).finally(() => pending.delete(key))
    pending.set(key, request)
    return request
  }
  return {
    getTurnover: (params: StatisticsRange) => read<TurnoverReport>('/report/turnoverStatistics', params),
    getUsers: (params: StatisticsRange) => read<UserReport>('/report/userStatistics', params),
    getOrders: (params: StatisticsRange) => read<OrderReport>('/report/ordersStatistics', params),
    getTop10: (params: StatisticsRange) => read<RankingReport>('/report/top10', params),
    export: () => client.get<Blob>('/report/export', { responseType: 'blob' }),
  }
}
export type StatisticsApi = ReturnType<typeof createStatisticsApi>
