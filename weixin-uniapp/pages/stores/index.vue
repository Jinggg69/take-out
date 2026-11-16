<template>
  <view class="app-page">
    <view class="app-heading">选择门店</view>
    <view class="app-subtitle">{{ merchantName || '当前商家' }}</view>
    <view class="app-search"><input v-model="keyword" placeholder="搜索门店名称" aria-label="门店名称" maxlength="100" confirm-type="search" @confirm="search" /><button class="app-button" @click="search">搜索</button></view>
    <AppState v-if="loading && !records.length" loading />
    <AppState v-else-if="error && !records.length" title="门店加载失败" :message="error" action="重新加载" @retry="load(true)" />
    <AppState v-else-if="!records.length" title="暂无可用门店" message="请调整搜索名称，或重新选择商家。" action="重新选择商家" @retry="openMerchantPicker" />
    <view v-else class="app-list"><button v-for="store in records" :key="store.storeId" class="app-list-row" :disabled="busy" @click="choose(store)"><view class="app-grow"><view class="app-title">{{ store.storeName }}</view><view v-if="store.address" class="app-meta">{{ store.address }}</view><view v-if="store.opensAt && store.closesAt" class="app-meta">营业时间 {{ store.opensAt }}—{{ store.closesAt }}</view></view><text class="app-status">{{ store.open === false ? '休息中' : store.open === true ? '营业中' : '选择' }}</text></button></view>
    <view v-if="actionError || error && records.length" class="app-error">{{ actionError || error }}</view>
    <button v-if="records.length < total" class="app-link" :loading="loading" :disabled="loading" @click="load()">加载更多门店</button>
    <button class="app-link" @click="openMerchantPicker">重新选择商家</button>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { getMerchantStores, getMerchant, getStoreContext } from '@/pages/api/api.js'
import { openMerchantPicker } from '@/services/context.js'
import { useAppStore } from '@/store/index.js'
import { usePagination } from '@/components/app-discovery/pagination.js'
import AppState from '@/components/app-state/index.vue'
const merchantId = ref(''), merchantName = ref(''), keyword = ref(''), busy = ref(false), actionError = ref('')
const app = useAppStore()
let appliedKeyword = ''
const { records, loading, error, total, load } = usePagination(params => { if (!merchantId.value) throw new Error('请先选择商家'); return getMerchantStores(merchantId.value, { ...params, keyword: appliedKeyword }) })
function search() { appliedKeyword = keyword.value.trim(); load(true) }
async function choose(store) {
  busy.value = true; actionError.value = ''
  const generation = app.storeGeneration
  try {
    app.assertCanSelect(store.storeId)
    const result = await getStoreContext(store.storeId)
    if (generation !== app.storeGeneration) throw new Error('选择已变化，请重新选择门店')
    if (result.code !== 1) throw new Error(result.msg || '门店验证失败')
    const context = result.data
    if (String(context.merchantId) !== String(merchantId.value)) throw new Error('门店归属已变更，请重新选择商家')
    app.selectContext(context)
    uni.switchTab({ url: '/pages/index/index' })
  } catch (e) { actionError.value = e.message || e.msg || '门店验证失败，请重试' }
  finally { busy.value = false }
}
onLoad(async options => { merchantId.value = options.merchantId || ''; load(true); if (merchantId.value) { try { const r = await getMerchant(merchantId.value); if (r.code === 1) merchantName.value = r.data.merchantName } catch (_) {} } })
onReachBottom(() => load())
</script>
<style lang="scss">@import '@/styles/app.scss';</style>
