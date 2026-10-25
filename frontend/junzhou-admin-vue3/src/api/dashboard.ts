import type { AxiosInstance } from 'axios'
import type { ApiResult } from './types.ts'

export type DashboardFigure = number | string | null
export interface BusinessOverview {
  turnover: DashboardFigure; validOrderCount: DashboardFigure; orderCompletionRate: DashboardFigure
  unitPrice: DashboardFigure; newUsers: DashboardFigure
}
export interface OrdersOverview {
  waitingOrders: DashboardFigure; deliveredOrders: DashboardFigure; completedOrders: DashboardFigure
  cancelledOrders: DashboardFigure; allOrders: DashboardFigure
}
export interface SalesOverview { sold: DashboardFigure; discontinued: DashboardFigure }
export function createDashboardApi(client: AxiosInstance) {
  return {
    getBusinessData: () => client.get<ApiResult<BusinessOverview>>('/workspace/businessData'),
    getOrdersOverview: () => client.get<ApiResult<OrdersOverview>>('/workspace/overviewOrders'),
    getDishesOverview: () => client.get<ApiResult<SalesOverview>>('/workspace/overviewDishes'),
    getSetmealsOverview: () => client.get<ApiResult<SalesOverview>>('/workspace/overviewSetmeals'),
  }
}
