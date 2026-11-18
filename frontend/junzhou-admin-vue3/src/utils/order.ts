import type { OrderAction, OrderQuery, OrderRecord } from '../api/order.ts'
export const orderTabs = [{ value: 0, label: '全部订单' }, { value: 2, label: '待接单' }, { value: 3, label: '制作 / 待履约' }, { value: 4, label: '派送中' }, { value: 5, label: '已完成' }, { value: 6, label: '已取消' }]
export const rejectionReasons = ['订单量较多，暂时无法接单', '菜品已销售完，暂时无法接单', '餐厅已打烊，暂时无法接单', '自定义原因']
export const cancellationReasons = ['订单量较多，暂时无法接单', '菜品已销售完，暂时无法接单', '骑手不足无法配送', '客户电话取消', '自定义原因']
export const refundReasons = ['顾客申请退款', '餐品质量问题', '订单内容有误', '自定义原因']
export const actionLabels: Record<OrderAction, string> = { accept: '接单', reject: '拒单', cancel: '取消', refund: '退款', delivery: '派送', complete: '完成', ready: '制作完成', verify: '核销取餐', dineComplete: '堂食完成' }
export function orderStatusLabel(status: number | string, order?: Pick<OrderRecord, 'fulfillmentType' | 'fulfillmentStatus'>) {
  if (order?.fulfillmentType === 'DINE_IN' && Number(status) === 3) return '堂食制作中'
  if (order?.fulfillmentType === 'PICKUP' && Number(status) === 3) return order.fulfillmentStatus === 'READY' ? '待取餐' : '制作中'
  return ['全部订单', '待付款', '待接单', '待派送', '派送中', '已完成', '已取消'][Number(status)] || '未知状态' }
export function orderRefundLabel(order: Pick<OrderRecord, 'payStatus' | 'refundStatus'>) {
  const labels: Record<string, string> = {
    REQUESTED: '退款处理中', PROCESSING: '退款处理中', VERIFYING: '退款待核验', SUCCESS: '退款成功',
    CLOSED: '退款已关闭，请联系商家', ABNORMAL: '退款异常，请联系商家',
  }
  return labels[order.refundStatus || ''] || ({ 2: '退款待核验', 3: '退款处理中', 4: '退款异常，请联系商家' } as Record<number, string>)[Number(order.payStatus)] || ''
}
export function orderActions(status: number | string, order?: Pick<OrderRecord, 'fulfillmentType' | 'fulfillmentStatus' | 'payStatus' | 'refundStatus'>): OrderAction[] {
  if (Number(status) === 5) return Number(order?.payStatus) === 1 && !order?.refundStatus ? ['refund'] : []
  if (order?.fulfillmentType === 'DINE_IN' && Number(status) === 3 && Number(order.payStatus) === 1 && !order.refundStatus) return ['dineComplete','cancel']
  if (order?.fulfillmentType && order.fulfillmentType !== 'DELIVERY') {
    if (Number(status) === 3 && order.fulfillmentType === 'PICKUP') {
      if (Number(order.payStatus) === 1 && !order.refundStatus) {
        if (order.fulfillmentStatus === 'PREPARING') return ['ready', 'cancel']
        if (order.fulfillmentStatus === 'READY') return ['verify', 'cancel']
      }
      return ['cancel']
    }
    return Number(status) === 1 ? ['cancel'] : Number(status) === 2 ? ['accept', 'reject'] : Number(status) === 3 ? ['cancel'] : []
  }
  return ({ 1: ['cancel'], 2: ['accept', 'reject'], 3: ['delivery', 'cancel'], 4: ['complete', 'cancel'] } as Record<number, OrderAction[]>)[Number(status)] || []
}
export function permittedOrderActions(order: OrderRecord, can: (action: string) => boolean) {
  const codes: Record<OrderAction, string> = { accept: 'accept', reject: 'reject', cancel: 'cancel', refund: 'refund', delivery: 'deliver', complete: 'complete', ready: 'pickup:ready', verify: 'pickup:verify', dineComplete: 'dine:complete' }
  return orderActions(order.status, order).filter(action => can(`order:${codes[action]}`) && (!['reject', 'cancel'].includes(action) || can('order:refund')))
}
export function buildOrderQuery(state: { page: number; pageSize: number; number: string; phone: string; dates: string[] | null; status: number; fulfillmentType?: string; fulfillmentStatus?: string }): OrderQuery {
  return { ...(state.fulfillmentType ? { fulfillmentType: state.fulfillmentType } : {}), ...(state.fulfillmentStatus ? { fulfillmentStatus: state.fulfillmentStatus } : {}), page: state.page, pageSize: state.pageSize, number: state.number || undefined, phone: state.phone || undefined, beginTime: state.dates?.[0] ? `${state.dates[0].slice(0, 10)} 00:00:00` : undefined, endTime: state.dates?.[1] ? `${state.dates[1].slice(0, 10)} 23:59:59` : undefined, status: state.status || undefined }
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

export function orderPaymentBreakdown(order: Pick<OrderRecord, 'amount' | 'membership' | 'externalPayAmount' | 'pricingSnapshot'>) {
  let member = order.membership
  if (!member) {
    try { const parsed: unknown = JSON.parse(order.pricingSnapshot || '{}'); if (parsed && typeof parsed === 'object' && 'member' in parsed && parsed.member && typeof parsed.member === 'object' && !Array.isArray(parsed.member)) member = parsed.member }
    catch { /* Legacy orders may not carry pricing snapshots. */ }
  }
  const amount = (value: unknown, fallback = 0) => { if (value == null || value === '' || typeof value === 'boolean') return fallback; const result = Number(value); return Number.isFinite(result) && result >= 0 ? result : fallback }
  return {
    externalAmount: amount(order.externalPayAmount ?? member?.externalAmount, amount(order.amount)),
    balanceAmount: amount(member?.balanceAmount), principalAmount: amount(member?.principalAmount), giftAmount: amount(member?.giftAmount),
    pointsUsed: amount(member?.pointsUsed), pointsDiscount: amount(member?.pointsDiscount), couponDiscount: amount(member?.couponDiscount),
    levelDiscount: amount(member?.levelDiscount), discountAmount: amount(member?.discountAmount),
  }
}

export function orderBill(order: Pick<OrderRecord, 'amount' | 'packAmount' | 'pricingSnapshot'>) {
  let snapshot: Record<string, unknown> = {}
  try {
    const parsed: unknown = JSON.parse(order.pricingSnapshot || '{}')
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) snapshot = parsed as Record<string, unknown>
  } catch { /* Orders created before pricing snapshots keep their legacy breakdown. */ }
  const monetary = (value: unknown) => {
    if (typeof value !== 'number' && (typeof value !== 'string' || !value.trim())) return undefined
    const number = Number(value)
    return Number.isFinite(number) && number >= 0 ? number : undefined
  }
  const amount = monetary(snapshot.amount) ?? monetary(order.amount) ?? 0
  const packFee = monetary(snapshot.packFee) ?? monetary(order.packAmount) ?? 0
  const deliveryFee = monetary(snapshot.deliveryFee) ?? 6
  const goodsAmount = monetary(snapshot.goodsAmount) ?? amount - packFee - deliveryFee
  return { goodsAmount, packFee, deliveryFee, amount }
}
