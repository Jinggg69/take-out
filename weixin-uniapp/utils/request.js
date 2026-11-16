import { getRequestStore as getAppStore } from './request-store.js'
import { baseUrl } from './env.js'

// Public discovery has no credentials. Identity survives store changes; store requests
// additionally belong to the exact selection generation that started them.
export function request({ url = '', params = {}, method = 'GET', scope = 'store' } = {}) {
  const app = getAppStore()
  if (!['public', 'identity', 'store'].includes(scope)) return Promise.reject({ msg: '请求范围无效' })
  if (scope === 'store' && !app.selectedStoreId) return Promise.reject({ missingStore: true, msg: '请先选择商家和门店' })
  if (scope === 'identity' && !app.token) return Promise.reject({ statusCode: 401, msg: '请先登录' })
  const storeId = app.selectedStoreId
  const generation = app.storeGeneration
  const token = app.token
  const userId = app.userId
  const epoch = app.authEpoch
  const stale = () => {
    if (scope !== 'public' && (epoch !== app.authEpoch || token !== app.token || userId !== app.userId)) return { staleIdentity: true, msg: '登录状态已变化，请重试' }
    if (scope === 'store' && (generation !== app.storeGeneration || storeId !== app.selectedStoreId)) return { staleStore: true, msg: '门店已切换，请重试' }
    return null
  }
  const header = { Accept: 'application/json', 'Content-Type': 'application/json' }
  if (scope === 'store') header['X-Store-Id'] = String(storeId)
  if (scope !== 'public' && token) header.authentication = token
  app.setLodding(false)
  return new Promise((resolve, reject) => uni.request({
    url: baseUrl + url, data: params, method, header,
    success: res => {
      const expired = stale()
      if (expired) { reject(expired); return }
      const data = res.data
      if (res.statusCode >= 400) {
        if (res.statusCode === 401 && scope !== 'public') app.clearAuth()
        reject({ ...(data && typeof data === 'object' ? data : {}), statusCode: res.statusCode, msg: data?.msg || (res.statusCode === 401 ? '请重新登录' : '请求失败，请稍后重试') })
      } else if (data && (data.code === 1 || data.code === 200)) resolve(data)
      else reject({ ...(data && typeof data === 'object' ? data : {}), statusCode: res.statusCode, msg: data?.msg || '响应数据异常，请稍后重试' })
    },
    fail: err => reject(stale() || { msg: '网络请求失败，请稍后重试', errMsg: err.errMsg, data: { msg: err.data } })
  }))
}
