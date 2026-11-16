export function createAuthGuard(getToken: () => string | undefined, getIdentity: () => string = () => 'EMPLOYEE') {
  return (to: { meta: { notNeedAuth?: boolean; accountType?: string } }): true | string => {
    if (to.meta.notNeedAuth) return true
    if (!getToken()) return '/login'
    const identity = getIdentity()
    if (to.meta.accountType && to.meta.accountType !== identity) return identity === 'PLATFORM' ? '/platform/tenants' : '/dashboard'
    return true
  }
}
