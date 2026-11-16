import { reactive } from 'vue'
import type { PlatformApi, PlatformRecord } from '../../api/platform.ts'
import { errorMessage } from '../../utils/error-message.ts'

export function createPlatformList(api: Pick<PlatformApi, 'getPage'>) {
  const state = reactive({ records: [] as PlatformRecord[], total: 0, page: 1, pageSize: 10, keyword: '', tenantId: '', loading: false, error: '' })
  let active = true, version = 0
  async function load(): Promise<void> {
    if (!active) return
    const current = ++version
    state.loading = true; state.error = ''
    try {
      const { data } = await api.getPage({ page: state.page, pageSize: state.pageSize, keyword: state.keyword.trim() || undefined, tenantId: state.tenantId || undefined })
      if (!active || version !== current) return
      if (String(data.code) !== '1') throw new Error(data.msg || '列表加载失败，请重试')
      if (!data.data || !Array.isArray(data.data.records) || !Number.isFinite(Number(data.data.total))) throw new Error('列表数据异常，请重试')
      const lastPage = Math.max(1, Math.ceil(Number(data.data.total) / state.pageSize))
      if (state.page > lastPage) { state.page = lastPage; await load(); return }
      state.records = data.data.records; state.total = Number(data.data.total)
    } catch (error) { if (active && version === current) state.error = errorMessage(error, '列表加载失败，请检查网络后重试') }
    finally { if (active && version === current) state.loading = false }
  }
  function dispose() { active = false; version++; state.loading = false }
  return { state, load, dispose }
}
