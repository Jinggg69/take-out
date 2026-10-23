import { reactive } from 'vue'
import type { OrderApi, OrderRecord } from '../../api/order.ts'
import { buildOrderQuery } from '../../utils/order.ts'
import { errorMessage } from '../../utils/error-message.ts'
export function createOrderList(api: Pick<OrderApi, 'getPage'>) {
  const state = reactive({ number: '', phone: '', dates: [] as string[] | null, status: 0, page: 1, pageSize: 10, records: [] as OrderRecord[], total: 0, loading: false, error: '', isSearch: false })
  let serial = 0; let disposed = false
  async function load(): Promise<void> {
    if (disposed) return
    const request = ++serial; state.loading = true; state.error = ''
    try {
      const { data } = await api.getPage(buildOrderQuery(state))
      if (disposed || request !== serial) return
      if (String(data.code) !== '1') throw new Error(data.msg || '订单查询失败')
      const total = Number(data.data?.total)
      if (!Array.isArray(data.data?.records) || !Number.isFinite(total) || total < 0) throw new Error('订单列表数据异常，请重新加载')
      state.records = data.data.records; state.total = total
      const maxPage = Math.max(1, Math.ceil(total / state.pageSize))
      if (state.page > maxPage) { state.page = maxPage; await load() }
    } catch (error) { if (!disposed && request === serial) state.error = errorMessage(error, '订单加载失败，请重试') }
    finally { if (!disposed && request === serial) state.loading = false }
  }
  function resetFilters() { state.number = ''; state.phone = ''; state.dates = [] }
  return { state, load, search: () => { state.page = 1; state.isSearch = true; return load() }, setPage: (page: number) => { state.page = page; return load() }, setPageSize: (size: number) => { state.pageSize = size; return load() }, setStatus: (status: number) => { state.status = status; state.page = 1; resetFilters(); state.isSearch = false; return load() }, clearFilters: () => { resetFilters(); state.page = 1; state.isSearch = false; return load() }, dispose: () => { disposed = true; serial++; state.loading = false } }
}
