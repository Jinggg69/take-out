import type { DishRecord } from '../api/dish.ts'
import type { SetmealApi, SetmealCategory, SetmealDish, SetmealId, SetmealPayload, SetmealRecord } from '../api/setmeal.ts'

export interface SetmealForm {
  name: string; categoryId: SetmealId; price: string; image: string; description: string
  status: boolean; dishes: SetmealDish[]; metadata: Record<string, unknown>
}
export type SetmealField = 'name' | 'categoryId' | 'price' | 'image' | 'dishes'
export function createSetmealForm(): SetmealForm {
  return { name: '', categoryId: '', price: '', image: '', description: '', status: true, dishes: [], metadata: {} }
}
export function validateSetmeal(form: SetmealForm): Partial<Record<SetmealField, string>> {
  const errors: Partial<Record<SetmealField, string>> = {}
  if (!/^([A-Za-z0-9\u4e00-\u9fa5]){2,20}$/.test(form.name)) errors.name = form.name ? '套餐名称输入不符，请输入2-20个字符' : '请输入套餐名称'
  if (form.categoryId === '' || form.categoryId === null || form.categoryId === undefined) errors.categoryId = '请选择套餐分类'
  if (!/^([1-9]\d{0,5}|0)(\.\d{1,2})?$/.test(form.price) || Number(form.price) <= 0) errors.price = '套餐价格格式有误，请输入大于零且最多保留两位小数的金额'
  if (!form.image) errors.image = '套餐图片不能为空'
  if (!form.dishes.length) errors.dishes = '套餐下菜品不能为空'
  else if (form.dishes.some(dish => !Number.isInteger(dish.copies) || dish.copies < 1 || dish.copies > 99)) errors.dishes = '每道菜品份数须为1至99的整数'
  return errors
}
export function setmealFormFromDetail(detail: SetmealRecord): SetmealForm {
  if (!Array.isArray(detail.setmealDishes)) throw new Error('套餐菜品数据异常，请重新加载或联系管理员')
  return { name: detail.name, categoryId: detail.categoryId, price: String(detail.price), image: detail.image,
    description: detail.description ?? '', status: String(detail.status) === '1', dishes: detail.setmealDishes.map(dish => ({ ...dish })), metadata: { ...detail } }
}
export function setmealPayload(form: SetmealForm, editing: boolean): SetmealPayload {
  const payload: SetmealPayload = { ...(editing ? form.metadata : { code: '' }), name: form.name, price: form.price, image: form.image,
    description: form.description, status: editing && form.status ? 1 : 0, categoryId: form.categoryId,
    setmealDishes: form.dishes.map(({ dishId, name, price, copies }) => ({ dishId, name, price, copies })) }
  delete payload.updateTime
  if (!editing) delete payload.id
  return payload
}
export function toggleSetmealDish(selected: SetmealDish[], dish: DishRecord, checked: boolean): SetmealDish[] {
  const exists = selected.some(item => String(item.dishId) === String(dish.id))
  const copy = selected.map(item => ({ ...item }))
  return checked ? exists ? copy : [{ dishId: dish.id, name: dish.name, price: dish.price, copies: 1 }, ...copy]
    : copy.filter(item => String(item.dishId) !== String(dish.id))
}
export function nextSetmealStatus(status: string | number): 0 | 1 { return String(status) === '0' ? 1 : 0 }
export function formatSetmealPrice(price: unknown): string { return price !== null && price !== '' && Number.isFinite(Number(price)) ? Number(price).toFixed(2) : '—' }
export function setmealResultMessage(result: { desc?: string | null; msg?: string | null }, fallback: string) { return result.desc || result.msg || fallback }

export async function searchSetmealDishes(api: Pick<SetmealApi, 'getDishes'>, name: string, categories: SetmealCategory[]): Promise<DishRecord[]> {
  async function read(params: { name?: string; categoryId?: SetmealId }) {
    const { data } = await api.getDishes(params)
    if (String(data.code) !== '1' || !Array.isArray(data.data)) throw new Error(setmealResultMessage(data, '菜品列表加载失败'))
    return data.data
  }
  try { return await read({ name }) }
  catch (error) {
    if (!categories.length) throw error
    // Legacy servers support name alone; the checked-in controller requires categoryId.
    const groups = await Promise.all(categories.map(item => read({ categoryId: item.id })))
    return groups.flat().filter(item => item.name.includes(name.trim()))
  }
}
