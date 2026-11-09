// Orders retain the store and pricing values captured when they were placed.
function readSnapshot(value) {
  try {
    const result = typeof value === 'string' ? JSON.parse(value) : value
    return result && typeof result === 'object' && !Array.isArray(result) ? result : {}
  } catch (error) { return {} }
}

export function orderSnapshotMoney(order, key, fallback = 0) {
  const snapshot = readSnapshot(order && order.pricingSnapshot)
  const amount = snapshot[key]
  return amount != null && Number.isFinite(Number(amount)) && Number(amount) >= 0
    ? Number(amount) : Number(fallback) || 0
}

export function orderSnapshotStoreName(order) {
  const snapshot = readSnapshot(order && order.storeSnapshot)
  return snapshot.name || (order && order.shopName) || ''
}
