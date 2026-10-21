import { reactive } from 'vue'
import type { EmployeeApi, EmployeeRecord } from '../../api/employee.ts'
import { errorMessage } from '../../utils/error-message.ts'

export function createEmployeeList(api: Pick<EmployeeApi, 'getList'>) {
  const state = reactive({ records: [] as EmployeeRecord[], total: 0, page: 1, pageSize: 10, name: '', isSearch: false, loading: false, error: '' })
  let version = 0
  let active = true
  async function load() {
    if (!active) return
    const current = ++version
    state.loading = true; state.error = ''
    state.isSearch = Boolean(state.name)
    try {
      const { data } = await api.getList({ page: state.page, pageSize: state.pageSize, name: state.name || undefined })
      if (!active || current !== version) return
      if (String(data.code) !== '1') throw new Error(data.msg || '员工列表加载失败，请重试')
      if (!data.data || !Array.isArray(data.data.records) || !Number.isFinite(data.data.total)) throw new Error('员工列表数据异常，请重试')
      state.records = data.data.records; state.total = data.data.total
    } catch (error) {
      if (active && current === version) state.error = errorMessage(error, '员工列表加载失败，请检查网络后重试')
    } finally { if (active && current === version) state.loading = false }
  }
  async function search(resetPage = false) { if (resetPage) state.page = 1; await load() }
  async function clearSearch() { state.name = ''; await load() }
  async function setPage(page: number) { state.page = page; await load() }
  async function setPageSize(size: number) { state.pageSize = size; await load() }
  function dispose() { active = false; version += 1; state.loading = false }
  return { state, load, search, clearSearch, setPage, setPageSize, dispose }
}
