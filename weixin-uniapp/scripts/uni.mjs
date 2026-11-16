import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const args = process.argv.slice(2)
const platformIndex = args.indexOf('-p')
const platform = platformIndex >= 0 ? args[platformIndex + 1] : 'h5'
const production = args.includes('build')
const child = spawn(process.execPath, [path.join(root, 'node_modules/@dcloudio/vite-plugin-uni/bin/uni.js'), ...args], {
  cwd: root,
  env: { ...process.env, UNI_INPUT_DIR: root, UNI_OUTPUT_DIR: path.join(root, 'unpackage/dist', production ? 'build' : 'dev', platform), NODE_ENV: production ? 'production' : 'development' },
  stdio: 'inherit',
})
child.on('exit', code => process.exit(code ?? 1))
child.on('error', error => { console.error(error.message); process.exit(1) })
