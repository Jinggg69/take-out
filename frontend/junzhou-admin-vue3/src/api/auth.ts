import type { AxiosInstance } from 'axios'
import type { ApiResult, LoginPayload, LoginUser, PasswordPayload, ShopStatus } from './types.ts'

export function createAuthApi(client: AxiosInstance) {
  return {
    login: (data: LoginPayload) => client.post<ApiResult<LoginUser | null>>('/employee/login', data),
    logout: () => client.post<ApiResult<null>>('/employee/logout', undefined, { params: {} }),
    getStatus: () => client.get<ApiResult<ShopStatus>>('/shop/status'),
    // Keep the original scalar body, not a new {status} payload.
    setStatus: (status: ShopStatus) => client.put<ApiResult<null>>(`/shop/${status}`, String(status)),
    editPassword: (data: PasswordPayload) => client.put<ApiResult<null>>('/employee/editPassword', data),
  }
}

export type AuthApi = ReturnType<typeof createAuthApi>
