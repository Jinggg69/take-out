import { reactive } from 'vue'
import type { DishApi, DishId, DishRecord } from '../../api/dish.ts'
import type { EditableFlavor } from '../../utils/dish.ts'
import { dishFormFromDetail, dishResultMessage } from '../../utils/dish.ts'
import { errorMessage } from '../../utils/error-message.ts'

export function createDishDetail(api: Pick<DishApi, 'getById'>) {
  const state = reactive({ record: null as DishRecord | null, flavors: [] as EditableFlavor[], loading: false, error: '' })
  let active = true
  let version = 0

  async function load(id: DishId): Promise<void> {
    if (!active) return
    const current = ++version
    state.record = null; state.flavors = []; state.loading = true; state.error = ''
    try {
      const { data } = await api.getById(id)
      if (!active || current !== version) return
      if (String(data.code) !== '1') throw new Error(dishResultMessage(data, '菜品详情加载失败，请重试'))
      const record = data.data
      if (!record || typeof record.name !== 'string' || !record.name.trim()) throw new Error('菜品详情数据异常，请重新加载或联系管理员')
      let flavors: EditableFlavor[]
      try {
        if (record.flavors != null && (!Array.isArray(record.flavors)
          || record.flavors.some(flavor => !flavor || typeof flavor.name !== 'string' || typeof flavor.value !== 'string'))) throw new Error('invalid flavors')
        flavors = dishFormFromDetail(record).flavors
      } catch { throw new Error('菜品口味数据异常，请重新加载或联系管理员') }
      state.record = { ...record }
      state.flavors = flavors
    } catch (cause) {
      if (active && current === version) state.error = errorMessage(cause, '菜品详情加载失败，请检查网络后重试')
    } finally {
      if (active && current === version) state.loading = false
    }
  }

  function dispose() { active = false; version++; state.loading = false }
  return { state, load, dispose }
}
