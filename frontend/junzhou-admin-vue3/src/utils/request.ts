import { workspace, pendingWrites } from '../stores/workspace'
import { clientEnvironment } from '../config'
import { router } from '../router'
import { sessionCookies } from './cookies'
import { createHttpClient } from './http'

export const request = createHttpClient({
  baseURL: clientEnvironment.baseApi,
  getToken: sessionCookies.getToken,
  onMutationPendingChange:delta=>{pendingWrites.value=Math.max(0,pendingWrites.value+delta)},
  getScope:()=>({storeId:workspace.storeId.value,revision:workspace.revision.value}),
  onUnauthorized: () => {
    workspace.reset()
    sessionCookies.clearSession()
    if (router.currentRoute.value.path !== '/login') void router.push('/login')
  },
})

export default request
