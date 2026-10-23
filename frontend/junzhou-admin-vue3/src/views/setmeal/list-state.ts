import { reactive } from 'vue'
import type { SetmealApi, SetmealId, SetmealQuery, SetmealRecord } from '../../api/setmeal.ts'
import { errorMessage } from '../../utils/error-message.ts'
import { setmealResultMessage } from '../../utils/setmeal.ts'

export function createSetmealList(api: Pick<SetmealApi, 'getPage'>) {
  const state = reactive({ records: [] as SetmealRecord[], total: 0, page: 1, pageSize: 10, name: '', categoryId: '' as SetmealId, status: '' as SetmealQuery['status'], isSearch: false, loading: false, error: '' })
  let active = true; let version = 0
  async function load(): Promise<void> {
    if (!active) return
    const current = ++version
    state.loading = true; state.error = ''; state.isSearch = Boolean(state.name || state.categoryId || state.status !== '')
    const params: SetmealQuery = { page: state.page, pageSize: state.pageSize, name: state.name || undefined, categoryId: state.categoryId || undefined, status: state.status }
    try {
      const { data } = await api.getPage(params)
      if (!active || current !== version) return
      if (String(data.code) !== '1') throw new Error(setmealResultMessage(data, '套餐列表加载失败，请重试'))
      const total = Number(data.data?.total)
      if (!data.data || !Array.isArray(data.data.records) || !Number.isFinite(total) || total < 0) throw new Error('套餐列表数据异常，请重试')
      const last = Math.max(1, Math.ceil(total / params.pageSize))
      if (!data.data.records.length && params.page > last) { state.page = last; await load(); return }
      state.records = data.data.records; state.total = total
    } catch (error) { if (active && current === version) state.error = errorMessage(error, '套餐列表加载失败，请检查网络后重试') }
    finally { if (active && current === version) state.loading = false }
  }
  async function search() { state.page = 1; await load() }
  async function clearName() { state.name = ''; await search() }
  async function clearCategory() { state.categoryId = ''; await search() }
  async function clearStatus() { state.status = ''; await search() }
  async function clearFilters() { state.name = ''; state.categoryId = ''; state.status = ''; await search() }
  async function setPage(page: number) { state.page = page; await load() }
  async function setPageSize(size: number) { state.pageSize = size; await search() }
  function dispose() { active = false; version++; state.loading = false }
  return { state, load, search, clearName, clearCategory, clearStatus, clearFilters, setPage, setPageSize, dispose }
}
