import store from './../store'
import { baseUrl } from './env'
// 参数： url:请求地址  param：请求参数  method：请求方式 callBack：回调函数
export function request({url='', params={}, method='GET'}) {
	const storeInfo = store.state
	const selectedStoreId = String(storeInfo.selectedStoreId || 1)
	const generation = storeInfo.storeGeneration || 0
	const stale = () => selectedStoreId !== String(store.state.selectedStoreId || 1) || generation !== (store.state.storeGeneration || 0)
	const scopeError = () => ({ staleStore: true, msg: '门店已切换，请重试' })
	let header = {
			'Accept': 'application/json',
			'Access-Control-Allow-Origin':'*',
			'Content-Type': 'application/json', 
			'X-Store-Id': selectedStoreId,
			// 'shopid':storeInfo.storeInfo.shopId ?? '',
			// 'storeid':storeInfo.storeInfo.storeId ?? '',
			'authentication': storeInfo.token
		}
	
	const requestRes = new Promise((resolve, reject) => {
		store.commit('setLodding', false)
		 uni.request({
			url: baseUrl+url, 
			data: params,
			header: header,
			method: method,
			success: (res) => {
				if (stale()) { reject(scopeError()); return }
				const { data } = res
				if (res.statusCode >= 400) {
					reject({ ...(data && typeof data === 'object' ? data : {}), statusCode: res.statusCode,
						msg: (data && data.msg) || (res.statusCode === 401 ? '请重新登录' : '请求失败，请稍后重试') })
					return
				}
				if (data && (data.code == 200 || data.code === 1)) {
					// store.commit('setLodding', false)
					resolve(res.data)
				}else{
					// store.commit('setLodding', true)
					reject({ ...(data && typeof data === 'object' ? data : {}), statusCode: res.statusCode,
						msg: (data && data.msg) || '响应数据异常，请稍后重试' })
				}
			},
			fail: (err) => {
				if (stale()) { reject(scopeError()); return }
				const error = { msg: '网络请求失败，请稍后重试', errMsg: err.errMsg, data: { msg: err.data } }
				// store.commit('setLodding', true)
				reject(error)
			}
		});
	})
	return requestRes
}

