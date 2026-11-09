import type { StoreSettings, StoreMenuUpdate } from '../api/store.ts'

export type SettingsDraft = { [K in keyof Omit<StoreSettings, 'version'>]: string } & { version: number }
export function settingsDraft(data: StoreSettings): SettingsDraft {
  return Object.fromEntries(Object.entries(data).map(([key, value]) => [key, key === 'version' ? value : value == null ? '' : String(value)])) as SettingsDraft
}
function numeric(value: string | number, label: string, min: number, max = Number.MAX_SAFE_INTEGER, integer = false) {
  const number = String(value).trim() === '' ? NaN : Number(value)
  if (!Number.isFinite(number) || number < min || number > max || (integer && !Number.isInteger(number))) throw new Error(`${label}须为${integer ? '整数' : '数字'}，范围 ${min}～${max}`)
  return number
}
function money(value: string, label: string) {
  const number = numeric(value, label, 0, 99999999.99)
  if (Math.abs(number * 100 - Math.round(number * 100)) > 0.000001) throw new Error(`${label}最多保留两位小数`)
  return number
}
export function settingsPayload(draft: SettingsDraft): StoreSettings {
  if (!draft.address.trim()) throw new Error('请填写门店地址')
  if (draft.address.trim().length > 255) throw new Error('门店地址最多 255 个字符')
  if (!draft.latitude.trim() || !draft.longitude.trim()) throw new Error('请填写完整的 BD-09 经纬度坐标')
  for (const coordinate of [draft.latitude, draft.longitude]) if (!/^-?\d+(\.\d{1,6})?$/.test(coordinate.trim())) throw new Error('坐标最多保留六位小数')
  for (const time of [draft.opensAt, draft.closesAt]) if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) throw new Error('营业时间须为 HH:mm 格式')
  return {
    version: draft.version, address: draft.address.trim(),
    latitude: numeric(draft.latitude, '纬度', -90, 90),
    longitude: numeric(draft.longitude, '经度', -180, 180),
    opensAt: draft.opensAt, closesAt: draft.closesAt,
    deliveryRadiusMeters: numeric(draft.deliveryRadiusMeters, '配送半径（米）', 1, 100000, true),
    minimumOrderAmount: money(draft.minimumOrderAmount, '起送金额'),
    packFeePerItem: money(draft.packFeePerItem, '每件打包费'),
    deliveryFee: money(draft.deliveryFee, '配送费'),
    freeDeliveryThreshold: draft.freeDeliveryThreshold.trim() === '' ? null : money(draft.freeDeliveryThreshold, '免配送费门槛'),
  }
}
export function menuPayload(row: Omit<StoreMenuUpdate, 'overridePrice'> & { overridePrice: string }): StoreMenuUpdate {
  return { id: row.id, onSale: row.onSale, soldOut: row.soldOut,
    overridePrice: row.overridePrice.trim() === '' ? null : money(row.overridePrice, '门店售价'),
    sort: numeric(row.sort, '排序', 0, 2147483647, true) }
}
