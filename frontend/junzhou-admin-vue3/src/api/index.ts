import { request } from '../utils/request'
import { createAuthApi } from './auth'
import { createEmployeeApi } from './employee'
import { createCategoryApi } from './category'

export const authApi = createAuthApi(request)
export const employeeApi = createEmployeeApi(request)
export const categoryApi = createCategoryApi(request)
