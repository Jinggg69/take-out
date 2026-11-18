import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createWorkspace } from '../src/stores/workspace.ts'

test('merchant identity survives store changes and clears when the session changes', () => {
 const workspace = createWorkspace()
 workspace.load([{id:'1',name:'一店',permissions:[]},{id:'2',name:'二店',permissions:[]}],{tenantName:'测试商户',roleNames:['商户管理员']})
 workspace.select('2')
 assert.equal(workspace.identity.value.tenantName,'测试商户')
 assert.deepEqual(workspace.identity.value.roleNames,['商户管理员'])
 workspace.reset()
 assert.equal(workspace.identity.value.tenantName,'')
 assert.deepEqual(workspace.identity.value.roleNames,[])
 workspace.load([{id:'3',name:'其他门店',permissions:[]}])
 assert.equal(workspace.identity.value.tenantName,'')
})
