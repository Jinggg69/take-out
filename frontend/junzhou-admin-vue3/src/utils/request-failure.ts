import { AxiosHeaders, type AxiosError } from 'axios'

export interface RequestFailureDetails {
  statusCode?: number
  retryAfterSeconds?: number
  msg?: string
}

// Keep the Axios response intact for existing callers. Presentation belongs to the page.
export async function normalizeRequestFailure(error: AxiosError): Promise<void> {
  const response = error.response
  if (!response) return
  let data: unknown = response.data
  if (data instanceof Blob) {
    try { data = JSON.parse(await data.text()) } catch { /* Preserve non-JSON binary errors. */ }
  }
  const details = error as AxiosError & RequestFailureDetails
  details.statusCode = response.status
  const header = response.headers instanceof AxiosHeaders ? response.headers.get('Retry-After') : Object.entries(response.headers).find(([key]) => key.toLowerCase() === 'retry-after')?.[1]
  const seconds = typeof header === 'string' && /^\d+$/.test(header) ? Number(header) : NaN
  const protection = response.status === 429 || (response.status === 503 && header != null)
  if (protection) details.retryAfterSeconds = Number.isSafeInteger(seconds) && seconds > 0 ? seconds : response.status === 503 ? 3 : 1
  const msg = data && typeof data === 'object' && 'msg' in data ? data.msg : undefined
  if (typeof msg === 'string' && msg.trim()) details.msg = msg
  else if (protection) details.msg = response.status === 429
    ? `操作太频繁，请${details.retryAfterSeconds}秒后再试`
    : '操作保护服务暂不可用，请稍后重试'
  if (details.msg) error.message = details.msg
}


