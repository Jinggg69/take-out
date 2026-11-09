import type { AxiosInstance } from 'axios'
import type { ApiResult } from './types.ts'

export interface StoreSettings {
  version: number
  address: string
  latitude: number | null
  longitude: number | null
  opensAt: string
  closesAt: string
  deliveryRadiusMeters: number
  minimumOrderAmount: number
  packFeePerItem: number
  deliveryFee: number
  freeDeliveryThreshold: number | null
}
export type MenuType = 'dish' | 'setmeal'
export interface StoreMenuItem {
  id: string | number
  name: string
  price: number
  onSale: 0 | 1
  soldOut: 0 | 1
  overridePrice: number | null
  sort: number
}
export type StoreMenuUpdate = Omit<StoreMenuItem, 'name' | 'price'>
export function createStoreApi(client: AxiosInstance) {
  return {
    settings: () => client.get<ApiResult<StoreSettings>>('/store/settings'),
    saveSettings: (data: StoreSettings) => client.put<ApiResult<StoreSettings>>('/store/settings', data),
    menu: (type: MenuType) => client.get<ApiResult<StoreMenuItem[]>>('/store/menu', { params: { type } }),
    saveMenu: (type: MenuType, data: StoreMenuUpdate) => client.put<ApiResult<null>>('/store/menu', data, { params: { type } }),
  }
}
