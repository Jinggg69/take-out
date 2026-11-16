import { existsSync } from 'node:fs'
import { readdir } from 'node:fs/promises'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

export async function checkWxss(output) {
  const compiler = process.env.WECHAT_WCSC_PATH || path.join(process.env['ProgramFiles(x86)'] || 'C:/Program Files (x86)', 'Tencent/微信web开发者工具/resources/app.asar.unpacked/node_modules/wcc-exec/wcsc.exe')
  if (!existsSync(compiler)) {
    if (process.env.WECHAT_WCSC_PATH) throw new Error(`WXSS compiler not found: ${compiler}`)
    console.log('SKIP: native WXSS check; set WECHAT_WCSC_PATH to the WeChat wcsc executable')
    return
  }
  let count = 0
  async function visit(directory) {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      const filename = path.join(directory, entry.name)
      if (entry.isDirectory()) await visit(filename)
      else if (entry.name.endsWith('.wxss')) {
        const relative = path.relative(output, filename).replaceAll('\\', '/')
        const result = spawnSync(compiler, [relative], { cwd: output, encoding: 'utf8', windowsHide: true, maxBuffer: 16 * 1024 * 1024 })
        if (result.error) throw result.error
        if (result.status !== 0) throw new Error(`WXSS compilation failed: ${relative}\n${result.stderr}`)
        count++
      }
    }
  }
  await visit(output)
  console.log(`PASS: WeChat native WXSS compiler checked ${count} stylesheets`)
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await checkWxss(path.resolve(process.argv[2] || 'unpackage/dist/build/mp-weixin'))
}
