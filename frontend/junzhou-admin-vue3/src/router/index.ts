import { createRouter, createWebHashHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0, left: 0 }),
  routes: [
    {
      path: '/',
      name: 'welcome',
      component: () => import('@/views/WelcomeView.vue'),
      meta: { title: '均州鱼庄管理系统', notNeedAuth: true },
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

router.afterEach(to => { document.title = to.meta.title })
