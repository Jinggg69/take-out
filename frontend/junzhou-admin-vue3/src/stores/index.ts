import { createPinia } from 'pinia'
import { authApi } from '../api'
import { sessionCookies } from '../utils/cookies'
import { createUserStore } from './user'
import { createAppStore } from './app'

export const pinia = createPinia()
export const useUserStore = createUserStore(authApi, sessionCookies)
export const useAppStore = createAppStore(sessionCookies)
