import type { DishRecord } from './dish.ts'
import type { AxiosInstance, AxiosResponse } from 'axios'
import type { ApiResult } from './types.ts'

export type SetmealId = string | number
export interface SetmealDish { dishId: SetmealId; name: string; price: number | string; copies: number; id?: SetmealId; setmealId?: SetmealId }
export interface SetmealRecord {
  [key: string]: unknown
  id: SetmealId
  name: string
  categoryId: SetmealId
  categoryName?: string | null
  price: number | string
  image: string
  description?: string | null
  status: number | string
  updateTime?: string | null
  setmealDishes?: SetmealDish[] | null
}
export interface SetmealCategory { id: SetmealId; name: string }
export interface SetmealQuery { page: number; pageSize: number; name?: string; categoryId?: SetmealId; status: '' | 0 | 1 }
export interface SetmealPage { records: SetmealRecord[]; total: number | string }
export interface SetmealResult<T> extends ApiResult<T> { desc?: string | null }
export interface SetmealPayload {
  [key: string]: unknown
  id?: SetmealId
  name: string
  categoryId: SetmealId
  price: string
  image: string
  description: string
  status: 0 | 1
  setmealDishes: SetmealDish[]
}

export function createSetmealApi(client: AxiosInstance) {
  const reads = new Map<string, Promise<AxiosResponse>>()
  let revision = 0
  function read<T>(path: string, params?: object): Promise<AxiosResponse<SetmealResult<T>>> {
    const key = JSON.stringify([path, params])
    const existing = reads.get(key)
    if (existing) return existing
    async function current(): Promise<AxiosResponse<SetmealResult<T>>> {
      const startedAt = revision
      const result = await client.get<SetmealResult<T>>(path, { params })
      // Release the old HTTP duplicate lock before requesting data changed by a write.
      return startedAt === revision ? result : current()
    }
    const request = current().then(result => { reads.delete(key); return result }, error => { reads.delete(key); throw error })
    reads.set(key, request)
    return request
  }
  function write(request: Promise<AxiosResponse<SetmealResult<null>>>) {
    return request.then(result => { if (String(result.data.code) === '1') revision += 1; return result })
  }
  return {
    getPage: (params: SetmealQuery) => read<SetmealPage>('/setmeal/page', params),
    getById: (id: SetmealId) => read<SetmealRecord>(`/setmeal/${encodeURIComponent(String(id))}`),
    getDishCategories: () => read<SetmealCategory[]>('/category/list', { type: 1 }),
    getDishes: (params: { categoryId?: SetmealId; name?: string }) => read<DishRecord[]>('/dish/list', params),
    getCategories: (form = false) => read<SetmealCategory[]>('/category/list', form ? { type: 2, page: 1, pageSize: 1000 } : { type: 2 }),
    add: (data: SetmealPayload) => write(client.post<SetmealResult<null>>('/setmeal', { ...data })),
    edit: (data: SetmealPayload) => write(client.put<SetmealResult<null>>('/setmeal', { ...data })),
    remove: (ids: string | number) => write(client.delete<SetmealResult<null>>('/setmeal', { params: { ids } })),
    setStatus: (id: SetmealId, status: 0 | 1) => write(client.post<SetmealResult<null>>(`/setmeal/status/${status}`, undefined, { params: { id } })),
    upload: (file: File, signal?: AbortSignal) => {
      const data = new FormData(); data.append('file', file)
      return client.post<SetmealResult<string>>('/common/upload', data, { signal })
    },
  }
}
export type SetmealApi = ReturnType<typeof createSetmealApi>
