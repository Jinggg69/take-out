import type { MemberBody, MemberResource } from '../../api/member.ts'

export function required(value: unknown, label: string) { const result = String(value ?? '').trim(); if (!result) throw new Error(`请填写${label}`); return result }
export function serverDate(value: unknown) { const text = String(value ?? '').replace('T', ' ').slice(0, 19); return /^\d{4}-\d\d-\d\d \d\d:\d\d$/.test(text) ? `${text}:00` : text }
function number(value: unknown, label: string, min = 0, max = Number.MAX_SAFE_INTEGER, integer = false) {
  const text = String(value ?? '')
  if (!(integer ? /^\d+$/ : /^\d+(\.\d{1,2})?$/).test(text)) throw new Error(`${label}格式不正确${integer ? '，须为整数' : '，最多两位小数'}`)
  const result = Number(text)
  if (!Number.isSafeInteger(integer ? result : Math.round(result * 100)) || result < min || result > max) throw new Error(`${label}须在 ${min} 至 ${max} 之间`)
  return result
}
export function memberPayload(draft: MemberBody) {
  const phone = String(draft.phone ?? '').trim()
  if (phone && !/^1\d{10}$/.test(phone)) throw new Error('请输入11位手机号')
  return { name: required(draft.name, '会员姓名'), phone, birthday: draft.birthday ?? '', remark: String(draft.remark ?? '').trim(), levelId: draft.levelId || undefined, tagIds: draft.tagIds ?? [], version: draft.version }
}
export function adjustmentPayload(kind: 'points' | 'balance', value: unknown, reason: unknown, requestId: string) {
  const text = String(value ?? '')
  if (!(kind === 'points' ? /^-?\d+$/ : /^-?\d+(\.\d{1,2})?$/).test(text)) throw new Error(kind === 'points' ? '积分须为整数' : '金额最多两位小数')
  const amount = Number(text)
  if (!Number.isSafeInteger(kind === 'points' ? amount : Math.round(amount * 100)) || amount === 0) throw new Error('调整数值须为非零有效数字')
  return { [kind === 'points' ? 'delta' : 'amount']: amount, reason: required(reason, '调整原因'), requestId: required(requestId, '业务号') }
}
export function resourcePayload(kind: MemberResource, d: MemberBody): MemberBody {
  const base = { name: required(d.name, '名称'), status: d.status === 0 ? 0 : 1, version: d.version }
  if (kind === 'tags') return base
  if (kind === 'levels') return { ...base, threshold: number(d.threshold, '升级门槛'), discountPercent: number(d.discountPercent, '折扣百分比', 1, 100, true) }
  if (kind === 'recharge-plans') return { ...base, amount: number(d.amount, '充值本金', 0.01), giftAmount: number(d.giftAmount, '赠送金额') }
  const startTime = serverDate(required(d.startTime, '开始时间')), endTime = serverDate(required(d.endTime, '结束时间'))
  if (![startTime, endTime].every(time => /^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d$/.test(time)) || endTime <= startTime) throw new Error('有效期结束时间须晚于开始时间')
  if (!['FIXED', 'DISCOUNT'].includes(String(d.type))) throw new Error('请选择优惠券类型')
  return { ...base, type: d.type, value: number(d.value, '优惠值', d.type === 'DISCOUNT' ? 1 : 0.01, d.type === 'DISCOUNT' ? 100 : Number.MAX_SAFE_INTEGER, d.type === 'DISCOUNT'), minSpend: number(d.minSpend, '使用门槛'), maxDiscount: d.maxDiscount === '' || d.maxDiscount == null ? undefined : number(d.maxDiscount, '最高优惠'), startTime, endTime, totalStock: number(d.totalStock, '发行总量', 1, 100000000, true), perMemberLimit: number(d.perMemberLimit, '每人限领', 1, 100000, true), pointsCost: number(d.pointsCost, '兑换积分', 0, 100000000, true), storeIds: d.storeIds ?? [], fulfillmentTypes: d.fulfillmentTypes ?? [] }
}
export function settingsPayload(d: MemberBody) { return { earnPerYuan: number(d.earnPerYuan, '每元赠送积分', 0, Number.MAX_SAFE_INTEGER, true), pointsPerYuan: number(d.pointsPerYuan, '每元抵扣积分', 1, Number.MAX_SAFE_INTEGER, true), maxDeductionPercent: number(d.maxDeductionPercent, '抵扣上限', 0, 100), allowCouponWithPoints: Boolean(d.allowCouponWithPoints), allowCouponWithLevel: Boolean(d.allowCouponWithLevel), version: d.version } }
