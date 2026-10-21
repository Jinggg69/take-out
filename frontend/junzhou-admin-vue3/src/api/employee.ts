import type { AxiosInstance, AxiosResponse } from 'axios'
import type { ApiResult } from './types.ts'

export type EmployeeId = number | string
export interface EmployeeRecord {
  id: EmployeeId
  username: string
  name: string
  phone: string
  sex: string
  idNumber: string
  status: number | string
  updateTime?: string | null
  [key: string]: unknown
}
export interface EmployeeQuery { page: number; pageSize: number; name?: string }
export interface EmployeePage { records: EmployeeRecord[]; total: number }
export interface EmployeeFields { username: string; name: string; phone: string; sex: '男' | '女'; idNumber: string }
export interface EmployeeForm extends EmployeeFields { id?: EmployeeId; [key: string]: unknown }
export interface EmployeePayload extends Omit<EmployeeFields, 'sex'> { id?: EmployeeId; sex: '0' | '1'; [key: string]: unknown }

function sharedRead<T>(pending: Map<string, Promise<T>>, key: string, start: () => Promise<T>): Promise<T> {
  const existing = pending.get(key)
  if (existing) return existing
  const request = start().then(result => { pending.delete(key); return result }, error => { pending.delete(key); throw error })
  pending.set(key, request)
  return request
}

export function createEmployeeApi(client: AxiosInstance) {
  // Rapid search/navigation should receive the pending read, not cancel its latest subscriber.
  const pages = new Map<string, Promise<AxiosResponse<ApiResult<EmployeePage>>>>()
  const details = new Map<string, Promise<AxiosResponse<ApiResult<EmployeeRecord>>>>()
  return {
    getList: (params: EmployeeQuery) => sharedRead(pages, JSON.stringify(params), () => client.get<ApiResult<EmployeePage>>('/employee/page', { params })),
    getById: (id: EmployeeId) => sharedRead(details, String(id), () => client.get<ApiResult<EmployeeRecord>>(`/employee/${encodeURIComponent(String(id))}`)),
    add: (data: EmployeePayload) => client.post<ApiResult<null>>('/employee', { ...data }),
    edit: (data: EmployeePayload) => client.put<ApiResult<null>>('/employee', { ...data }),
    setStatus: (id: EmployeeId, status: 0 | 1) => client.post<ApiResult<null>>(`/employee/status/${status}`, undefined, { params: { id } }),
  }
}
export type EmployeeApi = ReturnType<typeof createEmployeeApi>
