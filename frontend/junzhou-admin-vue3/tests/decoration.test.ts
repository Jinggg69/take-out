import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createDecorationApi, decorationPublishError, sameDecoration, type DecorationConfig } from '../src/api/decoration.ts'
import { sidebarNavigation } from '../src/router/navigation.ts'
import { pagePermissions } from '../src/stores/workspace.ts'

const draft: DecorationConfig = {
  theme: 'TEAL',
  modules: [
    { type: 'NOTICE', enabled: true, text: '欢迎光临' },
    { type: 'BANNER', enabled: true, items: [] },
    { type: 'ACTIVITY', enabled: false, items: [] },
  ],
}

test('publication status ignores MySQL JSON key order but respects module order', () => {
  const published: DecorationConfig = {
    modules: [
      { text: '欢迎光临', enabled: true, type: 'NOTICE' },
      { items: [], enabled: true, type: 'BANNER' },
      { items: [], enabled: false, type: 'ACTIVITY' },
    ],
    theme: 'TEAL',
  }
  assert.ok(sameDecoration(draft, published))
  assert.ok(!sameDecoration(draft, { ...published, modules: [...published.modules].reverse() }))
  assert.ok(!sameDecoration(draft, null))
})

test('decoration writes send the observed version and publish the saved draft', async () => {
  const calls: unknown[][] = []
  const client = Object.fromEntries(['get', 'put', 'post'].map(method => [method, (...args: unknown[]) => calls.push([method, ...args])]))
  const api = createDecorationApi(client as unknown as Parameters<typeof createDecorationApi>[0])
  await api.load()
  await api.save(4, draft)
  await api.publish(5)
  await api.reset(6)
  assert.deepEqual(calls, [
    ['get', '/decoration/home'],
    ['put', '/decoration/home', { version: 4, config: draft }],
    ['post', '/decoration/home/publish', { version: 5 }],
    ['post', '/decoration/home/reset', { version: 6 }],
  ])
})

test('publishing requires content for enabled modules and ignores disabled ones', () => {
  assert.match(decorationPublishError(draft), /轮播/)
  const adjusted = structuredClone(draft)
  adjusted.modules[1]!.enabled = false
  assert.equal(decorationPublishError(adjusted), '')
  adjusted.modules[0] = { type: 'NOTICE', enabled: true, text: '    ' }
  assert.match(decorationPublishError(adjusted), /公告/)
})

test('decoration menu is only exposed for its dedicated permission', () => {
  const available = (permissions: string[]) =>
    sidebarNavigation(path => permissions.includes(pagePermissions[path.slice(1)] ?? '')).map(item => item.path)
  assert.ok(!available(['shop:read']).includes('/decoration'))
  assert.ok(available(['decoration:manage']).includes('/decoration'))
})
