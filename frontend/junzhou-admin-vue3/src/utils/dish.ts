import type { DishFlavor, DishId, DishPayload, DishRecord } from '../api/dish.ts'

export interface EditableFlavor extends Omit<DishFlavor, 'value'> { name: string; value: string[] }
export interface DishForm {
  name: string; categoryId: DishId; price: string; image: string; description: string
  status: boolean; flavors: EditableFlavor[]; metadata: Record<string, unknown>
}
export type DishField = 'name' | 'categoryId' | 'price' | 'image'
export function createDishForm(): DishForm {
  return { name: '', categoryId: '', price: '', image: '', description: '', status: true, flavors: [], metadata: {} }
}
export function validateDish(form: DishForm): Partial<Record<DishField, string>> {
  const errors: Partial<Record<DishField, string>> = {}
  if (!/^([A-Za-z0-9\u4e00-\u9fa5]){2,20}$/.test(form.name)) errors.name = form.name ? '菜品名称输入不符，请输入2-20个字符' : '请输入菜品名称'
  if (form.categoryId === '' || form.categoryId === null || form.categoryId === undefined) errors.categoryId = '请选择菜品分类'
  if (!/^([1-9]\d{0,5}|0)(\.\d{1,2})?$/.test(form.price) || Number(form.price) <= 0) errors.price = '菜品价格格式有误，请输入大于零且最多保留两位小数的金额'
  if (!form.image) errors.image = '菜品图片不能为空'
  return errors
}
export function dishFormFromDetail(detail: DishRecord): DishForm {
  const flavors = (detail.flavors ?? []).map(flavor => {
    const value: unknown = JSON.parse(flavor.value)
    if (!Array.isArray(value) || !value.every(v => typeof v === 'string')) throw new Error('菜品口味数据异常，请重新加载或联系管理员')
    return { ...flavor, value: [...value] as string[] }
  })
  return { name: detail.name, categoryId: detail.categoryId, price: String(detail.price), image: detail.image,
    description: detail.description ?? '', status: String(detail.status) === '1', flavors, metadata: { ...detail } }
}
export function dishPayload(form: DishForm, editing: boolean): DishPayload {
  const payload: DishPayload = { ...(editing ? form.metadata : { code: '' }), name: form.name, price: form.price, image: form.image,
    description: form.description, status: editing && form.status ? 1 : 0, categoryId: form.categoryId,
    flavors: form.flavors.map(flavor => ({ ...flavor, value: JSON.stringify(flavor.value) })) }
  delete payload.dishFlavors
  if (editing) { delete payload.createTime; delete payload.updateTime } else delete payload.id
  return payload
}
const presets = [
  { name: '甜味', value: ['无糖', '少糖', '半糖', '多糖', '全糖'] },
  { name: '温度', value: ['热饮', '常温', '去冰', '少冰', '多冰'] },
  { name: '忌口', value: ['不要葱', '不要蒜', '不要香菜', '不要辣'] },
  { name: '辣度', value: ['不辣', '微辣', '中辣', '重辣'] },
]
export function flavorOptions(selected: EditableFlavor[]) {
  return presets.filter(preset => !selected.some(item => item.name === preset.name)).map(item => ({ name: item.name, value: [...item.value] }))
}
export function imageUploadError(file: { size: number }): string { return file.size / 1024 / 1024 < 2 ? '' : '上传文件大小不能超过2M，请选择更小的图片' }
export function nextDishStatus(status: string | number): 0 | 1 { return String(status) === '0' ? 1 : 0 }
export function formatDishPrice(price: unknown): string { return price !== null && price !== '' && Number.isFinite(Number(price)) ? Number(price).toFixed(2) : '—' }
export function dishResultMessage(result: { desc?: string | null; msg?: string | null }, fallback: string) { return result.desc || result.msg || fallback }
