import { reactive } from 'vue'
import type { ApiResult } from '../../api/types.ts'
import type { StatisticsRange, TurnoverReport, UserReport, OrderReport, RankingReport } from '../../api/statistics.ts'
interface Section<T> { data: T | null; loading: boolean; error: string }
function section<T>(): Section<T> { return { data: null, loading: false, error: '' } }
type Response<T> = Promise<{ data: ApiResult<T | null> }>
interface Reader {
  getTurnover: (range: StatisticsRange) => Response<TurnoverReport>
  getUsers: (range: StatisticsRange) => Response<UserReport>
  getOrders: (range: StatisticsRange) => Response<OrderReport>
  getTop10: (range: StatisticsRange) => Response<RankingReport>
}
export type ReportKey = 'turnover' | 'users' | 'orders' | 'top10'
export function createStatisticsState(api: Reader) {
  const state = reactive({ turnover: section<TurnoverReport>(), users: section<UserReport>(), orders: section<OrderReport>(), top10: section<RankingReport>() })
  let active = true; let generation = 0; let range: StatisticsRange | null = null
  const versions = { turnover: 0, users: 0, orders: 0, top10: 0 }
  async function fetchSection<T>(key: ReportKey, target: Section<T>, read: (params: StatisticsRange) => Response<T>) {
    if (!active || !range) return
    const current = generation; const version = ++versions[key]
    target.loading = true; target.error = ''; target.data = null
    try {
      const response = await read({ ...range })
      if (!active || current !== generation || version !== versions[key]) return
      if (String(response.data.code) !== '1') throw new Error(response.data.msg || '统计读取失败，请重试')
      if (!response.data.data || typeof response.data.data !== 'object') throw new Error('统计数据格式异常，请重试')
      target.data = response.data.data
    } catch (error) {
      if (active && current === generation && version === versions[key]) target.error = error instanceof Error ? error.message : '统计读取失败，请重试'
    } finally { if (active && current === generation && version === versions[key]) target.loading = false }
  }
  function retry(key: ReportKey) {
    switch (key) {
      case 'turnover': return fetchSection(key, state.turnover, api.getTurnover)
      case 'users': return fetchSection(key, state.users, api.getUsers)
      case 'orders': return fetchSection(key, state.orders, api.getOrders)
      case 'top10': return fetchSection(key, state.top10, api.getTop10)
    }
  }
  return { state, retry, async load(next: StatisticsRange) { if (!active) return; range = { ...next }; generation++; await Promise.all((['turnover', 'users', 'orders', 'top10'] as const).map(retry)) }, dispose() { active = false; generation++ } }
}
