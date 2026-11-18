const test = require('node:test')
const assert = require('node:assert/strict')
const loadService = () => import('../services/home-decoration.js')

const config = {
  theme: 'BURGUNDY',
  modules: [
    { type: 'NOTICE', enabled: true, text: '今日正常营业' },
    { type: 'BANNER', enabled: true, items: [{ imageUrl: 'https://images.example.com/banner.jpg', title: '招牌菜', target: 'MENU' }] },
    { type: 'ACTIVITY', enabled: true, items: [{ title: '领取优惠券', target: 'COUPONS' }] },
  ],
}

test('accepts only a supported publication for the currently selected merchant', async () => {
  const { readHomeDecoration } = await loadService()
  assert.deepEqual(readHomeDecoration({ merchantId: '10', config }, '10'), config)
  assert.equal(readHomeDecoration({ merchantId: '11', config }, '10'), null)
  assert.equal(readHomeDecoration({ merchantId: '10', config: null }, '10'), null)
  const hidden = structuredClone(config)
  hidden.modules[1] = { type: 'BANNER', enabled: false, items: [{ title: '', imageUrl: '', target: 'NONE' }] }
  assert.deepEqual(readHomeDecoration({ merchantId: '10', config: hidden }, '10'), hidden)
  const unicode = structuredClone(config)
  unicode.modules[0].text = '🐟'.repeat(100)
  unicode.modules[1].items[0].title = '🐟'.repeat(30)
  assert.deepEqual(readHomeDecoration({ merchantId: '10', config: unicode }, '10'), unicode)
  const invalid = structuredClone(config)
  invalid.modules[1].items[0].target = 'https://example.com'
  assert.equal(readHomeDecoration({ merchantId: '10', config: invalid }, '10'), null)
  invalid.modules[1] = invalid.modules[0]
  assert.equal(readHomeDecoration({ merchantId: '10', config: invalid }, '10'), null)
})

test('coalesces the same store request and drops late results after a selection change', async () => {
  const { createHomeDecorationLoader } = await loadService()
  const requests = []
  let selection = { storeId: '1', merchantId: '10', generation: 1 }, visible = null
  const load = createHomeDecorationLoader(() => selection, id => new Promise((resolve, reject) => requests.push({ id, resolve, reject })), value => { visible = value })
  const first = load()
  assert.equal(load(), first)
  assert.equal(requests.length, 1)
  selection = { storeId: '2', merchantId: '20', generation: 2 }
  const second = load()
  requests[1].resolve({ data: { merchantId: '20', config } })
  await second
  assert.deepEqual(visible, config)
  requests[0].resolve({ data: { merchantId: '10', config: { ...config, theme: 'TEAL' } } })
  await first
  assert.deepEqual(visible, config)
  const retry = load()
  requests[2].reject(new Error('offline'))
  await retry
  assert.equal(visible, null)
})
