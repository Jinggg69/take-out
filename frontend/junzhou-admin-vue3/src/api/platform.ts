import type { AxiosInstance } from 'axios'
import type { ApiResult, PasswordPayload } from './types.ts'

export type PlatformResource = 'tenants' | 'stores'
export interface PlatformRecord {
  id: string | number; code: string; name: string; status: number
  tenantId?: string | number; tenantName?: string; storeCount?: number
  address?: string; latitude?: number | null; longitude?: number | null
}
export interface PlatformQuery { page: number; pageSize: number; keyword?: string; tenantId?: string }
export interface PlatformFields {
  code: string; name: string; tenantId?: string | number; address?: string
  latitude?: number | null; longitude?: number | null
  adminUsername?: string; adminPassword?: string; adminName?: string
  store?: { code: string; name: string; address: string; latitude: number | null; longitude: number | null }
}
export function createPlatformAuthApi(client: AxiosInstance) {
  return {
    me: () => client.get<ApiResult<Record<string, unknown>>>('/auth/me'),
    logout: () => client.post<ApiResult<null>>('/auth/logout'),
    editPassword: (data: PasswordPayload) => client.put<ApiResult<null>>('/auth/password', data),
  }
}
export function createPlatformApi(client: AxiosInstance, resource: PlatformResource) {
  const path = `/${resource}`
  const item = (id: string | number) => `${path}/${encodeURIComponent(String(id))}`
  return {
    getPage: (params: PlatformQuery) => client.get<ApiResult<{ records: PlatformRecord[]; total: number }>>(path, { params }),
    create: (data: PlatformFields) => client.post<ApiResult<null>>(path, data),
    update: (id: string | number, data: PlatformFields) => client.put<ApiResult<null>>(item(id), data),
    remove: (id: string | number) => client.delete<ApiResult<null>>(item(id)),
    setStatus: (id: string | number, status: 0 | 1) => client.put<ApiResult<null>>(`${item(id)}/status`, { status }),
  }
}
export type PlatformApi = ReturnType<typeof createPlatformApi>
