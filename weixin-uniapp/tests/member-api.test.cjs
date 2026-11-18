const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const { loadRequest, loadStore } = require('./core-harness.cjs')
function api(request) {
 const source = fs.readFileSync(require('node:path').join(__dirname, '../services/member-api.js'), 'utf8').replace(/^import[^\n]*\n/gm, '').replace(/export /g, '')
 const box = { request, module: { exports: {} }, encodeURIComponent }
 vm.runInNewContext(source + '\nmodule.exports={memberMe,claimCoupon,exchangeCoupon,createRecharge,mockRecharge,refundRecharge};', box)
 return box.module.exports
}
test('member assets use authenticated store transport and reject stale merchant responses', async () => {
 const {store, uni} = loadStore(); store.setSession({token:'token-a',userId:'42'}); store.selectContext({storeId:'5',merchantId:'2'})
 let pending; uni.request = options => { pending = options }
 const client = api(loadRequest(store,uni)); const task = client.memberMe()
 assert.equal(pending.header.authentication,'token-a'); assert.equal(pending.header['X-Store-Id'],'5')
 store.selectContext({storeId:'6',merchantId:'3'})
 pending.success({statusCode:200,data:{code:1,data:{joined:true,member:{balance:100}}}})
 await assert.rejects(task, error => error.staleStore === true)
})
test('member requests reject stale account responses', async () => {
 const {store, uni} = loadStore(); store.setSession({token:'a',userId:'42'}); store.selectContext({storeId:'5',merchantId:'2'})
 let pending; uni.request = options => { pending = options }
 const task = api(loadRequest(store,uni)).memberMe(); store.setSession({token:'b',userId:'43'})
 pending.success({statusCode:200,data:{code:1,data:{joined:true}}}); await assert.rejects(task,error => error.staleIdentity === true)
})
test('mutations preserve exact idempotency keys and explicit mock outcome', async () => {
 const calls=[]; const client=api(options => {calls.push(options);return Promise.resolve({code:1})})
 await client.claimCoupon('9007199254740993','claim-key'); await client.exchangeCoupon('2','exchange-key'); await client.createRecharge('3','recharge-key'); await client.mockRecharge('4','CANCEL'); await client.refundRecharge('4','refund-key','未使用退款')
 assert.deepEqual(JSON.parse(JSON.stringify(calls.map(c=>c.params))),[{requestId:'claim-key'},{requestId:'exchange-key'},{planId:'3',requestId:'recharge-key'},{outcome:'CANCEL'},{requestId:'refund-key',reason:'未使用退款'}])
 assert.equal(calls[0].url,'/user/member/coupons/9007199254740993/claim')
})
