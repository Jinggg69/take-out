import type { PlatformFields, PlatformResource } from '../../api/platform.ts'

export function validatePlatformForm(form: PlatformFields, resource: PlatformResource, editing: boolean) {
  const errors: Record<string, string> = {}
  function required(key: string, value: unknown, label: string) { if (!String(value ?? '').trim()) errors[key] = `请输入${label}` }
  function coordinates(prefix: string, data: { latitude?: number | null; longitude?: number | null }) {
    if ((data.latitude == null) !== (data.longitude == null)) errors[prefix + 'latitude'] = '经纬度必须同时填写或同时留空'
    for (const [key, limit] of [['latitude', 90], ['longitude', 180]] as const) {
      const value = data[key]
      if (value != null && (!Number.isFinite(value) || Math.abs(value) > limit)) errors[prefix + key] = `请输入有效${key === 'latitude' ? '纬度（-90 至 90）' : '经度（-180 至 180）'}`
    }
  }
  required('code', form.code, '编码'); required('name', form.name, '名称')
  if (form.code && !/^[A-Za-z0-9_-]{1,64}$/.test(form.code.trim())) errors.code = '编码限64位以内字母、数字、下划线或连字符'
  if (form.name.length > 100) errors.name = '名称最多100位'
  if (resource === 'tenants' && !editing) {
    required('adminUsername', form.adminUsername, '管理员账号'); required('adminName', form.adminName, '管理员姓名')
    if (/^platform_/i.test(form.adminUsername ?? '')) errors.adminUsername = 'platform_ 前缀为平台账号保留，请使用其他账号'
    if (!form.adminPassword || form.adminPassword.length < 8 || form.adminPassword.length > 128) errors.adminPassword = '初始密码需要8至128位'
    if ((form.adminUsername?.length ?? 0) > 32) errors.adminUsername = '管理员账号最多32位'
    if ((form.adminName?.length ?? 0) > 32) errors.adminName = '管理员姓名最多32位'
    required('store.code', form.store?.code, '首店编码'); required('store.name', form.store?.name, '首店名称')
    if (form.store?.code && !/^[A-Za-z0-9_-]{1,64}$/.test(form.store.code.trim())) errors['store.code'] = '编码限64位以内字母、数字、下划线或连字符'
    coordinates('store.', form.store ?? {})
  }
  if (resource === 'stores') {
    required('tenantId', form.tenantId, '所属商户 ID')
    if (form.tenantId && !/^[1-9]\d*$/.test(String(form.tenantId))) errors.tenantId = '商户 ID 必须为正整数'
    coordinates('', form)
  }
  return errors
}
