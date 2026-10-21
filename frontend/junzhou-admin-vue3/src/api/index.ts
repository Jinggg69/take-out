import { request } from '../utils/request'
import { createAuthApi } from './auth'

export const authApi = createAuthApi(request)
