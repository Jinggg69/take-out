export interface OrderMessage { type: 1 | 2; orderId: string | number; content: string }
type SocketConnection = Pick<WebSocket, 'addEventListener' | 'removeEventListener' | 'close'>

export function buildOrderSocketUrl(base: string, origin: string, clientId: string): string {
  const url = new URL(base || '/ws/', origin)
  if (url.protocol === 'http:') url.protocol = 'ws:'
  if (url.protocol === 'https:') url.protocol = 'wss:'
  if (!['ws:', 'wss:'].includes(url.protocol)) throw new Error('订单提醒地址配置无效')
  url.pathname = `${url.pathname.replace(/\/$/, '')}/${encodeURIComponent(clientId)}`
  return url.toString()
}

export function parseOrderMessage(raw: unknown): OrderMessage | null {
  if (typeof raw !== 'string') return null
  try {
    const data = JSON.parse(raw)
    if (!data || ![1, 2].includes(data.type) || typeof data.content !== 'string'
      || !['string', 'number'].includes(typeof data.orderId)) return null
    return { type: data.type, orderId: data.orderId, content: data.content }
  } catch { return null }
}

export function connectOrderSocket(url: string,
  handlers: { onOrder: (message: OrderMessage) => void; onError: () => void },
  createSocket: (url: string) => SocketConnection = address => new WebSocket(address)) {
  const socket = createSocket(url)
  const onMessage = (event: Event) => {
    const message = parseOrderMessage((event as MessageEvent).data)
    if (message) handlers.onOrder(message)
  }
  const onError = () => handlers.onError()
  socket.addEventListener('message', onMessage)
  socket.addEventListener('error', onError)
  let closed = false
  return () => {
    if (closed) return
    closed = true
    socket.removeEventListener('message', onMessage)
    socket.removeEventListener('error', onError)
    socket.close()
  }
}
