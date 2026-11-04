import assert from 'node:assert/strict'
import { test } from 'node:test'
import axios from 'axios'
import { createHttpClient } from '../src/utils/http.ts'
import { createWorkspace } from '../src/stores/workspace.ts'

test('switching clears scope, rejects invalid stores and increments generation even when switching back', () => {
 const w=createWorkspace(); w.load([{id:'10',name:'一号店',permissions:['order:read']},{id:'11',name:'二号店',permissions:[]}]);
 assert.equal(w.storeId.value,'10'); const rev=w.revision.value;
 w.select('11');w.select('10');assert.ok(w.revision.value>rev);
 assert.throws(()=>w.select('99'));w.reset();assert.equal(w.storeId.value,null);
})
test('old store response and old unauthorized errors are discarded before side effects', async () => {
 let storeId='10', revision=1, unauthorized=0;
 const client=createHttpClient({baseURL:'/',getScope:()=>({storeId,revision}),onUnauthorized:()=>unauthorized++});
 let finish!:()=>void; let arrived!:()=>void; const ready=new Promise<void>(r=>{arrived=r});
 client.defaults.adapter=async config=>{arrived();await new Promise<void>(r=>{finish=r});assert.equal(config.headers.get('X-Store-Id'),'10');return {data:{status:401},status:200,statusText:'OK',headers:{},config}};
 const pending=client.get('/order'); await ready;storeId='11';revision++;finish();
 await assert.rejects(pending,e=>axios.isCancel(e));assert.equal(unauthorized,0);
})
test('pending writes expose a store switching lock until completion', async () => {
 let writes=0;let finish!:()=>void;let arrived!:()=>void;const ready=new Promise<void>(r=>{arrived=r})
 const client=createHttpClient({baseURL:'/',onMutationPendingChange:delta=>{writes+=delta}})
 client.defaults.adapter=async config=>{arrived();await new Promise<void>(r=>{finish=r});return {data:{code:1},status:200,statusText:'OK',headers:{},config}}
 const pending=client.put('/order',{id:'1'});await ready;const count=writes;finish();await pending;assert.equal(count,1);assert.equal(writes,0)
})
