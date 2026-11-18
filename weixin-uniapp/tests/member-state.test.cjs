const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm')
const { reactive, ref, computed, watch } = require('vue')
function setup(fetch) {
 const app=reactive({selectedMerchantId:'1',selectedStoreId:'11',storeGeneration:0,userId:'7',authEpoch:0,token:'a'}); let key=0,resets=0
 let source=fs.readFileSync(require('node:path').join(__dirname,'../services/member-state.js'),'utf8').replace(/^import[^\n]*\n/gm,'').replace(/export /g,'')
 const box={ref,computed,watch,useAppStore:()=>app,memberMe:fetch,createClientRequestId:()=>String(++key),onShow:()=>{},module:{exports:{}}}
 vm.runInNewContext(source+'\nmodule.exports=useMemberContext;',box)
 return {app, state:box.module.exports(()=>resets++),get resets(){return resets}}
}
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return{resolve,promise}}
test('merchant/account switches synchronously clear assets and block old chained page responses',async()=>{
 const pending=deferred(),h=setup(()=>pending.promise);h.state.profile.value={joined:true,member:{balance:9}};const task=h.state.load()
 h.app.selectedMerchantId='2';assert.equal(h.state.profile.value,null);assert.equal(h.resets,1)
 pending.resolve({data:{joined:true,member:{balance:100}}});await task;assert.equal(h.state.profile.value,null)
 h.state.profile.value={joined:true};h.app.authEpoch++;assert.equal(h.state.profile.value,null)
})
test('later profile load wins even when older same-context request completes last',async()=>{
 const a=deferred(),b=deferred();let calls=0;const h=setup(()=>++calls===1?a.promise:b.promise)
 const one=h.state.load(),two=h.state.load();b.resolve({data:{joined:true,member:{balance:2}}});await two;a.resolve({data:{joined:true,member:{balance:1}}});await one;assert.equal(h.state.profile.value.member.balance,2)
})
test('duplicate mutation is blocked while pending; unknown result retry preserves key',async()=>{
 const h=setup(async()=>({data:{joined:true}})),wait=deferred(),keys=[]
 const op=key=>{keys.push(key);return wait.promise};const task=h.state.action('recharge',op,async()=>{})
 await h.state.action('recharge',op);assert.equal(keys.length,1);wait.resolve(Promise.reject({msg:'network unknown'}));await task
 await h.state.action('recharge',async key=>{keys.push(key);return{}},async()=>{});assert.equal(keys[0],keys[1])
})
test('mutation response cannot invoke after callback after account switch',async()=>{
 const h=setup(async()=>({data:{}})),wait=deferred();let after=0;const task=h.state.action('claim',()=>wait.promise,async()=>after++)
 h.app.authEpoch++;wait.resolve({});await task;assert.equal(after,0);assert.equal(h.state.busy.value,false)
})
