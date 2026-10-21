export function createAuthGuard(getToken: () => string | undefined) {
  return (to: { meta: { notNeedAuth?: boolean } }): true | '/login' =>
    getToken() || to.meta.notNeedAuth ? true : '/login'
}
