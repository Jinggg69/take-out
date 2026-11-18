const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm')
const { ref, computed, watch } = require('vue')
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return{resolve,promise}}
function recharge(dependencies) {
 const profile=ref({joined:true,simulation:true,member:{status:1}}),error=ref(''),busy=ref(false)
 const box={ref,computed,watch,uni:{showToast(){}},useMemberContext:()=>({app:{},profile,busy,error,scope:{value:'1'},current:stamp=>stamp==='1',load:async()=>true,action:async(key,op,after)=>{await op('request');await after()},login(){}}), ...dependencies,module:{exports:{}}}
 const source=fs.readFileSync(require('node:path').join(__dirname,'../pages/member/recharge.vue'),'utf8').split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/mg,'')
 vm.runInNewContext(source+'\nmodule.exports={fetchData,create,records,plans,recordsLoading};',box);return box.module.exports
}
test('充值成功创建刷新不能被较早的套餐/记录请求覆盖',async()=>{
 const stale=deferred();let count=0;const page=recharge({rechargePlans:async()=>[],memberRecords:()=>++count===1?stale.promise:Promise.resolve({data:{records:[{id:'new',status:'PENDING'}],total:1}}),createRecharge:async()=>({})})
 const old=page.fetchData();await page.create({id:'plan'});stale.resolve({data:{records:[],total:0}});await old;assert.equal(page.records.value[0]?.id,'new')
})
test('待扣积分增加和冲抵明确标记，不显示获得积分；钱包保留本金与赠送账户',()=>{
 const source=fs.readFileSync(require('node:path').join(__dirname,'../services/member-state.js'),'utf8').replace(/^import[^\n]*\n/gm,'').replace(/export /g,'')
 const box={module:{exports:{}}};vm.runInNewContext(source+'\nmodule.exports={ledgerChange,ledgerAfter};',box)
 const {ledgerChange,ledgerAfter}=box.module.exports
 assert.equal(ledgerChange({assetType:'POINTS_DEBT',delta:400},'points'),'新增待扣 400')
 assert.equal(ledgerChange({assetType:'POINTS_DEBT',delta:-100},'points'),'已冲抵 100')
 assert.equal(ledgerAfter({assetType:'POINTS_DEBT',afterValue:300},'points'),'待扣积分剩余 300')
 assert.equal(ledgerAfter({assetType:'PRINCIPAL',afterValue:100},'wallet'),'本金余额变更后 ¥100.00')
 assert.equal(ledgerAfter({assetType:'GIFT',afterValue:10},'wallet'),'赠送余额变更后 ¥10.00')
 assert.equal(ledgerChange({assetType:'POINTS',delta:100},'points'),'+100')
})
