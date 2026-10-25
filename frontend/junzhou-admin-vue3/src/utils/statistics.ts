import type { StatisticsRange } from '../api/statistics.ts'
export type StatisticsPreset = 'yesterday' | '7' | '30' | 'week' | 'month'
export interface ChartRow { label: string; values: (number | null)[] }
function dateOnly(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` }
export function presetRange(preset: StatisticsPreset, now = new Date()): StatisticsRange {
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const end = new Date(start)
  if (preset === 'month') { start.setDate(1); end.setMonth(end.getMonth() + 1, 0) }
  else if (preset === 'week') {
    // Preserve the incumbent Sunday behavior as well as the complete calendar week.
    start.setDate(start.getDate() - (now.getDay() - 1)); end.setDate(end.getDate() + (7 - now.getDay()))
  } else {
    start.setDate(start.getDate() - (preset === 'yesterday' ? 1 : Number(preset))); end.setDate(end.getDate() - 1)
  }
  return { begin: dateOnly(start), end: dateOnly(end) }
}
function csv(value: unknown): string[] { return typeof value === 'string' && value.trim() ? value.split(',') : [] }
export function chartRows(labels: unknown, lists: unknown[]): ChartRow[] {
  const columns = lists.map(csv)
  return csv(labels).map((label, index) => ({ label, values: columns.map(column => {
    const value = column[index]?.trim()
    return value && Number.isFinite(Number(value)) ? Number(value) : null
  }) }))
}
export function rankingRows(labels: unknown, values: unknown) { return chartRows(labels, [values]).slice(0, 10) }
export function chartX(index: number, length: number) { return length <= 1 ? 332 : 72 + index / (length - 1) * 520 }
export function chartY(value: number, maximum: number) { return 218 - value / Math.max(1, maximum) * 182 }
export function lineSegments(rows: ChartRow[], series: number, maximum: number): string[] {
  const segments: string[] = []; let current: string[] = []
  rows.forEach((row, i) => {
    const value = row.values[series]
    if (value === null || value === undefined) { if (current.length) segments.push(current.join(' ')); current = [] }
    else current.push(`${chartX(i, rows.length)},${chartY(value, maximum)}`)
  })
  if (current.length) segments.push(current.join(' '))
  return segments
}
export async function exportWorkbook(blob: Blob): Promise<Blob> {
  if (!blob.size) throw new Error('导出文件为空，请重试')
  if (/json|text\//i.test(blob.type)) {
    const text = await blob.text()
    let message = '导出失败，请重试'
    try { message = JSON.parse(text).msg || message } catch { /* Non-JSON errors are not spreadsheets. */ }
    throw new Error(message)
  }
  return blob
}
