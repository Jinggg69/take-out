import { readFile, readdir, stat } from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'

const output = fileURLToPath(new URL('../unpackage/dist/build/mp-weixin/', import.meta.url))
const app = JSON.parse(await readFile(path.join(output, 'app.json'), 'utf8'))
assert.equal(app.pages[0], 'pages/merchants/index', 'The first entry must select a merchant')
assert.deepEqual(app.tabBar.list.map(item => item.text), ['首页', '点单', '订单', '我的'])
for (const item of app.tabBar.list) {
  for (const ext of ['.js', '.wxml', '.wxss']) await stat(path.join(output, item.pagePath + ext))
  for (const key of ['iconPath', 'selectedIconPath']) await stat(path.join(output, item[key]))
}
async function bytes(directory) {
  let total = 0
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const name = path.join(directory, entry.name)
    total += entry.isDirectory() ? await bytes(name) : (await stat(name)).size
  }
  return total
}
const size = await bytes(output)
assert.ok(size < 2 * 1024 * 1024, 'Main package exceeds the chosen 2 MiB budget')
console.log(`PASS: merchant entry, four native tabs, page files and icons; ${(size / 1024 / 1024).toFixed(2)} MiB main package`)
