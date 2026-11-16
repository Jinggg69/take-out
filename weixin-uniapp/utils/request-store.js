// Registration keeps request -> provider independent of store -> API -> request.
// The getter returns the existing Pinia instance without wrapping its interface.
let provider
export function registerRequestStore(getStore) {
  provider = getStore
}
export function getRequestStore() {
  if (!provider) throw new Error('应用状态尚未初始化')
  return provider()
}
