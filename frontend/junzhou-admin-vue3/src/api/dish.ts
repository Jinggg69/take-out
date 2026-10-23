import type { AxiosInstance, AxiosResponse } from 'axios'
import type { ApiResult } from './types.ts'

export type DishId = string | number
export interface DishFlavor { [key: string]: unknown; id?: DishId; dishId?: DishId; name: string; value: string }
export interface DishRecord {
  [key: string]: unknown
  id: DishId
  name: string
  categoryId: DishId
  categoryName?: string | null
  price: number | string
  image: string
  description?: string | null
  status: number | string
  updateTime?: string | null
  flavors?: DishFlavor[] | null
}
export interface DishCategory { id: DishId; name: string }
export interface DishQuery { page: number; pageSize: number; name?: string; categoryId?: DishId; status: '' | 0 | 1 }
export interface DishPage { records: DishRecord[]; total: number | string }
export interface DishResult<T> extends ApiResult<T> { desc?: string | null }
export interface DishPayload {
  [key: string]: unknown
  id?: DishId
  name: string
  categoryId: DishId
  price: string
  image: string
  description: string
  status: 0 | 1
  flavors: DishFlavor[]
}

export function createDishApi(client: AxiosInstance) {
  const reads = new Map<string, Promise<AxiosResponse>>()
  let revision = 0
  function read<T>(path: string, params?: object): Promise<AxiosResponse<DishResult<T>>> {
    const key = JSON.stringify([path, params])
    const existing = reads.get(key)
    if (existing) return existing
    async function current(): Promise<AxiosResponse<DishResult<T>>> {
      const startedAt = revision
      const result = await client.get<DishResult<T>>(path, { params })
      // Release the old HTTP duplicate lock before requesting data changed by a write.
      return startedAt === revision ? result : current()
    }
    const request = current().then(result => { reads.delete(key); return result }, error => { reads.delete(key); throw error })
    reads.set(key, request)
    return request
  }
  function write(request: Promise<AxiosResponse<DishResult<null>>>) {
    return request.then(result => { if (String(result.data.code) === '1') revision += 1; return result })
  }
  return {
    getPage: (params: DishQuery) => read<DishPage>('/dish/page', params),
    getById: (id: DishId) => read<DishRecord>(`/dish/${encodeURIComponent(String(id))}`),
    getCategories: () => read<DishCategory[]>('/category/list', { type: 1 }),
    add: (data: DishPayload) => write(client.post<DishResult<null>>('/dish', { ...data })),
    edit: (data: DishPayload) => write(client.put<DishResult<null>>('/dish', { ...data })),
    remove: (ids: string | number) => write(client.delete<DishResult<null>>('/dish', { params: { ids } })),
    setStatus: (id: DishId, status: 0 | 1) => write(client.post<DishResult<null>>(`/dish/status/${status}`, undefined, { params: { id } })),
    upload: (file: File, signal?: AbortSignal) => {
      const data = new FormData(); data.append('file', file)
      return client.post<DishResult<string>>('/common/upload', data, { signal })
    },
  }
}
export type DishApi = ReturnType<typeof createDishApi>
