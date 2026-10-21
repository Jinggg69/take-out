import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { AuthApi } from '../api/auth.ts'
import type { LoginPayload } from '../api/types.ts'
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

    async function logout() {
      // As before, only a completed HTTP request permits clearing the session.
      await api.logout()
      storage.clearSession()
      token.value = ''
      username.value = ''
      userInfo.value = {}
      roles.value = []
    }
    return { token, username, userInfo, roles, displayName, login, logout }
  })
}
