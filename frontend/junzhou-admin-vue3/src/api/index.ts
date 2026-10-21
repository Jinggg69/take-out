import { request } from '../utils/request'
import { createAuthApi } from './auth'
import { createEmployeeApi } from './employee'

export const authApi = createAuthApi(request)
export const employeeApi = createEmployeeApi(request)
