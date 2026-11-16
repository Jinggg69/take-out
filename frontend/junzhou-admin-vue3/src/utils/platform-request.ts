import { clientEnvironment } from '../config'
import { router } from '../router'
import { sessionCookies } from './cookies'
import { createHttpClient } from './http'

// Production uses the same origin (or the configured admin API origin) with its own namespace.
const base = clientEnvironment.baseApi
export const platformRequest = createHttpClient({
  baseURL: /^https?:\/\//.test(base) ? new URL('/platform', base).href : '/platform',
  getToken: sessionCookies.getToken,
  onUnauthorized: () => {
    sessionCookies.clearSession()
    if (router.currentRoute.value.path !== '/login') void router.replace('/login')
  },
})
