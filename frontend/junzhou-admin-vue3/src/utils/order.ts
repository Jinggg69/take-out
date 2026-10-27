import type { OrderAction, OrderQuery, OrderRecord } from '../api/order.ts'
export const orderTabs = [{ value: 0, label: '全部订单' }, { value: 2, label: '待接单' }, { value: 3, label: '待派送' }, { value: 4, label: '派送中' }, { value: 5, label: '已完成' }, { value: 6, label: '已取消' }]
export const rejectionReasons = ['订单量较多，暂时无法接单', '菜品已销售完，暂时无法接单', '餐厅已打烊，暂时无法接单', '自定义原因']
export const cancellationReasons = ['订单量较多，暂时无法接单', '菜品已销售完，暂时无法接单', '骑手不足无法配送', '客户电话取消', '自定义原因']
export const actionLabels: Record<OrderAction, string> = { accept: '接单', reject: '拒单', cancel: '取消', delivery: '派送', complete: '完成' }
export function orderStatusLabel(status: number | string) { return ['全部订单', '待付款', '待接单', '待派送', '派送中', '已完成', '已取消'][Number(status)] || '未知状态' }
export function orderRefundLabel(order: Pick<OrderRecord, 'payStatus' | 'refundStatus'>) {
  const labels: Record<string, string> = {
    REQUESTED: '退款处理中', PROCESSING: '退款处理中', VERIFYING: '退款待核验', SUCCESS: '退款成功',
    CLOSED: '退款已关闭，请联系商家', ABNORMAL: '退款异常，请联系商家',
  }
  return labels[order.refundStatus || ''] || ({ 2: '退款待核验', 3: '退款处理中', 4: '退款异常，请联系商家' } as Record<number, string>)[Number(order.payStatus)] || ''
}
export function orderActions(status: number | string): OrderAction[] {
  return ({ 1: ['cancel'], 2: ['accept', 'reject'], 3: ['delivery', 'cancel'], 4: ['complete', 'cancel'], 5: ['cancel'] } as Record<number, OrderAction[]>)[Number(status)] || []
}
export function buildOrderQuery(state: { page: number; pageSize: number; number: string; phone: string; dates: string[] | null; status: number }): OrderQuery {
  return { page: state.page, pageSize: state.pageSize, number: state.number || undefined, phone: state.phone || undefined, beginTime: state.dates?.[0] ? `${state.dates[0].slice(0, 10)} 00:00:00` : undefined, endTime: state.dates?.[1] ? `${state.dates[1].slice(0, 10)} 23:59:59` : undefined, status: state.status || undefined }
}
export function orderReason(selected: string, custom: string, label: string) {
  if (!selected) throw new Error(`请选择${label}原因`)
  if (selected !== '自定义原因') return selected
  const reason = custom.trim()
  if (!reason) throw new Error(`请输入${label}原因`)
  if (reason.length > 20) throw new Error('自定义原因限20字内')
  return reason
}
export function shouldOpenNextOrder(auto: boolean, fromDetail: boolean, tab: number, status: number, remaining: number) { return auto && fromDetail && tab === 2 && status === 2 && remaining > 1 }
export function orderMoney(amount: unknown) { const number = Number(amount ?? 0); return Number.isFinite(number) ? number.toFixed(2) : '0.00' }
