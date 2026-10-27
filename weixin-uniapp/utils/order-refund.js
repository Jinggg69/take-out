export function orderRefundLabel(order) {
  const labels = {
    REQUESTED: '退款处理中', PROCESSING: '退款处理中', VERIFYING: '退款待核验',
    SUCCESS: '退款成功', CLOSED: '退款已关闭，请联系商家', ABNORMAL: '退款异常，请联系商家',
  }
  if (!order) return ''
  if (labels[order.refundStatus]) return labels[order.refundStatus]
  // Historical payStatus=2 alone cannot prove that the provider returned money.
  return ({ 2: '退款待核验', 3: '退款处理中', 4: '退款异常，请联系商家' })[Number(order.payStatus)] || ''
}
