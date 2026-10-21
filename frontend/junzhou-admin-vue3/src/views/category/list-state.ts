import { reactive } from 'vue'
import type { CategoryApi, CategoryRecord, CategoryType } from '../../api/category.ts'
import { errorMessage } from '../../utils/error-message.ts'
import { categoryResultMessage } from '../../utils/category.ts'

export function createCategoryList(api: Pick<CategoryApi, 'getPage'>) {
  const state = reactive({ records: [] as CategoryRecord[], total: 0, page: 1, pageSize: 10, name: '', type: '' as CategoryType | '', isSearch: false, loading: false, error: '' })
  let active = true
  let version = 0
  async function load(): Promise<void> {
    if (!active) return
    const current = ++version
    state.loading = true; state.error = ''; state.isSearch = Boolean(state.name || state.type)
    const params = { page: state.page, pageSize: state.pageSize, name: state.name || undefined, type: state.type || undefined }
    try {
      const { data } = await api.getPage(params)
      if (!active || current !== version) return
      if (String(data.code) !== '1') throw new Error(categoryResultMessage(data, '分类列表加载失败，请重试'))
      const total = Number(data.data?.total)
      if (!data.data || !Array.isArray(data.data.records) || !Number.isFinite(total) || total < 0) throw new Error('分类列表数据异常，请重试')
      const lastPage = Math.max(1, Math.ceil(total / params.pageSize))
      if (!data.data.records.length && params.page > lastPage) {
        state.page = lastPage
        await load()
        return
      }
      state.records = data.data.records; state.total = total
    } catch (error) {
      if (active && current === version) state.error = errorMessage(error, '分类列表加载失败，请检查网络后重试')
    } finally { if (active && current === version) state.loading = false }
  }
  async function clearName() { state.name = ''; await load() }
  async function clearType() { state.type = ''; await load() }
  async function clearFilters() { state.name = ''; state.type = ''; await load() }
  async function setPage(page: number) { state.page = page; await load() }
  async function setPageSize(size: number) { state.pageSize = size; await load() }
  function dispose() { active = false; version += 1; state.loading = false }
  return { state, load, search: load, clearName, clearType, clearFilters, setPage, setPageSize, dispose }
}
