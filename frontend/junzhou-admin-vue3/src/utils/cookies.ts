import Cookies from 'js-cookie'
import type { LoginUser } from '../api/types.ts'

export interface CookieJar {
  get(name: string): string | undefined
  set(name: string, value: string): unknown
  remove(name: string): unknown
}

export function createSessionStorage(jar: CookieJar = Cookies) {
  return {
    getToken: () => jar.get('token'),
    getUsername: () => jar.get('username') || '',
    setUsername: (name: string) => { jar.set('username', name) },
    getUserInfo: (): Record<string, unknown> => {
      try {
        const value: unknown = JSON.parse(jar.get('user_info') || '{}')
        return value !== null && typeof value === 'object' && !Array.isArray(value)
          ? value as Record<string, unknown> : {}
      } catch { return {} }
    },
    saveUser: (user: LoginUser) => {
      jar.set('token', user.token)
      // js-cookie 2 serialized objects implicitly; version 3 needs explicit JSON.
      jar.set('user_info', JSON.stringify(user))
    },
    clearSession: () => {
      for (const name of ['token', 'username', 'user_info', 'userInfo']) jar.remove(name)
    },
    getSidebarStatus: () => jar.get('sidebar_status'),
    setSidebarStatus: (opened: boolean) => { jar.set('sidebar_status', opened ? 'opened' : 'closed') },
  }
}

export const sessionCookies = createSessionStorage()
export type SessionStorage = ReturnType<typeof createSessionStorage>
