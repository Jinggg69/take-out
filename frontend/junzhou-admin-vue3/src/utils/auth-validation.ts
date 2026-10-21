import type { LoginPayload, PasswordPayload } from '../api/types.ts'

export type FieldErrors<T> = Partial<Record<keyof T, string>>
export interface PasswordForm extends PasswordPayload { affirmPassword: string }

export function validateLogin(form: LoginPayload): FieldErrors<LoginPayload> {
  const errors: FieldErrors<LoginPayload> = {}
  if (!form.username) errors.username = '请输入账号'
  if (form.password.length < 6) errors.password = '密码至少需要6位'
  return errors
}

export function validatePasswordChange(form: PasswordForm): FieldErrors<PasswordForm> {
  const errors: FieldErrors<PasswordForm> = {}
  for (const field of ['oldPassword', 'newPassword'] as const) {
    if (!form[field]) errors[field] = field === 'oldPassword' ? '请输入原始密码' : '请输入新密码'
    else if (!/^[0-9A-Za-z]{6,20}$/.test(form[field])) errors[field] = '请输入6至20位数字或字母，区分大小写'
  }
  if (!form.affirmPassword) errors.affirmPassword = '请再次输入密码'
  else if (form.affirmPassword !== form.newPassword) errors.affirmPassword = '密码不一致，请重新输入密码'
  return errors
}
