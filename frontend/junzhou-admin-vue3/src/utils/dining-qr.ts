import QRCode from '../vendor/qrcode/index.js'

/** Local SVG generation keeps the revocable token off third-party QR services. */
export function diningQr(payload: string) {
  if (!/^JZ:[0-9a-f]{32}$/.test(payload)) throw new Error('桌码格式无效')
  const qr = new QRCode(3, 1)
  qr.addData(payload); qr.make()
  const size = qr.getModuleCount()
  const cells: string[] = []
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    if (qr.isDark(y, x)) cells.push(`M${x + 4} ${y + 4}h1v1h-1z`)
  }
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size + 8} ${size + 8}" shape-rendering="crispEdges"><path fill="white" d="M0 0h${size + 8}v${size + 8}H0z"/><path fill="black" d="${cells.join('')}"/></svg>`)
}
