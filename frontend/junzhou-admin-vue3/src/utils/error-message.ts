import axios from 'axios'
import type { RequestFailureDetails } from './request-failure.ts'

export function errorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const message = (error as typeof error & RequestFailureDetails).msg ?? error.response?.data?.msg
    if (typeof message === 'string' && message) return message
    if (error.response?.status === 401) return '登录已失效，请重新登录'
    return fallback
  }
  return error instanceof Error && error.message ? error.message : fallback
}
