import type { ProxyOptions } from 'vite'

export function createDevProxy(apiTarget: string): Record<'/api' | '/ws', ProxyOptions> {
  let target: URL
  try {
    target = new URL(apiTarget)
  } catch {
    throw new Error('API_PROXY_TARGET 必须是有效的 HTTP 或 HTTPS 地址')
  }
  if (!['http:', 'https:'].includes(target.protocol) || target.search || target.hash) {
    throw new Error('API_PROXY_TARGET 必须是无查询参数的 HTTP 或 HTTPS 地址')
  }
  const apiPrefix = target.pathname.replace(/\/$/, '')
  return {
    '/api': {
      target: target.origin,
      changeOrigin: true,
      rewrite: path => path.replace(/^\/api(?=\/|\?|$)/, apiPrefix),
    },
    '/ws': { target: target.origin, changeOrigin: true, ws: true },
  }
}
