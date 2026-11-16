import { createPinia } from 'pinia'
import { authApi } from '../api'
import { sessionCookies } from '../utils/cookies'
import { createUserStore } from './user'
import { createAppStore } from './app'
import { createPlatformAuthApi } from '../api/platform'
import { platformRequest } from '../utils/platform-request'

export const pinia = createPinia()
export const useUserStore = createUserStore(authApi, sessionCookies, createPlatformAuthApi(platformRequest))
export const useAppStore = createAppStore(sessionCookies)
