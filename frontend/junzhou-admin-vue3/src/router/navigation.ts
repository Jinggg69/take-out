export const primaryNavigation = [
  { path: '/dashboard', label: '工作台', icon: 'dashboard' },
  { path: '/statistics', label: '数据统计', icon: 'statistics' },
  { path: '/dining', label: '堂食桌台', icon: 'shop' },
  { path: '/order', label: '订单管理', icon: 'order' },
  { path: '/setmeal', label: '套餐管理', icon: 'setmeal' },
  { path: '/dish', label: '菜品管理', icon: 'dish' },
  { path: '/category', label: '分类管理', icon: 'category' },
  { path: '/member/list', label: '会员列表', icon: 'employee' },
  { path: '/member/levels', label: '会员等级', icon: 'employee' },
  { path: '/member/tags', label: '会员标签', icon: 'category' },
  { path: '/member/points', label: '积分流水', icon: 'statistics' },
  { path: '/member/recharge-plans', label: '储值管理', icon: 'order' },
  { path: '/member/coupons', label: '优惠券', icon: 'setmeal' },
  { path: '/member/settings', label: '会员设置', icon: 'shop' },
  { path: '/employee', label: '员工管理', icon: 'employee' },
  { path: '/access', label: '角色与任职', icon: 'employee' },
  { path: '/store', label: '门店设置与菜单', icon: 'shop' },
] as const


const productPaths = ['/dish', '/setmeal', '/category']
type NavigationItem = (typeof primaryNavigation)[number]
export interface SidebarItem {
  path: string
  label: string
  icon: NavigationItem['icon']
  children?: NavigationItem[]
}
export function sidebarNavigation(canVisit: (path: string) => boolean): SidebarItem[] {
  const result: SidebarItem[] = []
  for (const item of primaryNavigation) {
    if (item.path.startsWith('/member/')) {
      if (item.path !== '/member/list') continue
      const children = primaryNavigation.filter(child => child.path.startsWith('/member/') && canVisit(child.path))
      if (children.length) result.push({ path: 'members', label: '会员管理', icon: 'employee', children })
    } else if (productPaths.includes(item.path)) {
      if (item.path !== '/setmeal') continue
      const children = productPaths.flatMap(path => primaryNavigation.filter(child => child.path === path && canVisit(path)))
      if (children.length) result.push({ path: 'products', label: '商品管理', icon: 'category', children })
    } else if (canVisit(item.path)) result.push(item)
  }
  return result
}
export interface Breadcrumb { label: string; path?: string }
export function breadcrumbsFor(path: string, pageTitle: string, canVisit: (path: string) => boolean): Breadcrumb[] {
  if (path === '/dashboard') return [{ label: '首页' }]
  const result: Breadcrumb[] = canVisit('/dashboard') ? [{ label: '首页', path: '/dashboard' }] : []
  const parent = primaryNavigation.find(item => path === item.path || path.startsWith(item.path + '/'))
  if (parent && productPaths.includes(parent.path)) result.push({ label: '商品管理' })
  if (path.startsWith('/member/')) result.push({ label: '会员管理' })
  if (parent && parent.path !== path) result.push({ label: parent.label, ...(canVisit(parent.path) ? { path: parent.path } : {}) })
  result.push({ label: pageTitle || parent?.label || '管理系统' })
  return result
}
