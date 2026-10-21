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
      meta: { title: '登录 · 均州鱼庄管理系统', notNeedAuth: true },
    },
    {
      path: '/', redirect: '/dashboard',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        ...primaryNavigation.map(item => ({
          path: item.path.slice(1), name: item.icon,
          component: item.path === '/employee'
            ? () => import('@/views/employee/EmployeeListView.vue')
            : () => import('@/views/ModulePendingView.vue'),
          meta: { title: `${item.label} · 均州鱼庄管理系统`, pageTitle: item.label, activeMenu: item.path },
        })),
        ...[
          { path: 'dish/add', title: '添加菜品', menu: '/dish' },
          { path: 'setmeal/add', title: '添加套餐', menu: '/setmeal' },
        ].map(item => ({
          path: item.path,
          component: () => import('@/views/ModulePendingView.vue'),
          meta: { title: `${item.title} · 均州鱼庄管理系统`, pageTitle: item.title, activeMenu: item.menu },
        })),
        {
          path: 'employee/add', name: 'employee-form',
          component: () => import('@/views/employee/EmployeeFormView.vue'),
          meta: { title: '添加员工 · 均州鱼庄管理系统', pageTitle: '添加员工', activeMenu: '/employee' },
        },
      ],
    },
    {
      path: '/404',
      name: 'not-found',
      component: () => import('@/views/error/NotFoundView.vue'),
      meta: { title: '页面不存在 · 均州鱼庄管理系统', notNeedAuth: true },
    },
    { path: '/:pathMatch(.*)*', redirect: '/404' },
  ],
})

const authGuard = createAuthGuard(sessionCookies.getToken)
router.beforeEach(to => {
  routePending.value = true
  if (to.name === 'employee-form') {
    to.meta.pageTitle = to.query.id ? '修改员工信息' : '添加员工'
    to.meta.title = `${to.meta.pageTitle} · 均州鱼庄管理系统`
  }
  return authGuard(to)
})
router.afterEach(to => { document.title = to.meta.title; routePending.value = false })
router.onError(() => { routePending.value = false; ElMessage.error('页面加载失败，请刷新后重试') })
