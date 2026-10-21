export interface ApiResult<T> {
  code: number | string
  data: T
  msg?: string | null
}

export interface LoginPayload { username: string; password: string }
export interface LoginUser { token: string; name?: string; [key: string]: unknown }
export type ShopStatus = 0 | 1
export interface PasswordPayload { oldPassword: string; newPassword: string }
