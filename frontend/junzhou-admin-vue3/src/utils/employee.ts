import type { EmployeeFields, EmployeeForm, EmployeePayload, EmployeeRecord } from '../api/employee.ts'
import type { FieldErrors } from './auth-validation.ts'

export function createEmployeeForm(): EmployeeForm {
  return { username: '', name: '', phone: '', sex: '男', idNumber: '' }
}
export function employeeFormFromDetail(detail: EmployeeRecord): EmployeeForm {
  return { ...detail, sex: detail.sex === '0' ? '女' : '男' }
}
export function employeePayload(form: EmployeeForm): EmployeePayload {
  return { ...form, sex: form.sex === '女' ? '0' : '1' }
}
export function validateEmployee(form: EmployeeFields): FieldErrors<EmployeeFields> {
  const errors: FieldErrors<EmployeeFields> = {}
  if (!form.username) errors.username = '请输入账号'
  else if (!/^[a-z0-9]{3,20}$/.test(form.username)) errors.username = '账号输入不符，请输入3至20位小写字母或数字'
  if (!form.name) errors.name = '请输入员工姓名'
  if (!form.phone) errors.phone = '请输入手机号'
  else if (!/^1(3|4|5|6|7|8)\d{9}$/.test(form.phone)) errors.phone = '请输入正确的手机号'
  if (!form.idNumber) errors.idNumber = '请输入身份证号码'
  else if (!/(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/.test(form.idNumber)) errors.idNumber = '身份证号码不正确'
  return errors
}
export function canManageEmployee(employee: Pick<EmployeeRecord, 'username'>): boolean {
  return employee.username !== 'admin'
}
export function nextEmployeeStatus(status: EmployeeRecord['status']): 0 | 1 {
  return String(status) === '1' ? 0 : 1
}
