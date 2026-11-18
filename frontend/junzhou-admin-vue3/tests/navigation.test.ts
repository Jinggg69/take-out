import { test } from 'node:test'
import assert from 'node:assert/strict'
import { sidebarNavigation, breadcrumbsFor } from '../src/router/navigation.ts'

test('product group contains only authorized children and disappears if empty', () => {
 const items = sidebarNavigation(path => ['/order','/dish','/category'].includes(path))
 assert.deepEqual(items.map(item => item.label), ['订单管理','商品管理'])
 assert.deepEqual(items[1]?.children?.map(item => item.path), ['/dish','/category'])
 assert.equal(sidebarNavigation(path => path === '/employee').some(item => item.children), false)
})
test('breadcrumbs include product hierarchy and an editable list parent', () => {
 assert.deepEqual(breadcrumbsFor('/dish/add','修改菜品',()=>true), [
  {label:'首页',path:'/dashboard'}, {label:'商品管理'}, {label:'菜品管理',path:'/dish'}, {label:'修改菜品'},
 ])
 assert.deepEqual(breadcrumbsFor('/dashboard','工作台',()=>true),[{label:'首页'}])
 assert.deepEqual(breadcrumbsFor('/employee/add','添加员工',path=>path!=='/dashboard'),[
  {label:'员工管理',path:'/employee'}, {label:'添加员工'},
 ])
})
