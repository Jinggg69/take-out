import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { test } from 'node:test'
import { diningQr } from '../src/utils/dining-qr.ts'

test('table code matches the reference QR matrix and includes a four-module quiet zone', () => {
  const svg=decodeURIComponent(diningQr('JZ:0123456789abcdef0123456789abcdef').split(',')[1]!)
  assert.match(svg,/viewBox="0 0 37 37"/)
  const dark=new Set([...svg.matchAll(/M(\d+) (\d+)h1v1h-1z/g)].map(match=>`${Number(match[1])-4},${Number(match[2])-4}`))
  let matrix=''
  for(let y=0;y<29;y++)for(let x=0;x<29;x++)matrix+=dark.has(`${x},${y}`)?'1':'0'
  assert.equal(createHash('sha256').update(matrix).digest('hex'),'c452f6b191af79930d1c832b25308e83e55848ae09773d9a35330705c673f233')
})
test('table QR rejects arbitrary payloads before constructing SVG', () => {
  for(const payload of ['', 'https://example.com', 'JZ:<script>', 'JZ:'+'a'.repeat(33)])assert.throws(()=>diningQr(payload))
})
