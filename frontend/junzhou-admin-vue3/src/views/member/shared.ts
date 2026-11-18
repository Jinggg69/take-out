import { onBeforeUnmount, ref, watch } from 'vue'
import { workspace } from '@/stores/workspace'
import type { ApiResult } from '@/api/types'
import { errorMessage } from '@/utils/error-message'

export function unwrap<T>(response: { data: ApiResult<T> }): T { if (String(response.data.code) !== '1') throw new Error(response.data.msg || '操作失败，请重试'); return response.data.data }
export function useMemberLoad<T>(empty: () => T, fetcher: () => Promise<T>) {
  const value = ref<T>(empty()), loading = ref(false), error = ref('')
  let generation = 0, alive = true
  async function load() {
    const ticket = ++generation
    loading.value = true; error.value = ''
    try { const result = await fetcher(); if (alive && ticket === generation) value.value = result }
    catch (e) { if (alive && ticket === generation) { value.value = empty(); error.value = errorMessage(e, '加载失败，请重试') } }
    finally { if (alive && ticket === generation) loading.value = false }
  }
  watch(workspace.revision, () => { value.value = empty(); void load() })
  onBeforeUnmount(() => { alive = false; generation++ })
  return { value, loading, error, load }
}
export const money = (value: unknown) => value == null ? '—' : `¥${Number(value).toFixed(2)}`
export function display(value: unknown): string { if (value == null || value === '') return '—'; if (Array.isArray(value)) return value.join('、') || '—'; const text = String(value); return /^\d{4}-\d\d-\d\dT/.test(text) ? text.replace('T', ' ') : text }
export const statusLabels: Record<string, string> = { AVAILABLE: '可用', FROZEN: '冻结', HELD: '冻结', USED: '已使用', EXPIRED: '已过期', VOID: '作废', PENDING: '待支付', PAID: '已入账', SUCCESS: '成功', FAILED: '失败', FAIL: '失败', CANCEL: '已取消', CANCELLED: '已取消', REFUNDING: '退款中', REFUNDED: '已退款', MOCK: '模拟支付', WECHAT: '微信支付', FIXED: '减额券', DISCOUNT: '折扣券', PRINCIPAL: '本金', GIFT: '赠送余额', POINTS: '积分', POINTS_DEBT: '待扣积分', MANUAL: '人工入账' }
export interface Column { key: string; label: string; money?: boolean; width?: number; labels?: Record<string, string> }
export const pointColumns: Column[] = [{ key: 'memberNo', label: '会员号' }, { key: 'assetType', label: '资产类型' }, { key: 'delta', label: '变动积分' }, { key: 'beforeValue', label: '变动前' }, { key: 'afterValue', label: '变动后' }, { key: 'businessNo', label: '业务号' }, { key: 'reason', label: '原因' }, { key: 'operatorName', label: '操作人' }, { key: 'storeName', label: '门店' }, { key: 'createTime', label: '时间', width: 180 }]
export const walletColumns: Column[] = pointColumns.map(col => ['delta', 'beforeValue', 'afterValue'].includes(col.key) ? { ...col, money: true, label: col.label.replace('积分', '金额') } : col)
export const rechargeColumns: Column[] = [{ key: 'number', label: '充值单号', width: 190 }, { key: 'memberNo', label: '会员号' }, { key: 'amount', label: '本金', money: true }, { key: 'giftAmount', label: '赠送金额', money: true }, { key: 'paymentChannel', label: '支付通道' }, { key: 'status', label: '状态' }, { key: 'createTime', label: '充值时间', width: 180 }]
