import type { AxiosInstance } from 'axios'
import type { ApiResult } from './types.ts'

export const decorationThemes = {
  DEFAULT: { label: '默认绿', color: '#176B4B' },
  BURGUNDY: { label: '酒红', color: '#8F2D32' },
  NAVY: { label: '深蓝', color: '#28466B' },
  TEAL: { label: '墨青', color: '#1F6464' },
} as const
export const decorationTargets = { NONE: '仅展示', MENU: '去点餐', MEMBER: '会员中心', COUPONS: '优惠券中心' } as const
export const decorationModuleLabels = { BANNER: '轮播图', NOTICE: '公告', ACTIVITY: '活动入口' } as const
export type DecorationTarget = keyof typeof decorationTargets
export interface DecorationLink { title: string; target: DecorationTarget }
export interface DecorationBanner extends DecorationLink { imageUrl: string }
export type DecorationModule = { enabled: boolean } & (
  { type: 'BANNER'; items: DecorationBanner[] } |
  { type: 'NOTICE'; text: string } |
  { type: 'ACTIVITY'; items: DecorationLink[] }
)
export interface DecorationConfig { theme: keyof typeof decorationThemes; modules: DecorationModule[] }
export interface DecorationState { version: number; draft: DecorationConfig; published: DecorationConfig | null }

function decorationContent(config: DecorationConfig) {
  return [config.theme, config.modules.map(module => {
    const content = module.type === 'NOTICE' ? module.text : module.type === 'BANNER'
      ? module.items.map(item => [item.title, item.target, item.imageUrl])
      : module.items.map(item => [item.title, item.target])
    return [module.type, module.enabled, content]
  })]
}

export function sameDecoration(left: DecorationConfig, right: DecorationConfig | null | undefined): boolean {
  return Boolean(right && JSON.stringify(decorationContent(left)) === JSON.stringify(decorationContent(right)))
}

export function decorationPublishError(config: DecorationConfig): string {
  for (const module of config.modules) {
    if (!module.enabled) continue
    if (module.type === 'NOTICE') {
      if (!module.text.trim()) return '请输入公告内容，或关闭公告模块。'
    } else if (module.type === 'BANNER') {
      if (!module.items.length || module.items.some(item => !item.imageUrl || !item.title.trim())) {
        return '请为轮播图添加图片和标题，或关闭轮播图模块。'
      }
    } else if (!module.items.length || module.items.some(item => !item.title.trim())) {
      return '请填写活动入口标题，或关闭活动入口模块。'
    }
  }
  return ''
}

export function createDecorationApi(client: AxiosInstance) {
  const home = '/decoration/home'
  return {
    load: () => client.get<ApiResult<DecorationState>>(home),
    save: (version: number, config: DecorationConfig) => client.put<ApiResult<DecorationState>>(home, { version, config }),
    publish: (version: number) => client.post<ApiResult<DecorationState>>(`${home}/publish`, { version }),
    reset: (version: number) => client.post<ApiResult<DecorationState>>(`${home}/reset`, { version }),
  }
}
