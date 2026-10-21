import type { CategoryCreate, CategoryEdit, CategoryForm, CategoryId, CategoryRecord, CategoryResult } from '../api/category.ts'
import type { FieldErrors } from './auth-validation.ts'

export function createCategoryForm(): CategoryForm { return { name: '', sort: '' } }
export function categoryFormFromRow(row: CategoryRecord): CategoryForm { return { name: row.name, sort: row.sort } }
export function validateCategory(form: CategoryForm, title = '分类名称'): FieldErrors<CategoryForm> {
  const errors: FieldErrors<CategoryForm> = {}
  if (!form.name) errors.name = `${title}不能为空`
  else if (form.name.length < 2) errors.name = '分类名称输入不符，请输入2至20个字符'
  else if (!/^[A-Za-z\u4e00-\u9fa5]+$/.test(form.name)) errors.name = '分类名称包含特殊字符'
  if (form.sort || String(form.sort) === '0') {
    if (!/^\d+$/.test(String(form.sort))) errors.sort = '排序只能输入数字类型'
    else if (Number(form.sort) > 99) errors.sort = '排序只能输入0至99数字'
  } else errors.sort = '排序不能为空'
  return errors
}
export function categoryCreatePayload(form: CategoryForm, type: '1' | '2'): CategoryCreate { return { name: form.name, type, sort: form.sort } }
export function categoryEditPayload(form: CategoryForm, id: CategoryId): CategoryEdit { return { id, name: form.name, sort: form.sort } }
export function categoryTypeLabel(type: CategoryRecord['type']): string { return String(type) === '1' ? '菜品分类' : '套餐分类' }
export function nextCategoryStatus(status: CategoryRecord['status']): 0 | 1 { return String(status) === '1' ? 0 : 1 }
export function categoryResultMessage(result: Pick<CategoryResult<unknown>, 'msg' | 'desc'>, fallback: string): string { return result.desc || result.msg || fallback }
