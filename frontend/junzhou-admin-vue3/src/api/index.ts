import { request } from '../utils/request'
import { createAuthApi } from './auth'
import { createEmployeeApi } from './employee'
import { createCategoryApi } from './category'
import { createDishApi } from './dish'

export const authApi = createAuthApi(request)
export const employeeApi = createEmployeeApi(request)
export const categoryApi = createCategoryApi(request)
export const dishApi = createDishApi(request)
