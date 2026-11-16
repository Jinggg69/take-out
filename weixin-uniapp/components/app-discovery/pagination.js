import { ref } from 'vue'

// A generation prevents a slow search or old merchant response replacing a newer one.
export function usePagination(fetchPage) {
  const records = ref([]), loading = ref(false), error = ref(''), total = ref(0)
  let page = 0, generation = 0
  async function load(reset = false) {
    if (!reset && (loading.value || (page > 0 && records.value.length >= total.value))) return
    if (reset) { generation++; page = 0; records.value = []; total.value = 0 }
    const current = generation, next = page + 1
    loading.value = true; error.value = ''
    try {
      const result = await fetchPage({ page: next, pageSize: 10 })
      if (current !== generation) return
      if (result.code !== 1) throw new Error(result.msg || '加载失败，请重试')
      records.value = reset ? result.data.records : records.value.concat(result.data.records)
      total.value = Number(result.data.total); page = next
    } catch (e) { if (current === generation) error.value = e.message || e.msg || '网络异常，请重试' }
    finally { if (current === generation) loading.value = false }
  }
  return { records, loading, error, total, load }
}
