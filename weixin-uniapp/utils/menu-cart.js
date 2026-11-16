export function cartPayload(product, flavor) {
  if (!product) throw new Error('商品信息不完整')
  const setmeal = product.setmealId != null || Number(product.type) === 2
  const id = setmeal ? (product.setmealId ?? product.id) : (product.dishId ?? product.id)
  if (id == null || !/^\d+$/.test(String(id))) throw new Error('商品信息不完整')
  if (setmeal) return { setmealId: String(id) }
  const value = flavor ?? product.dishFlavor ?? ''
  return { dishId: String(id), dishFlavor: Array.isArray(value) ? value.join(',') : value }
}

export function flavorGroups(flavors) {
  if (!Array.isArray(flavors)) return []
  return flavors.flatMap(group => {
    try {
      const values = Array.isArray(group.value) ? group.value : JSON.parse(group.value)
      const options = Array.isArray(values) ? values.filter(value => typeof value === 'string' && value.trim()) : []
      return options.length ? [{ name: group.name || '规格', options }] : []
    } catch { return [] }
  })
}

export function productQuantity(product, cart) {
  const key = cartPayload(product)
  return (Array.isArray(cart) ? cart : []).reduce((total, row) => {
    const same = key.setmealId != null ? row.setmealId != null && String(row.setmealId) === key.setmealId : row.dishId != null && String(row.dishId) === key.dishId
    return total + (same ? Number(row.number) || 0 : 0)
  }, 0)
}

export function cartTotal(cart) {
  let quantity = 0, cents = 0
  for (const row of cart || []) {
    const count = Number(row.number), price = Number(row.amount)
    if (!Number.isInteger(count) || count < 1 || !Number.isFinite(price) || price < 0) throw new Error('商品数量或价格异常，请刷新购物车')
    quantity += count
    cents += Math.round(price * 100) * count
  }
  return { quantity, amount: (cents / 100).toFixed(2) }
}
