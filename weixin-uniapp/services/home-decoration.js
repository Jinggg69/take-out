export const HOME_ACCENT = Object.freeze({ DEFAULT: '#176B4B', BURGUNDY: '#8F2D32', NAVY: '#28466B', TEAL: '#1F6464' })
const types = ['BANNER', 'NOTICE', 'ACTIVITY']
const targets = ['NONE', 'MENU', 'MEMBER', 'COUPONS']

function validModule(module) {
  if (!module || !types.includes(module.type) || typeof module.enabled !== 'boolean') return false
  if (!module.enabled) return true
  if (module.type === 'NOTICE') return typeof module.text === 'string' && Array.from(module.text).length <= 100
  const isBanner = module.type === 'BANNER'
  return Array.isArray(module.items) && module.items.length <= (isBanner ? 5 : 4) &&
    module.items.every(item => item && typeof item.title === 'string' && item.title.trim().length > 0 &&
      Array.from(item.title).length <= 30 && targets.includes(item.target) &&
      (!isBanner || (typeof item.imageUrl === 'string' && /^https:\/\/[^/\s]+\/\S+$/.test(item.imageUrl))))
}

export function readHomeDecoration(payload, merchantId) {
  if (String(payload?.merchantId) !== String(merchantId)) return null
  const config = payload?.config
  if (!config || typeof config.theme !== 'string' || !Object.prototype.hasOwnProperty.call(HOME_ACCENT, config.theme) ||
      !Array.isArray(config.modules) || config.modules.length !== types.length ||
      !config.modules.every(validModule) || new Set(config.modules.map(module => module.type)).size !== types.length) return null
  return config
}

export function createHomeDecorationLoader(getSelection, fetchDecoration, display) {
  let current
  const keyOf = ({ storeId, merchantId, generation }) => JSON.stringify([storeId, merchantId, generation])
  return function load() {
    const selection = getSelection()
    const key = keyOf(selection)
    if (current?.key !== key) display(null)
    if (current?.key === key && current.pending) return current.pending
    const request = { key, pending: null }
    current = request
    if (!selection.storeId || !selection.merchantId) return Promise.resolve()
    const receive = config => {
      if (current === request && keyOf(getSelection()) === key) display(config)
    }
    request.pending = fetchDecoration(selection.storeId)
      .then(response => receive(readHomeDecoration(response.data, selection.merchantId)))
      .catch(() => receive(null))
      .finally(() => { request.pending = null })
    return request.pending
  }
}
