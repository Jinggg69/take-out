import { request } from '../utils/request'
import { createAuthApi } from './auth'
import { createEmployeeApi } from './employee'
import { createCategoryApi } from './category'
import { createDishApi } from './dish'
import { createSetmealApi } from './setmeal'
import { createOrderApi } from './order'
import { createDashboardApi } from './dashboard'
import { createStatisticsApi } from './statistics'

export const authApi = createAuthApi(request)
export const employeeApi = createEmployeeApi(request)
export const categoryApi = createCategoryApi(request)
export const dishApi = createDishApi(request)
export const setmealApi = createSetmealApi(request)
export const orderApi = createOrderApi(request)
export const dashboardApi = createDashboardApi(request)
export const statisticsApi = createStatisticsApi(request)
