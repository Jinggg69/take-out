const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')

function loadMenu() {
  const additions = [], subtractions = []
  const context = vm.createContext({
    module: { exports: {} }, mapState: () => ({}), mapMutations: () => ({}),
    navBar: {}, Phone: {}, popMask: {}, popCart: {}, dishDetail: {},
    newAddShoppingCartAdd: async value => { additions.push(value); return { code: 1 } },
    newShoppingCartSub: async value => { subtractions.push(value); return { code: 1 } },
    uni: { showToast() {} },
  })
  const source = fs.readFileSync(path.join(__dirname, '../pages/index/index.js'), 'utf8')
    .replace(/^import[\s\S]*?from\s*["'][^"']+["'];?[^\n]*/gm, '')
    .replace('export default', 'module.exports =')
  vm.runInContext(source, context)
  const component = context.module.exports
  const page = Object.assign(component.data(), component.methods, {
    orderListDataes: [], flavorDataes: [], dishDetailes: { dishNumber: 0 },
    getTableOrderDishListes() {}, getDishListDataes() {},
  })
  return { page, additions, subtractions }
}

test('多项口味的加菜和减菜发送同一完整组合', async () => {
  const { page, additions, subtractions } = loadMenu()
  const item = { id: 5, type: 1, flavorRemark: JSON.stringify(['微辣', '少盐']) }
  await page.addDishAction(item, '普通')
  await page.redDishAction(item, '普通')
  assert.equal(additions[0].dishFlavor, '微辣,少盐')
  assert.equal(subtractions[0].dishFlavor, '微辣,少盐')
  assert.equal(subtractions[0].dishId, 5)
})

test('购物车已有口味优先于过时的规格选择，套餐只发送套餐 ID', async () => {
  const { page, subtractions } = loadMenu()
  await page.redDishAction({ dishId: 5, dishFlavor: '重辣,多盐', flavorRemark: '["微辣"]' }, '购物车')
  assert.equal(subtractions[0].dishFlavor, '重辣,多盐')
  await page.redDishAction({ setmealId: 5, dishFlavor: '不适用' }, '购物车')
  assert.equal(subtractions[1].setmealId, 5)
  assert.equal(subtractions[1].dishId, undefined)
  assert.equal(subtractions[1].dishFlavor, undefined)
})
