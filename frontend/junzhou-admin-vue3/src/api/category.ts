import type { AxiosInstance, AxiosResponse } from 'axios'
import type { ApiResult } from './types.ts'

export type CategoryId = string | number
export type CategoryType = 1 | 2
export interface CategoryRecord {
  id: CategoryId
  name: string
  type: CategoryType | '1' | '2'
  sort: number | string
  status: number | string
  updateTime?: string | null
}
export interface CategoryQuery { page: number; pageSize: number; name?: string; type?: CategoryType }
export interface CategoryPage { records: CategoryRecord[]; total: number | string }
export interface CategoryResult<T> extends ApiResult<T> { desc?: string | null }
export interface CategoryForm { name: string; sort: number | string }
export interface CategoryCreate extends CategoryForm { type: '1' | '2' }
export interface CategoryEdit extends CategoryForm { id: CategoryId }
export type CategoryEditor = { mode: 'add'; type: '1' | '2' } | { mode: 'edit'; record: CategoryRecord }

export function createCategoryApi(client: AxiosInstance) {
  const pages = new Map<string, Promise<AxiosResponse<CategoryResult<CategoryPage>>>>()
  let revision = 0
  async function readCurrentPage(params: CategoryQuery): Promise<AxiosResponse<CategoryResult<CategoryPage>>> {
    const startedAt = revision
    const response = await client.get<CategoryResult<CategoryPage>>('/category/page', { params })
    // Wait for the old read to release the HTTP duplicate lock before refreshing past a write.
    return startedAt === revision ? response : readCurrentPage(params)
  }
  function afterWrite(request: Promise<AxiosResponse<CategoryResult<null>>>) {
    return request.then(response => { if (String(response.data.code) === '1') revision += 1; return response })
  }
  function getPage(params: CategoryQuery) {
    const key = JSON.stringify(params)
    const existing = pages.get(key)
    if (existing) return existing
    const request = readCurrentPage(params)
      .then(result => { pages.delete(key); return result }, error => { pages.delete(key); throw error })
    pages.set(key, request)
    return request
  }
  return {
    getPage,
    add: (data: CategoryCreate) => afterWrite(client.post<CategoryResult<null>>('/category', { ...data })),
    edit: (data: CategoryEdit) => afterWrite(client.put<CategoryResult<null>>('/category', { ...data })),
    remove: (id: CategoryId) => afterWrite(client.delete<CategoryResult<null>>('/category', { params: { id } })),
    setStatus: (id: CategoryId, status: 0 | 1) => afterWrite(client.post<CategoryResult<null>>(`/category/status/${status}`, undefined, { params: { id } })),
  }
}
export type CategoryApi = ReturnType<typeof createCategoryApi>
