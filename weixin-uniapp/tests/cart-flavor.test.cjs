const assert = require('node:assert/strict')
const test = require('node:test')
let menu
test.before(async () => { menu = await import('../utils/menu-cart.js') })

test('菜品与套餐ID不混淆，同菜不同完整口味分别增减', () => {
  const product = { id: '5', type: 1 }
  assert.deepEqual(menu.cartPayload(product, ['微辣', '少盐']), { dishId: '5', dishFlavor: '微辣,少盐' })
  assert.deepEqual(menu.cartPayload({ setmealId: '5', type: 2 }, ['微辣']), { setmealId: '5' })
  const cart = [{ dishId: '5', dishFlavor: '微辣,少盐', number: 2 }, { dishId: '5', dishFlavor: '重辣,少盐', number: 1 }, { setmealId: '5', number: 4 }]
  assert.equal(menu.productQuantity(product, cart), 3)
  assert.equal(menu.productQuantity({ id: '5', type: 2 }, cart), 4)
  assert.deepEqual(menu.cartPayload(cart[1]), { dishId: '5', dishFlavor: '重辣,少盐' })
})
test('后台规格JSON解析且拒绝损坏或空组选项', () => {
  assert.deepEqual(menu.flavorGroups([{ name: '辣度', value: '["微辣","重辣"]' }]), [{ name: '辣度', options: ['微辣', '重辣'] }])
  assert.deepEqual(menu.flavorGroups([{ name: '坏值', value: '{bad' }, { name: '空值', value: '[]' }]), [])
})
test('购物车只计算真实商品金额，按分处理小数，不附加其他门店费用', () => {
  assert.deepEqual(menu.cartTotal([{ number: 3, amount: '0.10' }, { number: 1, amount: '0.20' }]), { quantity: 4, amount: '0.50' })
  assert.deepEqual(menu.cartTotal([]), { quantity: 0, amount: '0.00' })
  assert.throws(() => menu.cartTotal([{ number: 1, amount: 'not-price' }]), /价格/)
  assert.throws(() => menu.cartPayload({}), /商品/)
})
