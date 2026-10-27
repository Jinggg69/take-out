import type { AxiosInstance } from 'axios'
import type { ApiResult } from './types.ts'

export type OrderId = string | number
export type OrderAction = 'accept' | 'reject' | 'cancel' | 'delivery' | 'complete'
export type RefundStatus = 'REQUESTED' | 'PROCESSING' | 'VERIFYING' | 'SUCCESS' | 'CLOSED' | 'ABNORMAL'
export interface OrderItem { id?: OrderId; name: string; number: number; amount: number | string; dishFlavor?: string }
export interface OrderRecord {
  id: OrderId; number: string; status: number | string; amount: number | string
  consignee?: string; userName?: string; phone?: string; address?: string; orderTime?: string
  estimatedDeliveryTime?: string; deliveryTime?: string; cancelTime?: string; checkoutTime?: string
  cancelReason?: string; rejectionReason?: string; remark?: string; orderDishes?: string
  tablewareNumber?: number; tablewareStatus?: number; packAmount?: number | string; payMethod?: number
  orderDetailList?: OrderItem[]
  payStatus?: number | string; refundStatus?: RefundStatus | string | null
}
export interface OrderQuery { page: number; pageSize: number; number?: string; phone?: string; beginTime?: string; endTime?: string; status?: number }
export interface OrderPage { records: OrderRecord[]; total: number | string }
export interface OrderStatistics { toBeConfirmed: number; confirmed: number; deliveryInProgress: number }
export function createOrderApi(client: AxiosInstance) {
  const reads = new Map<string, Promise<unknown>>()
  let revision = 0
  function read<T>(url: string, params?: OrderQuery): Promise<{ data: ApiResult<T> }> {
    const key = JSON.stringify([url, params])
    const existing = reads.get(key)
    if (existing) return existing as Promise<{ data: ApiResult<T> }>
    async function current(): Promise<{ data: ApiResult<T> }> {
      const start = revision
      const response = await client.get<ApiResult<T>>(url, { params })
      return start === revision ? response : current()
    }
    const pending = current().finally(() => reads.delete(key))
    reads.set(key, pending)
    return pending
  }
  async function write(url: string, data?: object) {
    const response = await client.put<ApiResult<null>>(url, data)
    if (String(response.data.code) === '1') revision++
    return response
  }
  return {
    getPage: (params: OrderQuery) => read<OrderPage>('/order/conditionSearch', params),
    getStatistics: () => read<OrderStatistics>('/order/statistics'),
    getDetail: (id: OrderId) => read<OrderRecord>(`/order/details/${encodeURIComponent(id)}`),
    accept: (id: OrderId) => write('/order/confirm', { id }),
    reject: (data: { id: OrderId; rejectionReason: string }) => write('/order/rejection', { ...data }),
    cancel: (data: { id: OrderId; cancelReason: string }) => write('/order/cancel', { ...data }),
    delivery: (id: OrderId) => write(`/order/delivery/${encodeURIComponent(id)}`),
    complete: (id: OrderId) => write(`/order/complete/${encodeURIComponent(id)}`),
  }
}
export type OrderApi = ReturnType<typeof createOrderApi>
