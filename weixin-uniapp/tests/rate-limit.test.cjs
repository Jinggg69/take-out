const test = require('node:test')
const assert = require('node:assert/strict')
const { loadStore, loadRequest } = require('./core-harness.cjs')
for (const statusCode of [429, 503]) test(`保护${statusCode}保留等待时间且不登出、不重放`, async () => {
 const { store } = loadStore(); store.setSession({token:'t',userId:'u'}); store.selectContext({merchantId:'1',storeId:'1'})
 let calls=0
 const request=loadRequest(store,{request: o=>{calls++;o.success({statusCode,header:{'rEtRy-AfTeR':'7'},data:{code:0,msg:'请等待'}})}})
 await assert.rejects(request({url:'/x',method:'POST'}),e=>e.statusCode===statusCode&&e.retryAfterSeconds===7&&e.msg==='请等待')
 assert.equal(store.token,'t');assert.equal(calls,1)
})
test('429缺失或非法等待值使用有限默认',async()=>{
 const {store}=loadStore();store.selectContext({merchantId:'1',storeId:'1'})
 const request=loadRequest(store,{request:o=>o.success({statusCode:429,header:{'Retry-After':'oops'},data:{}})})
 await assert.rejects(request({}),e=>e.retryAfterSeconds===3)
})

test('普通503不标记为保护等待',async()=>{
 const {store}=loadStore();store.selectContext({merchantId:'1',storeId:'1'})
 const request=loadRequest(store,{request:o=>o.success({statusCode:503,data:{msg:'业务不可用'}})})
 await assert.rejects(request({}),e=>e.statusCode===503&&e.retryAfterSeconds===undefined&&e.msg==='业务不可用')
})
