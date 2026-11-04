import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios'

export interface HttpClientOptions {
  baseURL: string
  getToken?: () => string | undefined
  getScope?: () => { storeId: string | null; revision: number }
  onMutationPendingChange?: (delta: number) => void
  onUnauthorized?: () => void
}

function serializeGetParams(params: Record<string, unknown>): string {
  const parts: string[] = []
  const append = (key: string, value: unknown) => {
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
  }
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined) continue
    if (typeof value === 'object') {
      for (const [nestedKey, nestedValue] of Object.entries(value)) {
        append(`${key}[${nestedKey}]`, nestedValue)
      }
    } else {
      append(key, value)
    }
  }
  return parts.join('&')
}

export function createHttpClient(options: HttpClientOptions): AxiosInstance {
  const client = axios.create({ baseURL: options.baseURL, timeout: 600000 })
  const snapshots=new WeakMap<InternalAxiosRequestConfig,string>()
  const identity=()=>JSON.stringify([options.getToken?.(),options.getScope?.()])
  const stale=(config?:InternalAxiosRequestConfig)=>!!config && snapshots.has(config) && snapshots.get(config)!==identity()
  const mutations=new WeakSet<InternalAxiosRequestConfig>()
  const pending = new Map<string, InternalAxiosRequestConfig>()
  const requestKeys = new WeakMap<InternalAxiosRequestConfig, string>()
  const release = (config?: InternalAxiosRequestConfig) => {
    if (!config) return
    if(mutations.delete(config))options.onMutationPendingChange?.(-1)
    const key = requestKeys.get(config)
    // A canceled duplicate must never release the original in-flight request.
    if (key !== undefined && pending.get(key) === config) pending.delete(key)
    requestKeys.delete(config)
  }

  client.interceptors.request.use(config => {
    snapshots.set(config,identity())
    const store=options.getScope?.().storeId
    if(store)config.headers.set('X-Store-Id',store)
    const token = options.getToken?.()
    if (token) config.headers.set('token', token)
    if (config.method === 'get' && config.params) {
      const query = serializeGetParams(config.params)
      if (query) config.url = `${config.url}${config.url?.includes('?') ? '&' : '?'}${query}`
      config.params = {}
    }
    const data = typeof config.data === 'string' ? config.data : JSON.stringify(config.data)
    // A local Map does not need the old MD5 dependency to identify the same inputs.
    const key = `${identity()}&${config.url}&${config.method}&${data}`
    if (pending.has(key)) {
      throw new axios.CanceledError('重复请求', config)
    }
    if(!['get','head','options'].includes(config.method??'get')){mutations.add(config);options.onMutationPendingChange?.(1)}
    pending.set(key, config)
    requestKeys.set(config, key)
    return config
  })

  client.interceptors.response.use(response => {
    release(response.config)
    if(stale(response.config))throw new axios.CanceledError('门店或会话已切换',response.config)
    if (response.data?.status === 401) options.onUnauthorized?.()
    return response
  }, (error: unknown) => {
    if (axios.isAxiosError(error)) {
      release(error.config)
      if(stale(error.config))return Promise.reject(new axios.CanceledError('门店或会话已切换',error.config))
      if (error.response?.status === 401) options.onUnauthorized?.()
      if (error.response?.status === 405) error.message = '请求错误'
    }
    return Promise.reject(error)
  })
  return client
}
