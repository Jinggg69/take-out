const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { spawnSync } = require('node:child_process')
const sass = require('sass')

const compiler = process.env.WECHAT_WCSC_PATH || path.join(process.env['ProgramFiles(x86)'] || 'C:/Program Files (x86)', 'Tencent/微信web开发者工具/resources/app.asar.unpacked/node_modules/wcc-exec/wcsc.exe')

test('共享页面样式可由微信原生 WXSS 编译器解析', { skip: !fs.existsSync(compiler) && '未安装微信原生 WXSS 编译器' }, () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'junzhou-wxss-test-'))
  try {
    const css = sass.compile(path.join(__dirname, '../styles/app.scss')).css
    fs.writeFileSync(path.join(directory, 'app.wxss'), css)
    const result = spawnSync(compiler, ['app.wxss'], { cwd: directory, encoding: 'utf8', windowsHide: true })
    assert.ifError(result.error)
    assert.equal(result.status, 0, result.stderr)
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})
