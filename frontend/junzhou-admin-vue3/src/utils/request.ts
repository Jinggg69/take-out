import { clientEnvironment } from '../config'
import { router } from '../router'
import { sessionCookies } from './cookies'
import { createHttpClient } from './http'

export const request = createHttpClient({
  baseURL: clientEnvironment.baseApi,
  getToken: sessionCookies.getToken,
  onUnauthorized: () => {
    // Preserve the original redirect; do not add role checks or revoke cookies here.
    if (router.currentRoute.value.path !== '/login') void router.push('/login')
  },
})

export default request
