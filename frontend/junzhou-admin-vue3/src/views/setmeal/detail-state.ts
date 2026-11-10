import { reactive } from 'vue'
import type { SetmealApi, SetmealDish, SetmealId } from '../../api/setmeal.ts'
import { errorMessage } from '../../utils/error-message.ts'
import { setmealResultMessage } from '../../utils/setmeal.ts'

export function createSetmealDetail(api: Pick<SetmealApi, 'getById'>) {
  const state = reactive({ name: '', dishes: [] as SetmealDish[], loading: false, error: '' })
  let active = true
  let version = 0

  async function load(id: SetmealId): Promise<void> {
    if (!active) return
    const current = ++version
    state.name = ''; state.dishes = []; state.loading = true; state.error = ''
    try {
      const { data } = await api.getById(id)
      if (!active || current !== version) return
      if (String(data.code) !== '1') throw new Error(setmealResultMessage(data, '套餐菜品明细加载失败，请重试'))
      const detail = data.data
      if (!detail || typeof detail.name !== 'string' || !Array.isArray(detail.setmealDishes)
        || !detail.setmealDishes.every(validDish)) throw new Error('套餐菜品数据异常，请重新加载或联系管理员')
      state.name = detail.name
      state.dishes = detail.setmealDishes.map(dish => ({ ...dish }))
    } catch (cause) {
      if (active && current === version) state.error = errorMessage(cause, '套餐菜品明细加载失败，请检查网络后重试')
    } finally {
      if (active && current === version) state.loading = false
    }
  }

  function dispose() { active = false; version++; state.loading = false }
  return { state, load, dispose }
}

function validDish(value: unknown): value is SetmealDish {
  if (!value || typeof value !== 'object') return false
  const dish = value as Partial<SetmealDish>
  return (typeof dish.dishId === 'string' && dish.dishId.trim() !== '' || typeof dish.dishId === 'number' && Number.isFinite(dish.dishId))
    && typeof dish.name === 'string' && dish.name.trim() !== ''
    && (typeof dish.price === 'number' || typeof dish.price === 'string' && dish.price.trim() !== '')
    && Number.isFinite(Number(dish.price)) && Number(dish.price) >= 0
    && typeof dish.copies === 'number' && Number.isInteger(dish.copies) && dish.copies > 0
}
