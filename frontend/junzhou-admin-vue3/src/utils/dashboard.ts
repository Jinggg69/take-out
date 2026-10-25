import { shallowReactive } from 'vue'
import type { ApiResult } from '../api/types.ts'
import { errorMessage } from './error-message.ts'

export const dashboardOrderLinks = [
  { key: 'waitingOrders', label: '待接单', status: 2 },
  { key: 'deliveredOrders', label: '待派送', status: 3 },
  { key: 'completedOrders', label: '已完成', status: 5 },
  { key: 'cancelledOrders', label: '已取消', status: 6 },
  { key: 'allOrders', label: '全部订单', status: 0 },
] as const
function figure(value: unknown) {
  if (value == null || value === '' || typeof value === 'boolean') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}
export function dashboardCount(value: unknown) { const number = figure(value); return number == null ? '—' : String(number) }
export function dashboardMoney(value: unknown) { const number = figure(value); return number == null ? '—' : `￥${number.toFixed(2)}` }
export function dashboardRate(value: unknown) { const number = figure(value); return number == null ? '—' : `${(number * 100).toFixed(0)}%` }

/** Independent sections retain their own loading/error lifecycle; stale reads cannot replace a newer refresh. */
export function createDashboardResource<T>(read: () => Promise<{ data: ApiResult<T> }>) {
  const state = shallowReactive<{ data: T | null; loading: boolean; error: string }>({ data: null, loading: false, error: '' })
  let serial = 0; let disposed = false
  async function load() {
    if (disposed) return
    const request = ++serial
    state.loading = true; state.error = ''; state.data = null
    try {
      const { data } = await read()
      if (disposed || request !== serial) return
      if (String(data.code) !== '1' || !data.data || typeof data.data !== 'object' || Array.isArray(data.data)) throw new Error(data.msg || '数据读取失败，请重试')
      state.data = data.data
    } catch (error) { if (!disposed && request === serial) state.error = errorMessage(error, '数据读取失败，请重试') }
    finally { if (!disposed && request === serial) state.loading = false }
  }
  return { state, load, dispose: () => { disposed = true; serial++; state.loading = false } }
}
