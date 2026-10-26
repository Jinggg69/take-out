import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { AuthApi } from '../api/auth.ts'
import type { LoginPayload, PasswordPayload } from '../api/types.ts'
import type { SessionStorage } from '../utils/cookies.ts'

export function createUserStore(api: AuthApi, storage: SessionStorage) {
  return defineStore('user', () => {
    const token = ref(storage.getToken() || '')
    const username = ref(storage.getUsername())
    const userInfo = ref(storage.getUserInfo())
    const roles = ref<string[]>([])
    const displayName = computed(() => typeof userInfo.value.name === 'string' && userInfo.value.name
      ? userInfo.value.name : username.value || '员工')

    async function login(payload: LoginPayload) {
      const name = payload.username.trim()
      username.value = name
      storage.setUsername(name)
      const { data: result } = await api.login({ username: name, password: payload.password })
      if (String(result.code) !== '1' || !result.data || !result.data.token) {
        throw new Error(result.msg || '登录失败，请稍后重试')
      }
      storage.saveUser(result.data)
      token.value = result.data.token
      userInfo.value = { ...result.data }
      return result
    }

    function clearSession() {
      storage.clearSession()
      token.value = ''
      username.value = ''
      userInfo.value = {}
      roles.value = []
    }

    async function logout() {
      const { data: result } = await api.logout()
      if (String(result.code) !== '1') throw new Error(result.msg || '退出失败，请重试')
      clearSession()
    }

    async function changePassword(payload: PasswordPayload) {
      const { data: result } = await api.editPassword(payload)
      if (String(result.code) !== '1') throw new Error(result.msg || '密码修改失败，请重试')
      // The backend revokes every existing session after a successful password change.
      clearSession()
      return result
    }
    return { token, username, userInfo, roles, displayName, login, logout, changePassword, clearSession }
  })
}
