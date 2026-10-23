import { reactive } from 'vue'
import type { DishApi, DishId, DishQuery, DishRecord } from '../../api/dish.ts'
import { errorMessage } from '../../utils/error-message.ts'
import { dishResultMessage } from '../../utils/dish.ts'

export function createDishList(api: Pick<DishApi, 'getPage'>) {
  const state = reactive({ records: [] as DishRecord[], total: 0, page: 1, pageSize: 10, name: '', categoryId: '' as DishId, status: '' as DishQuery['status'], isSearch: false, loading: false, error: '' })
  let active = true; let version = 0
  async function load(): Promise<void> {
    if (!active) return
    const current = ++version
    state.loading = true; state.error = ''; state.isSearch = Boolean(state.name || state.categoryId || state.status !== '')
    const params: DishQuery = { page: state.page, pageSize: state.pageSize, name: state.name || undefined, categoryId: state.categoryId || undefined, status: state.status }
    try {
      const { data } = await api.getPage(params)
      if (!active || current !== version) return
      if (String(data.code) !== '1') throw new Error(dishResultMessage(data, '菜品列表加载失败，请重试'))
      const total = Number(data.data?.total)
      if (!data.data || !Array.isArray(data.data.records) || !Number.isFinite(total) || total < 0) throw new Error('菜品列表数据异常，请重试')
      const last = Math.max(1, Math.ceil(total / params.pageSize))
      if (!data.data.records.length && params.page > last) { state.page = last; await load(); return }
      state.records = data.data.records; state.total = total
    } catch (error) { if (active && current === version) state.error = errorMessage(error, '菜品列表加载失败，请检查网络后重试') }
    finally { if (active && current === version) state.loading = false }
  }
  async function search() { state.page = 1; await load() }
  async function clearName() { state.name = ''; await load() }
  async function clearCategory() { state.categoryId = ''; await load() }
  async function clearStatus() { state.status = ''; await load() }
  async function clearFilters() { state.name = ''; state.categoryId = ''; state.status = ''; await load() }
  async function setPage(page: number) { state.page = page; await load() }
  async function setPageSize(size: number) { state.pageSize = size; await load() }
  function dispose() { active = false; version++; state.loading = false }
  return { state, load, search, clearName, clearCategory, clearStatus, clearFilters, setPage, setPageSize, dispose }
}
