import { createRouter, createWebHashHistory } from 'vue-router'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sessionCookies } from '../utils/cookies'
import { createAuthGuard } from './guard'
import { primaryNavigation } from './navigation'

export const routePending = ref(false)

export const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0, left: 0 }),
  routes: [
    {
      path: '/login', name: 'login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { title: '登录 · 均州餐饮点餐营销管理系统', notNeedAuth: true },
    },
    {
      path: '/', redirect: () => sessionCookies.getUserInfo().accountType === 'PLATFORM' ? '/platform/tenants' : '/dashboard',
      meta: { title: '管理系统', accountType: 'EMPLOYEE' },
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        ...primaryNavigation.map(item => ({
          path: item.path.slice(1), name: item.path.slice(1),
          component: item.path === '/member/list'
            ? () => import('@/views/member/MemberListView.vue')
            : item.path === '/member/settings'
            ? () => import('@/views/member/SettingsView.vue')
            : item.path.startsWith('/member/')
            ? () => import('@/views/member/ResourceView.vue')
            : item.path === '/points'
            ? () => import('@/views/points/PointsView.vue')
            : item.path === '/decoration'
            ? () => import('@/views/decoration/DecorationView.vue')
            : item.path === '/dining'
            ? () => import('@/views/order/DiningView.vue')
            : item.path === '/store'
            ? () => import('@/views/store/StoreView.vue')
            : item.path === '/access'
            ? () => import('@/views/access/AccessView.vue')
            : item.path === '/employee'
            ? () => import('@/views/employee/EmployeeListView.vue')
            : item.path === '/category'
              ? () => import('@/views/category/CategoryListView.vue')
              : item.path === '/dish'
                ? () => import('@/views/dish/DishListView.vue')
                : item.path === '/setmeal'
                  ? () => import('@/views/setmeal/SetmealListView.vue')
                  : item.path === '/order'
                    ? () => import('@/views/order/OrderListView.vue')
                    : item.path === '/dashboard'
                      ? () => import('@/views/dashboard/DashboardView.vue')
                      : () => import('@/views/statistics/StatisticsView.vue'),
          props: item.path.startsWith('/member/') && item.path !== '/member/settings' ? { resource: item.path.split('/')[2] } : undefined,
          meta: { title: `${item.label} · 均州餐饮点餐营销管理系统`, pageTitle: item.label, activeMenu: item.path },
        })),
        { path: 'member/points', name: 'member/points', redirect: { path: '/points', query: { tab: 'ledger' } } },
        {
          path: 'setmeal/add', name: 'setmeal-form',
          component: () => import('@/views/setmeal/SetmealFormView.vue'),
          meta: { title: '新建套餐 · 均州餐饮点餐营销管理系统', pageTitle: '新建套餐', activeMenu: '/setmeal' },
        },
        {
          path: 'dish/add', name: 'dish-form',
          component: () => import('@/views/dish/DishFormView.vue'),
          meta: { title: '新建菜品 · 均州餐饮点餐营销管理系统', pageTitle: '新建菜品', activeMenu: '/dish' },
        },
        {
          path: 'employee/add', name: 'employee-form',
          component: () => import('@/views/employee/EmployeeFormView.vue'),
          meta: { title: '添加员工 · 均州餐饮点餐营销管理系统', pageTitle: '添加员工', activeMenu: '/employee' },
        },
      ],
    },
    {
      path: '/platform', redirect: '/platform/tenants',
      component: () => import('@/layouts/PlatformLayout.vue'),
      meta: { title: '平台管理', accountType: 'PLATFORM' },
      children: ['tenants', 'stores'].map(resource => ({
        path: resource, name: `platform-${resource}`,
        component: () => import('@/views/platform/PlatformManagement.vue'),
        props: { resource },
        meta: { title: `${resource === 'tenants' ? '商户管理' : '门店管理'} · 均州餐饮点餐营销管理系统`, accountType: 'PLATFORM' as const },
      })),
    },
    {
      path: '/404',
      name: 'not-found',
      component: () => import('@/views/error/NotFoundView.vue'),
      meta: { title: '页面不存在 · 均州餐饮点餐营销管理系统', notNeedAuth: true },
    },
    { path: '/:pathMatch(.*)*', redirect: '/404' },
  ],
})

const authGuard = createAuthGuard(sessionCookies.getToken, () => sessionCookies.getUserInfo().accountType === 'PLATFORM' ? 'PLATFORM' : 'EMPLOYEE')
router.beforeEach(to => {
  routePending.value = true
  if (to.name === 'employee-form') {
    to.meta.pageTitle = to.query.id ? '修改员工信息' : '添加员工'
    to.meta.title = `${to.meta.pageTitle} · 均州餐饮点餐营销管理系统`
  }
  if (to.name === 'dish-form') {
    to.meta.pageTitle = to.query.id ? '修改菜品' : '新建菜品'
    to.meta.title = `${to.meta.pageTitle} · 均州餐饮点餐营销管理系统`
  }
  if (to.name === 'setmeal-form') {
    to.meta.pageTitle = to.query.id ? '修改套餐' : '新建套餐'
    to.meta.title = `${to.meta.pageTitle} · 均州餐饮点餐营销管理系统`
  }
  return authGuard(to)
})
router.afterEach(to => { document.title = to.meta.title; routePending.value = false })
router.onError(() => { routePending.value = false; ElMessage.error('页面加载失败，请刷新后重试') })
