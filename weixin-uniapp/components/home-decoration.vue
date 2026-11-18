<template>
  <view v-if="visibleModules.length" class="home-decoration">
    <view v-for="module in visibleModules" :key="module.type">
      <swiper
        v-if="module.type === 'BANNER'"
        :key="module.items.length"
        class="decoration-banner"
        :indicator-dots="module.items.length > 1"
      >
        <swiper-item v-for="(item, index) in module.items" :key="index">
          <button
            v-if="item.target !== 'NONE'"
            class="banner-slide"
            @click="emit('open', item.target)"
          >
            <image :src="item.imageUrl" mode="aspectFill" @error="hideImage(item.imageUrl)" />
            <text class="banner-title">{{ item.title }}</text>
          </button>
          <view v-else class="banner-slide">
            <image :src="item.imageUrl" mode="aspectFill" @error="hideImage(item.imageUrl)" />
            <text class="banner-title">{{ item.title }}</text>
          </view>
        </swiper-item>
      </swiper>
      <view v-else-if="module.type === 'NOTICE'" class="decoration-notice">
        <text class="notice-label" :style="{ color: accent }">公告</text>
        <text class="notice-text">{{ module.text }}</text>
      </view>
      <view v-else class="decoration-activities">
        <view class="activities-title">门店活动</view>
        <view class="activities-list">
          <template v-for="(item, index) in module.items" :key="index">
            <view v-if="item.target === 'NONE'" class="activity-item">{{ item.title }}</view>
            <button v-else class="activity-item activity-action" :style="{ color: accent }" @click="emit('open', item.target)">{{ item.title }}</button>
          </template>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({ modules: { type: Array, required: true }, accent: { type: String, required: true } })
const emit = defineEmits(['open'])
const failedImages = ref([])
watch(() => props.modules, () => { failedImages.value = [] })

const visibleModules = computed(() => props.modules.filter(module => module.enabled)
  .map(module => module.type === 'BANNER'
    ? { ...module, items: module.items.filter(item => !failedImages.value.includes(item.imageUrl)) }
    : module)
  .filter(module => module.type === 'NOTICE' ? module.text.trim() : module.items.length))

function hideImage(url) {
  if (!failedImages.value.includes(url)) failedImages.value.push(url)
}
</script>

<style scoped lang="scss">
.home-decoration { display: flex; flex-direction: column; gap: 20rpx; margin: 24rpx 0; }
.decoration-banner { height: 356rpx; overflow: hidden; border-radius: 20rpx; background: #fff; }
.banner-slide { display: flex; flex-direction: column; width: 100%; height: 100%; padding: 0; margin: 0; text-align: left; background: #fff; color: #1f2933; }
.banner-slide::after, .activity-action::after { border: 0; }
.banner-slide image { display: block; width: 100%; height: 260rpx; }
.banner-title { max-width: 100%; padding: 14rpx 22rpx; font-size: 27rpx; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.decoration-notice { display: flex; align-items: baseline; gap: 16rpx; padding: 20rpx 24rpx; border: 1px solid #e6e9ec; border-radius: 16rpx; background: #fff; font-size: 26rpx; }
.notice-label { flex-shrink: 0; font-weight: 600; }
.notice-text { min-width: 0; overflow-wrap: anywhere; white-space: pre-wrap; }
.decoration-activities { padding: 20rpx 24rpx; border-radius: 20rpx; background: #fff; }
.activities-title { margin-bottom: 12rpx; font-weight: 600; }
.activities-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12rpx; }
.activity-item { display: flex; align-items: center; min-height: 88rpx; width: 100%; padding: 14rpx 16rpx; margin: 0; border: 1px solid #e6e9ec; border-radius: 12rpx; background: #f7f8fa; color: #1f2933; font-size: 25rpx; line-height: 1.45; text-align: left; overflow-wrap: anywhere; }
.activity-action { font-weight: 600; }
</style>
