import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { createServer as createHttpServer } from 'node:http'
import type { AddressInfo } from 'node:net'
import type { Duplex } from 'node:stream'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'
import { createServer as createViteServer, loadEnv } from 'vite'
import { readClientEnvironment } from '../src/config/environment.ts'

const root = fileURLToPath(new URL('..', import.meta.url))

test('checked-in modes preserve dev/UAT true and production false', () => {
  for (const [mode, permissions, socket] of [
    ['development', true, 'ws://localhost:8080/ws/'],
    ['production', false, '/ws/'],
    ['production.uat', true, '/ws/'],
  ] as const) {
    const config = readClientEnvironment(loadEnv(mode, root, ''))
    assert.equal(config.baseApi, '/api')
    assert.equal(config.deletePermissions, permissions)
    assert.equal(config.socketUrl, socket)
  }
})

test('the actual Vite config forwards HTTP and WebSocket to their original namespaces',
  { timeout: 20000 }, async () => {
    const upgradedSockets = new Set<Duplex>()
    let upgradedPath = ''
    const backend = createHttpServer(async (request, response) => {
      let body = ''
      for await (const chunk of request) body += chunk.toString()
      response.setHeader('Content-Type', 'application/json')
      response.end(JSON.stringify({ path: request.url, token: request.headers.token, body }))
    })
    backend.on('upgrade', (request, socket) => {
      upgradedPath = request.url ?? ''
      upgradedSockets.add(socket)
      socket.on('error', () => socket.destroy())
      socket.on('close', () => upgradedSockets.delete(socket))
      const accept = createHash('sha1')
        .update(`${request.headers['sec-websocket-key']}258EAFA5-E914-47DA-95CA-C5AB0DC85B11`)
        .digest('base64')
      socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n'
        + `Sec-WebSocket-Accept: ${accept}\r\n\r\n`)
      socket.on('data', () => socket.end(Buffer.from([0x88, 0x00])))
    })
    await new Promise<void>(resolve => backend.listen(0, '127.0.0.1', resolve))
    const previousTarget = process.env.API_PROXY_TARGET
    process.env.API_PROXY_TARGET = `http://127.0.0.1:${(backend.address() as AddressInfo).port}/admin`
    let vite: Awaited<ReturnType<typeof createViteServer>> | undefined
    let websocket: WebSocket | undefined
    try {
      vite = await createViteServer({
        root, configFile: fileURLToPath(new URL('../vite.config.ts', import.meta.url)),
        server: { host: '127.0.0.1', port: 0, open: false }, logLevel: 'error',
      })
      await vite.listen()
      const port = (vite.httpServer?.address() as AddressInfo).port
      const origin = `http://127.0.0.1:${port}`
      const response = await fetch(`${origin}/api/employee/login`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', token: 'fixture-token' },
        body: JSON.stringify({ username: 'fixture', password: 'fixture' }),
        signal: AbortSignal.timeout(5000),
      })
      assert.equal(response.status, 200)
      assert.deepEqual(await response.json(), {
        path: '/admin/employee/login', token: 'fixture-token',
        body: '{"username":"fixture","password":"fixture"}',
      })
      const query = await fetch(`${origin}/api/dish/page?status=0&name=%E9%B1%BC`, {
        signal: AbortSignal.timeout(5000),
      })
      assert.equal((await query.json()).path, '/admin/dish/page?status=0&name=%E9%B1%BC')
      websocket = new WebSocket(`ws://127.0.0.1:${port}/ws/fixture-client`)
      await new Promise<void>((resolve, reject) => {
        const timeout = setTimeout(() => reject(new Error('WebSocket proxy handshake timed out')), 5000)
        websocket?.addEventListener('open', () => { clearTimeout(timeout); resolve() }, { once: true })
        websocket?.addEventListener('error', () => {
          clearTimeout(timeout); reject(new Error('WebSocket proxy handshake failed'))
        }, { once: true })
      })
      assert.equal(upgradedPath, '/ws/fixture-client')
    } finally {
      websocket?.close()
      for (const socket of upgradedSockets) socket.destroy()
      await vite?.close()
      backend.closeAllConnections()
      await new Promise<void>((resolve, reject) => backend.close(error => error ? reject(error) : resolve()))
      if (previousTarget === undefined) delete process.env.API_PROXY_TARGET
      else process.env.API_PROXY_TARGET = previousTarget
    }
  })
