import { ref, computed, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAppStore } from '../store/index.js'
import { memberMe, memberRecords } from './member-api.js'
import { createClientRequestId } from '../utils/order-submission.js'

// Each mounted view clears synchronously on identity or context changes. The
// transport also rejects old responses; the stamp covers chained page requests.
export function useMemberContext(reset = () => {}, withCouponCount = false) {
  const app = useAppStore(), profile = ref(null), loading = ref(false), busy = ref(false), error = ref(''), couponCount = ref(null)
  const scope = computed(() => [app.selectedMerchantId, app.selectedStoreId, app.storeGeneration, app.userId, app.authEpoch, app.token].join(':'))
  const keys = new Map()
  let loadVersion = 0
  const current = stamp => stamp === scope.value
  watch(scope, () => { loadVersion++; couponCount.value = null; profile.value = null; loading.value = false; busy.value = false; error.value = ''; keys.clear(); reset() }, { flush: 'sync' })
  async function load() {
    const stamp = scope.value, run = ++loadVersion
    if (!app.selectedStoreId || !app.token) return false
    loading.value = true; error.value = ''
    try { const r = await memberMe(); if (!current(stamp) || run !== loadVersion) return false; profile.value = r.data
      if (withCouponCount && r.data?.joined) {
        try { const c = await memberRecords('coupons', { status: 'AVAILABLE', page: 1, pageSize: 1 }); if (current(stamp) && run === loadVersion) couponCount.value = Number(c.data.total) }
        catch { if (current(stamp) && run === loadVersion) couponCount.value = null }
      }
      return current(stamp) && run === loadVersion }
    catch (e) { if (current(stamp) && run === loadVersion) { profile.value = null; error.value = e.msg || e.message || '会员信息加载失败' } return false }
    finally { if (current(stamp) && run === loadVersion) loading.value = false }
  }
  async function action(key, operation, after = load) {
    if (busy.value || !app.token || !app.selectedStoreId) return
    const stamp = scope.value
    busy.value = true; error.value = ''
    if (!keys.has(key)) keys.set(key, createClientRequestId())
    try { const r = await operation(keys.get(key)); if (!current(stamp)) return; keys.delete(key); await after(r); return r }
    catch (e) { if (current(stamp)) error.value = e.msg || e.message || '操作未完成，请重试' }
    finally { if (current(stamp)) busy.value = false }
  }
  async function login() { try { await app.ensureLogin(); await load() } catch (e) { error.value = e.msg || e.message || '登录未完成' } }
  onShow(load)
  return { app, profile, couponCount, loading, busy, error, scope, current, load, action, login }
}
export const yuan = value => value == null || !Number.isFinite(Number(value)) ? '—' : Number(value).toFixed(2)
export const couponValue = c => c.type === 'DISCOUNT' ? Number(c.value) / 10 + '折' : '¥' + yuan(c.value)
export const statusText = status => ({ PENDING: '待确认', CREATED: '待确认', SUCCESS: '已成功', PAID: '已成功', FAIL: '失败', FAILED: '失败', CANCEL: '已取消', CANCELLED: '已取消', REFUNDED: '已退款', AVAILABLE: '可用', USED: '已使用', EXPIRED: '已过期', HELD: '已冻结', FROZEN: '已冻结', VOID: '已作废' }[status] || status || '—')

export const ledgerAccount = item => ({ POINTS: '可用积分', POINTS_DEBT: '待扣积分', PRINCIPAL: '本金余额', GIFT: '赠送余额' }[item.assetType] || '账户')
export function ledgerChange(item, kind) {
  const delta = Number(item.delta)
  if (item.assetType === 'POINTS_DEBT') return (delta > 0 ? '新增待扣 ' : delta < 0 ? '已冲抵 ' : '待扣变更 ') + Math.abs(delta)
  return (delta > 0 ? '+' : '') + (kind === 'points' ? item.delta : '¥' + yuan(item.delta))
}
export const ledgerAfter = (item, kind) => ledgerAccount(item) + (item.assetType === 'POINTS_DEBT' ? '剩余 ' : '变更后 ') + (kind === 'points' ? item.afterValue : '¥' + yuan(item.afterValue))
