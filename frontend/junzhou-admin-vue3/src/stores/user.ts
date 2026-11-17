import { workspace } from './workspace.ts'
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { AuthApi } from '../api/auth.ts'
import type { LoginPayload, PasswordPayload } from '../api/types.ts'
import type { SessionStorage } from '../utils/cookies.ts'
import type { createPlatformAuthApi } from '../api/platform.ts'

export function createUserStore(api: AuthApi, storage: SessionStorage, platformApi?: ReturnType<typeof createPlatformAuthApi>) {
  return defineStore('user', () => {
    const token = ref(storage.getToken() || '')
    const username = ref(storage.getUsername())
    const userInfo = ref(storage.getUserInfo())
    const roles = ref<string[]>([])
    const accountType = computed(() => userInfo.value.accountType === 'PLATFORM' ? 'PLATFORM' : 'EMPLOYEE')
    const identityApi = () => {
      if (accountType.value !== 'PLATFORM') return api
      if (!platformApi) throw new Error('平台认证服务未配置，请刷新后重试')
      return platformApi
    }
    let version = 0
    const displayName = computed(() => typeof userInfo.value.name === 'string' && userInfo.value.name
      ? userInfo.value.name : username.value || '员工')

    async function login(payload: LoginPayload) {
      const current = ++version
      workspace.reset()
      const name = payload.username.trim()
      username.value = name
      storage.setUsername(name)
      const { data: result } = await api.login({ username: name, password: payload.password, ...(payload.captcha !== undefined ? { captcha: payload.captcha } : {}) })
      if (current !== version) throw new Error('会话已切换，请重新登录')
      if (String(result.code) !== '1' || !result.data || !result.data.token) {
        throw new Error(result.msg || '登录失败，请稍后重试')
      }
      storage.saveUser(result.data)
      token.value = result.data.token
      userInfo.value = { ...result.data }
      return result
    }

    function clearSession() {
      version++
      workspace.reset()
      storage.clearSession()
      token.value = ''
      username.value = ''
      userInfo.value = {}
      roles.value = []
    }

    async function logout() {
      const current = version
      const { data: result } = await identityApi().logout()
      if (current !== version) return
      if (String(result.code) !== '1') throw new Error(result.msg || '退出失败，请重试')
      clearSession()
    }

    async function changePassword(payload: PasswordPayload) {
      const current = version
      const { data: result } = await identityApi().editPassword(payload)
      if (current !== version) throw new Error('会话已切换')
      if (String(result.code) !== '1') throw new Error(result.msg || '密码修改失败，请重试')
      // The backend revokes every existing session after a successful password change.
      clearSession()
      return result
    }
    return { token, username, userInfo, roles, accountType, displayName, login, logout, changePassword, clearSession }
  })
}
