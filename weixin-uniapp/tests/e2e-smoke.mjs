// Test-only backend fixtures. No mock data is imported by the shipped application.
import { chromium } from 'playwright'
import { createServer } from 'node:http'
import { readFile, mkdir } from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const dist = path.join(root, 'unpackage/dist/build/h5')
const output = path.resolve(root, '../local-reports/vue3-ui')
await mkdir(output, { recursive: true })
const types = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.png': 'image/png', '.svg': 'image/svg+xml', '.gif': 'image/gif', '.woff2': 'font/woff2', '.ttf': 'font/ttf' }
const server = createServer(async (req, res) => {
  try {
    const requested = decodeURIComponent(new URL(req.url, 'http://localhost').pathname)
    let filename = path.resolve(dist, '.' + requested)
    if (!filename.startsWith(dist + path.sep) && filename !== dist) { res.writeHead(403).end(); return }
    if (filename === dist || requested.endsWith('/')) filename = path.join(dist, 'index.html')
    const content = await readFile(filename)
    res.setHeader('content-type', types[path.extname(filename)] || 'application/octet-stream'); res.end(content)
  } catch { res.writeHead(404).end('Not found') }
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const origin = `http://127.0.0.1:${server.address().port}`
const merchants = [{ merchantId: '1', merchantName: '测试商家甲', activeStoreCount: 2 }, { merchantId: '2', merchantName: '测试商家乙', activeStoreCount: 1 }]
const contexts = Object.fromEntries([['11', '1', '甲一店'], ['12', '1', '甲二店'], ['21', '2', '乙门店']].map(([storeId, merchantId, storeName]) => [storeId, { storeId, merchantId, merchantName: merchants.find(x => x.merchantId === merchantId).merchantName, storeName, address: '测试地址，仅用于界面验证', open: true, opensAt: '09:00', closesAt: '22:00', minimumOrderAmount: '0.00', deliveryFee: '6.00', packFeePerItem: '1.00', configVersion: '1' }]))
const cart = { '11': [], '12': [], '21': [] }
const requests = [], unexpected = [], errors = []
let paid = false, submitted = null
const now = () => new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 19).replace('T', ' ')
const history = () => ({ orderId: '701', orderNumber: 'TEST701', merchantId: '1', merchantName: '测试商家甲', storeId: '11', storeName: '甲一店', storeAvailable: true, orderTime: now(), status: paid ? 2 : 1, payStatus: paid ? 1 : 0, fulfillmentType: 'PICKUP', fulfillmentStatus: paid ? 'AWAITING_ACCEPTANCE' : 'AWAITING_PAYMENT', amount: '89.00', totalQuantity: 1, items: [{ name: '清蒸鲜鱼', number: 1 }], orderDetails: [{ name: '清蒸鲜鱼', number: 1, unitPrice: '88.00' }], goodsAmount: '88.00', packFee: '1.00', deliveryFee: '0.00', contactName: '测试用户', contactPhone: '13800000000' })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1, isMobile: true, hasTouch: true, timezoneId: 'Asia/Shanghai' })
const page = await context.newPage()
page.setDefaultTimeout(15000)
page.on('pageerror', error => errors.push(error.message))
await page.route('**/user/**', async route => {
  const req = route.request(), u = new URL(req.url()), p = u.pathname, method = req.method(), headers = req.headers(), store = headers['x-store-id']
  if (method === 'OPTIONS') { await route.fulfill({ status: 204, headers: { 'access-control-allow-origin': '*', 'access-control-allow-methods': '*', 'access-control-allow-headers': '*' } }); return }
  requests.push({ path: p, store, method, params: Object.fromEntries(u.searchParams) })
  const payload = req.postDataJSON?.() || {}
  let data, failure = null
  if (p === '/user/merchants') { const list = merchants.filter(x => !u.searchParams.get('keyword') || x.merchantName.includes(u.searchParams.get('keyword'))); data = { total: list.length, records: list } }
  else if (/^\/user\/merchants\/\d+\/stores$/.test(p)) { const id = p.split('/')[3], records = Object.values(contexts).filter(x => x.merchantId === id); data = { total: records.length, records } }
  else if (/^\/user\/merchants\/\d+$/.test(p)) data = merchants.find(x => x.merchantId === p.split('/').at(-1))
  else if (/^\/user\/stores\/\d+\/context$/.test(p)) data = contexts[p.split('/')[3]]
  else if (p === '/user/me') data = { userId: '42', nickname: '测试用户', avatarUrl: null }
  else if (p === '/user/me/order-filters') data = { merchantId: '1', merchantName: '测试商家甲', stores: [{ storeId: '11', storeName: '甲一店' }, { storeId: '12', storeName: '甲二店' }] }
  else if (p === '/user/me/orders') { assert.ok(u.searchParams.get('merchantId')); assert.equal(store, undefined); const records = u.searchParams.get('merchantId') === '1' ? [history()] : []; data = { total: records.length, records } }
  else if (/^\/user\/me\/orders\/\d+$/.test(p)) data = history()
  else if (p === '/user/category/list') { assert.ok(store); data = [{ id: '101', name: '招牌菜', type: 1 }, { id: '102', name: '套餐', type: 2 }] }
  else if (p === '/user/dish/list') data = [{ id: '501', name: '清蒸鲜鱼', price: 88, status: 1, flavors: [] }, { id: '502', name: '风味小炒', price: 28, status: 1, flavors: [{ name: '辣度', value: '["微辣","重辣"]' }, { name: '盐量', value: '["少盐","正常"]' }] }]
  else if (p === '/user/setmeal/list') data = [{ id: '601', name: '双人套餐', price: 119, status: 1 }]
  else if (p === '/user/shoppingCart/list') { assert.ok(store); data = cart[store] || [] }
  else if (p === '/user/shoppingCart/add' || p === '/user/shoppingCart/sub') {
    assert.ok(store); const rows = cart[store]; const id = payload.dishId || payload.setmealId
    const item = rows.find(x => String(x.dishId || x.setmealId) === String(id) && (x.dishFlavor || '') === (payload.dishFlavor || ''))
    if (p.endsWith('/add')) { if (item) item.number++; else rows.push({ id: String(rows.length + 1), ...payload, name: id === '502' ? '风味小炒' : id === '601' ? '双人套餐' : '清蒸鲜鱼', amount: id === '502' ? 28 : id === '601' ? 119 : 88, number: 1 }) }
    else if (item && --item.number <= 0) rows.splice(rows.indexOf(item), 1)
    data = null
  }
  else if (p === '/user/shoppingCart/clean') { cart[store] = []; data = null }
  else if (p === '/user/order/quote') { const items = cart[store], goodsAmount = items.reduce((s, x) => s + x.amount * x.number, 0), count = items.reduce((s, x) => s + x.number, 0), type = u.searchParams.get('fulfillmentType') || 'DELIVERY', packFee = type === 'DINE_IN' ? 0 : count, deliveryFee = type === 'DELIVERY' ? 6 : 0; data = { storeId: store, configVersion: 1, fulfillmentType: type, items, goodsAmount, packAmount: count, packFee, deliveryFee, amount: goodsAmount + packFee + deliveryFee } }
  else if (p === '/user/addressBook/default') failure = '没有查询到默认地址'
  else if (p === '/user/addressBook/list') { assert.equal(store, undefined); data = [{ id: '801', consignee: '测试用户', sex: '0', phone: '13800000000', provinceName: '湖北省', cityName: '十堰市', districtName: '丹江口市', detail: '测试路1号', isDefault: 1 }] }
  else if (p === '/user/order/getEstimatedDeliveryTime') data = now()
  else if (p === '/user/order/submit') { assert.equal(payload.fulfillmentType, 'PICKUP'); assert.ok(payload.clientRequestId); submitted = payload; data = { id: '701', orderNumber: 'TEST701', orderTime: now(), orderAmount: payload.amount }; cart[store] = [] }
  else if (p === '/user/order/payment') data = { channel: 'MOCK', orderId: '701', orderNumber: 'TEST701' }
  else if (p === '/user/order/701/payment/mock') { paid = payload.outcome === 'SUCCESS'; data = { id: '701', status: paid ? 2 : 1, payStatus: paid ? 1 : 0 } }
  else if (p === '/user/order/701/payment/confirm') data = { id: '701', status: paid ? 2 : 1, payStatus: paid ? 1 : 0 }
  else if (p === '/user/order/orderDetail/701') data = { ...history(), id: '701', number: 'TEST701', orderDetailList: [{ name: '清蒸鲜鱼', number: 1, amount: 88 }] }
  else { unexpected.push(method + ' ' + p); failure = '测试未声明此接口' }
  await route.fulfill({ status: 200, contentType: 'application/json', headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*' }, body: JSON.stringify(failure ? { code: 0, msg: failure, data: null } : { code: 1, msg: null, data }) })
})
const clickText = async text => {
  const target = page.getByText(text, { exact: true }).last()
  await target.waitFor()
  const button = target.locator('xpath=ancestor-or-self::uni-button[1]')
  if (await button.count()) await button.click()
  else await target.click()
}
const screenshot = name => page.screenshot({ path: path.join(output, name + '.png'), fullPage: true })
const tab = async name => { await page.locator('uni-tabbar').getByText(name, { exact: true }).click() }
try {
  await page.goto(origin)
  await page.getByText('测试商家甲', { exact: true }).waitFor()
  await screenshot('01-merchants')
  assert.equal(requests.find(x => x.path === '/user/merchants').store, undefined)
  await page.evaluate(() => localStorage.setItem('appSession', JSON.stringify({ type: 'object', data: { token: 'ui-test-token', userId: '42', baseUserInfo: { nickname: '测试用户' } } })))
  await page.reload(); await clickText('测试商家甲'); await clickText('甲一店')
  await page.getByText('选择用餐方式', { exact: true }).waitFor(); await screenshot('02-home')
  await clickText('到店自取'); await page.getByText('清蒸鲜鱼', { exact: true }).waitFor()
  await screenshot('03-menu')
  await page.locator('[aria-label="添加清蒸鲜鱼"]').click()
  await page.getByText('共 1 份，费用以结算为准', { exact: true }).waitFor()
  await clickText('选规格'); await clickText('重辣'); await clickText('正常'); await clickText('加入购物车')
  assert.equal(cart['11'].find(x => x.dishId === '502').dishFlavor, '重辣,正常')
  // Keep the checkout fixture focused on one purchased item.
  cart['11'] = cart['11'].filter(x => x.dishId === '501')
  await clickText('去结算')
  await page.locator('uni-input').filter({ hasText: '请填写取餐联系人' }).locator('input').fill('测试用户')
  await page.locator('uni-input').filter({ hasText: '请填写11位手机号' }).locator('input').fill('13800000000')
  await screenshot('04-checkout')
  await clickText('去支付'); await page.getByText('确认支付', { exact: true }).waitFor(); await clickText('确认支付')
  await page.getByText('模拟收银台', { exact: true }).waitFor(); await clickText('模拟支付成功')
  await page.waitForURL(url => url.href.includes('/pages/success/index')); await screenshot('05-payment-result')
  assert.ok(paid && submitted)
  await clickText('返回首页'); await tab('订单')
  await page.getByText('查看订单详情', { exact: true }).waitFor(); await screenshot('06-orders')
  const ordersRequest = requests.findLast(x => x.path === '/user/me/orders'); assert.equal(ordersRequest.params.merchantId, '1'); assert.equal(ordersRequest.store, undefined)
  await clickText('查看订单详情'); await page.getByText('清蒸鲜鱼', { exact: true }).first().waitFor(); await screenshot('07-detail')
  await page.goBack()
  await tab('我的'); await page.getByText('收货地址', { exact: true }).waitFor(); await screenshot('08-my')
  await clickText('收货地址'); await page.getByText('测试路1号', { exact: false }).first().waitFor(); await screenshot('09-addresses')
  assert.equal(requests.findLast(x => x.path === '/user/addressBook/list').store, undefined)
  await page.goBack(); await tab('点单'); await page.getByText('清蒸鲜鱼', { exact: true }).waitFor()
  await page.locator('[aria-label="添加清蒸鲜鱼"]').click(); await page.getByText('共 1 份，费用以结算为准', { exact: true }).waitFor()
  await clickText('切换商家'); await clickText('测试商家乙'); await page.getByText('乙门店', { exact: true }).waitFor()
  await clickText('到店自取'); await page.getByText('选好菜品，再去结算', { exact: true }).waitFor()
  assert.equal(cart['11'].length, 1); assert.equal(cart['21'].length, 0)
  await clickText('切换商家'); await clickText('测试商家甲'); await clickText('甲一店'); await clickText('到店自取')
  await page.getByText('共 1 份，费用以结算为准', { exact: true }).waitFor()
  assert.deepEqual(unexpected, []); assert.deepEqual(errors, [])
  console.log('PASS: guest discovery, merchant/store selection, four tabs, SKU cart, pickup quote, mock payment, merchant-only orders, history detail, shared addresses, cross-merchant cart isolation and recovery')
  console.log('Screenshots: ' + output)
} catch (error) {
  await screenshot('failure')
  console.error((await page.locator('body').innerText()).slice(0, 5000)); console.error('Runtime errors:', errors); console.error('Unexpected API:', unexpected); console.error('Recent requests:', requests.slice(-12))
  throw error
} finally { await browser.close(); server.close() }
