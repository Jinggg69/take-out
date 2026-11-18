<script setup lang="ts">
import { ref } from 'vue'
import { decorationThemes, type DecorationConfig } from '@/api/decoration'

defineProps<{ config: DecorationConfig; storeName: string }>()
const currentSlide = ref(0)
const brokenImages = ref<string[]>([])
</script>

<template>
  <aside class="management-panel preview">
    <h2>手机预览</h2>
    <div
      class="phone"
      :style="{ '--preview-accent': decorationThemes[config.theme].color }"
    >
      <div class="phone-title">
        首页
      </div>
      <div class="phone-content">
        <div class="phone-panel store-name">
          {{ storeName }}
        </div>
        <template
          v-for="module in config.modules"
          :key="module.type"
        >
          <template v-if="module.enabled">
            <div
              v-if="module.type === 'BANNER' && module.items.length"
              class="preview-banner"
            >
              <template
                v-for="(item, index) in module.items"
                :key="index"
              >
                <div v-if="index === Math.min(currentSlide, module.items.length - 1)">
                  <img
                    v-if="item.imageUrl && !brokenImages.includes(item.imageUrl)"
                    :src="item.imageUrl"
                    :alt="item.title || '轮播图预览'"
                    @error="brokenImages.push(item.imageUrl)"
                  />
                  <div
                    v-else
                    class="image-placeholder"
                  >
                    {{ item.imageUrl ? '图片暂时无法显示' : '待上传图片' }}
                  </div>
                  <div
                    v-if="item.title"
                    class="banner-caption"
                  >
                    <span>{{ item.title }}</span>
                  </div>
                </div>
              </template>
              <nav
                v-if="module.items.length > 1"
                class="banner-pages"
                aria-label="轮播图片预览"
              >
                <button
                  v-for="(_, index) in module.items"
                  :key="index"
                  type="button"
                  :aria-label="`预览第 ${index + 1} 张图片`"
                  :aria-pressed="index === Math.min(currentSlide, module.items.length - 1)"
                  @click="currentSlide = index"
                >
                  {{ index + 1 }}
                </button>
              </nav>
            </div>
            <div
              v-else-if="module.type === 'NOTICE' && module.text.trim()"
              class="phone-panel notice"
            >
              <strong>公告</strong>
              <span>{{ module.text }}</span>
            </div>
            <div
              v-else-if="module.type === 'ACTIVITY' && module.items.length"
              class="phone-panel campaign-preview"
            >
              <strong>门店活动</strong>
              <div class="campaign-grid">
                <div
                  v-for="(item, index) in module.items"
                  :key="index"
                  :class="{ 'campaign-link': item.target !== 'NONE' }"
                >
                  {{ item.title || '未填写标题' }}
                </div>
              </div>
            </div>
          </template>
        </template>
        <h3>选择用餐方式</h3>
        <div class="phone-panel activities">
          <div><span>堂食点餐</span><span class="target">扫码入桌</span></div>
          <div><span>到店自取</span><span class="target">去点单</span></div>
          <div><span>外卖配送</span><span class="target">去点单</span></div>
        </div>
        <h3>浏览菜单</h3>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.preview { align-self: start; position: sticky; top: calc(var(--jz-toolbar-height) + 16px); }
h2 { margin: 0 0 16px; font-size: var(--jz-font-section); }
.phone { max-width: 340px; margin-inline: auto; border: 1px solid var(--jz-border); border-radius: 14px; overflow: hidden; font-size: var(--jz-font-control); }
.phone-title { padding: 12px; text-align: center; font-weight: 600; border-bottom: 1px solid var(--jz-border); }
.phone-content { background: var(--jz-page-background); padding: 12px; }
.phone-panel, .preview-banner { border-radius: 8px; background: var(--jz-surface); margin-bottom: 12px; overflow: hidden; overflow-wrap: anywhere; }
.phone-panel:not(.activities), .activities > div, .banner-caption { padding: 10px 12px; }
.store-name { font-weight: 600; }
.notice { display: flex; gap: 10px; }
.notice span { white-space: pre-wrap; }
.notice strong, .target { flex: none; color: var(--preview-accent); font-size: var(--jz-font-meta); }
.banner-caption, .activities > div { display: flex; justify-content: space-between; align-items: baseline; gap: 10px; }
.activities > div + div { border-top: 1px solid var(--jz-border); }
.campaign-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; margin-top: 6px; }
.campaign-grid > div { display: flex; align-items: center; min-height: 42px; padding: 6px 8px; border: 1px solid var(--jz-border); border-radius: var(--jz-radius-control); background: var(--jz-page-background); font-size: var(--jz-font-meta); }
.campaign-link { color: var(--preview-accent); font-weight: 600; }
.preview-banner img, .image-placeholder { display: block; width: 100%; aspect-ratio: 2.65; object-fit: cover; }
.image-placeholder { display: grid; place-items: center; color: var(--jz-text-secondary); }
.banner-pages { display: flex; justify-content: center; gap: 4px; padding: 4px; }
.banner-pages button { border: 0; color: var(--jz-text-secondary); background: transparent; min-width: 28px; min-height: 28px; cursor: pointer; }
.banner-pages button[aria-pressed='true'] { color: var(--preview-accent); font-weight: 600; text-decoration: underline; }
h3 { font-size: var(--jz-font-control); margin: 16px 0 8px; }
@media (max-width: 900px) { .preview { position: static; } }
</style>
