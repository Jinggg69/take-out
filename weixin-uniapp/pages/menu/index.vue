<template>
  <view class="menu-page">
    <AppStoreHeader />
    <view v-if="!app.selectedStoreId" class="menu-state">
      <text class="state-title">先选一家门店</text><text class="muted">选择商家和门店后，就可以查看菜单。</text>
      <button class="primary-button" @click="openMerchantPicker">选择商家</button>
    </view>
    <template v-else>
      <view class="mode-row">
        <button v-for="mode in modes" :key="mode.value" class="mode-button" :class="{ active: app.fulfillmentType === mode.value }" :disabled="locked" @click="changeMode(mode.value)">{{ mode.label }}</button>
      </view>
      <view v-if="app.diningContext" class="context-note">堂食 · {{ app.diningContext.name }}<text class="text-action" @click="leaveDining">退出堂食</text></view>
      <view v-if="locked" class="context-note pending-note">有一笔订单正在处理中<button class="text-button" @click="recoverOrder">查看订单</button></view>
      <view v-if="loading" class="menu-state"><text>正在加载菜单…</text></view>
      <view v-else-if="error" class="menu-state"><text class="state-title">菜单暂时没能加载</text><text class="muted">{{ error }}</text><button class="outline-button" @click="loadMenu">重新加载</button></view>
      <view v-else-if="!categories.length" class="menu-state"><text class="state-title">门店暂未提供菜品</text><text class="muted">可以切换门店，或稍后再来看看。</text></view>
      <view v-else class="catalog">
        <scroll-view class="category-list" scroll-y>
          <button v-for="category in categories" :key="category.id" class="category-button" :class="{ selected: selectedCategory?.id === category.id }" @click="selectCategory(category)">{{ category.name }}</button>
        </scroll-view>
        <scroll-view class="product-list" scroll-y :scroll-top="scrollTop">
          <view class="category-heading">{{ selectedCategory?.name }}</view>
          <view v-if="productLoading" class="small-state">正在加载菜品…</view>
          <view v-else-if="productError" class="small-state"><text>{{ productError }}</text><button class="text-button" @click="selectCategory(selectedCategory)">重试</button></view>
          <view v-else-if="!products.length" class="small-state">该分类暂时没有可售菜品</view>
          <view v-for="product in products" :key="product.id" class="product-row">
            <image v-if="product.image" class="product-photo" :src="product.image" mode="aspectFill" @click="showProduct(product)" />
            <view v-else class="product-placeholder" @click="showProduct(product)"><view class="plate-mark" /></view>
            <view class="product-info">
              <text class="product-name" @click="showProduct(product)">{{ product.name }}</text>
              <text v-if="product.description" class="product-description">{{ product.description }}</text>
              <text v-else class="product-description">{{ Number(product.type) === 2 ? '组合套餐' : '门店现售' }}</text>
              <view class="product-actions"><text class="product-price">¥{{ price(product.price) }}</text>
                <view class="quantity-actions">
                  <text v-if="quantity(product)" class="quantity-label">已选 {{ quantity(product) }}</text>
                  <button v-if="flavorGroups(product.flavors).length" class="spec-button" :disabled="busy || locked" @click="showProduct(product)">选规格</button>
                  <button v-else class="add-button" :disabled="busy || locked" :aria-label="'添加' + product.name" @click="addProduct(product)"><view class="plus-mark" /></button>
                </view>
              </view>
            </view>
          </view>
          <view class="list-bottom">菜品与价格以门店当前信息为准</view>
        </scroll-view>
      </view>
      <view class="cart-bar">
        <button class="cart-summary" :aria-label="'查看购物车，共' + total.quantity + '份'" @click="openCart">
          <view class="cart-icon"><view class="basket-mark" /><text v-if="total.quantity" class="cart-count">{{ total.quantity }}</text></view>
          <view><view class="cart-amount">¥{{ total.amount }}</view><text class="cart-caption">{{ cartError || (total.quantity ? '共 ' + total.quantity + ' 份，费用以结算为准' : '选好菜品，再去结算') }}</text></view>
        </button>
        <button class="checkout-button" :disabled="!total.quantity || !!cartError || busy || locked || app.storeContext?.open === false" @click="checkout">{{ app.storeContext?.open === false ? '门店休息中' : '去结算' }}</button>
      </view>
    </template>
    <view v-if="sheet" class="sheet-mask" @click="closeSheet">
      <view class="menu-sheet" @click.stop>
        <view class="sheet-header"><text class="sheet-title">{{ sheet === 'cart' ? '已选菜品' : activeProduct?.name }}</text><button class="close-button" aria-label="关闭" @click="closeSheet"><view class="close-mark" /></button></view>
        <template v-if="sheet === 'cart'">
          <scroll-view class="sheet-body" scroll-y>
            <view v-if="!app.orderListData.length" class="small-state">还没有添加菜品</view>
            <view v-for="(row, index) in app.orderListData" :key="row.id || index" class="cart-item">
              <view class="cart-item-info"><text class="product-name">{{ row.name }}</text><text v-if="row.dishFlavor" class="muted">{{ row.dishFlavor }}</text><text class="cart-item-price">¥{{ price(row.amount) }}</text></view>
              <view class="stepper"><button class="subtract-button" :disabled="busy || locked" :aria-label="'减少' + row.name" @click="changeCart(row, -1)"><view class="minus-mark" /></button><text>{{ row.number }}</text><button class="add-button" :disabled="busy || locked" :aria-label="'增加' + row.name" @click="changeCart(row, 1)"><view class="plus-mark" /></button></view>
            </view>
          </scroll-view>
          <view class="sheet-footer"><button class="outline-button" :disabled="busy || locked || !app.orderListData.length" @click="clearCart">清空购物车</button><button class="primary-button" :disabled="!total.quantity || busy || locked || !!cartError || app.storeContext?.open === false" @click="checkout">去结算 · ¥{{ total.amount }}</button></view>
        </template>
        <template v-else>
          <scroll-view class="sheet-body" scroll-y>
            <image v-if="activeProduct?.image" class="detail-photo" :src="activeProduct.image" mode="aspectFill" />
            <text v-if="activeProduct?.description" class="detail-description">{{ activeProduct.description }}</text>
            <view v-for="(group, index) in groups" :key="group.name + index" class="flavor-group"><text class="flavor-name">{{ group.name }}</text><view class="flavor-options"><button v-for="option in group.options" :key="option" class="flavor-option" :class="{ chosen: choices[index] === option }" @click="choices[index] = option">{{ option }}</button></view></view>
            <view v-if="setmealLoading" class="small-state">正在查看套餐内容…</view>
            <view v-for="(dish, index) in setmealDishes" :key="dish.id || index" class="setmeal-line"><text>{{ dish.name }}</text><text>× {{ dish.copies || 1 }}</text></view>
          </scroll-view>
          <view class="sheet-footer"><text class="detail-price">¥{{ price(activeProduct?.price) }}</text><button class="primary-button" :disabled="busy || locked" @click="confirmProduct">{{ busy ? '正在添加…' : '加入购物车' }}</button></view>
        </template>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { useAppStore } from '../../store/index.js'
import AppStoreHeader from '../../components/app-store-header.vue'
import { getCategoryList, dishListByCategoryId, querySetmeaList, getShoppingCartList, newAddShoppingCartAdd, newShoppingCartSub, delShoppingCart, querySetmealDishById } from '../api/api.js'
import { openMerchantPicker, scanDining } from '../../services/context.js'
import { cartPayload, cartTotal, flavorGroups, productQuantity } from '../../utils/menu-cart.js'

const app = useAppStore()
const categories = ref([]), selectedCategory = ref(null), products = ref([])
const loading = ref(false), productLoading = ref(false), busy = ref(false)
const error = ref(''), productError = ref(''), cartError = ref(''), scrollTop = ref(0)
const sheet = ref(''), activeProduct = ref(null), choices = ref([]), setmealDishes = ref([]), setmealLoading = ref(false)
let requestId = 0, categoryRequest = 0, visible = false
const modes = [{ value: 'DINE_IN', label: '堂食' }, { value: 'PICKUP', label: '自取' }, { value: 'DELIVERY', label: '外卖' }]
const locked = computed(() => !!app.pendingSubmission || !!app.paymentInFlight)
const groups = computed(() => flavorGroups(activeProduct.value?.flavors))
const total = computed(() => { try { return cartTotal(app.orderListData) } catch { return { quantity: 0, amount: '—' } } })
const price = value => Number.isFinite(Number(value)) ? Number(value).toFixed(2) : '—'
const quantity = product => productQuantity(product, app.orderListData)
const selectionKey = () => [app.selectedMerchantId, app.selectedStoreId, app.storeGeneration].join(':')
const toast = err => { if (!err?.staleStore && !err?.staleIdentity) uni.showToast({ title: err?.msg || err?.message || '操作未完成，请重试', icon: 'none' }) }

async function refreshCart() {
  if (!app.token || !app.selectedStoreId) { app.initdishListMut([]); return }
  try { const result = await getShoppingCartList(); const list = result.data || []; cartTotal(list); app.initdishListMut(list); cartError.value = '' }
  catch (err) { if (err.staleStore || err.staleIdentity) return; cartError.value = err.msg || err.message || '购物车加载失败，请刷新'; throw err }
}
async function loadMenu() {
  if (!app.selectedStoreId) return
  const run = ++requestId
  loading.value = true; error.value = ''; sheet.value = ''
  try {
    const result = await getCategoryList()
    if (run !== requestId || !visible) return
    categories.value = Array.isArray(result.data) ? result.data : []
    selectedCategory.value = categories.value.find(item => String(item.id) === String(selectedCategory.value?.id)) || categories.value[0] || null
    if (selectedCategory.value) await selectCategory(selectedCategory.value)
    await refreshCart()
  } catch (err) { if (run === requestId && !err.staleStore && !err.staleIdentity) error.value = err.msg || err.message || '请检查网络后重试' }
  finally { if (run === requestId) loading.value = false }
}
async function selectCategory(category) {
  if (!category) return
  const run = ++categoryRequest
  selectedCategory.value = category; products.value = []; productLoading.value = true; productError.value = ''
  scrollTop.value = 1
  try {
    const type = Number(category.type) === 2 ? 2 : 1
    const result = await (type === 2 ? querySetmeaList : dishListByCategoryId)({ categoryId: category.id })
    if (run !== categoryRequest || !visible) return
    products.value = (Array.isArray(result.data) ? result.data : []).map(item => ({ ...item, type }))
    scrollTop.value = 0
  } catch (err) { if (run === categoryRequest && !err.staleStore && !err.staleIdentity) productError.value = err.msg || err.message || '菜品加载失败' }
  finally { if (run === categoryRequest) productLoading.value = false }
}
async function changeMode(type) {
  if (locked.value) return
  try {
    if (type === 'DINE_IN' && !app.diningContext) { await scanDining(); await loadMenu() }
    else { if (type !== 'DINE_IN') app.setDiningContext(null); app.setFulfillmentType(type) }
  } catch (err) { toast(err) }
}
function leaveDining() { if (!locked.value) { app.setDiningContext(null); app.setFulfillmentType('DELIVERY') } }
async function addProduct(product, flavor) {
  if (busy.value || locked.value) return
  busy.value = true
  const originalSelection = selectionKey()
  try { await app.ensureLogin(); if (originalSelection !== selectionKey()) throw { staleStore: true }; await newAddShoppingCartAdd(cartPayload(product, flavor)); await refreshCart(); sheet.value = '' }
  catch (err) { toast(err) }
  finally { busy.value = false }
}
async function showProduct(product) {
  activeProduct.value = product; choices.value = flavorGroups(product.flavors).map(group => group.options[0]); setmealDishes.value = []; sheet.value = 'product'
  if (Number(product.type) !== 2) return
  setmealLoading.value = true
  try { const result = await querySetmealDishById({ id: product.id }); if (activeProduct.value?.id === product.id) setmealDishes.value = result.data || [] }
  catch (err) { toast(err) }
  finally { setmealLoading.value = false }
}
function confirmProduct() { return addProduct(activeProduct.value, choices.value) }
function closeSheet() { if (!busy.value) sheet.value = '' }
async function openCart() { if (!app.token) return; try { await refreshCart(); sheet.value = 'cart' } catch (err) { toast(err) } }
async function changeCart(row, delta) {
  if (busy.value || locked.value) return
  busy.value = true
  try { await (delta > 0 ? newAddShoppingCartAdd : newShoppingCartSub)(cartPayload(row)); await refreshCart() }
  catch (err) { toast(err) }
  finally { busy.value = false }
}
function clearCart() {
  uni.showModal({ title: '清空购物车', content: '确认移除当前门店已选的全部菜品？', success: async result => {
    if (!result.confirm || busy.value || locked.value) return
    busy.value = true
    try { await delShoppingCart(); await refreshCart(); sheet.value = '' } catch (err) { toast(err) } finally { busy.value = false }
  } })
}
async function checkout() {
  if (busy.value || locked.value || app.storeContext?.open === false) return
  const originalSelection = selectionKey()
  try { await app.ensureLogin(); if (originalSelection !== selectionKey()) throw { staleStore: true }; await refreshCart(); if (!total.value.quantity) return; sheet.value = ''; uni.navigateTo({ url: '/pages/order/index' }) }
  catch (err) { toast(err) }
}
function recoverOrder() { uni.navigateTo({ url: app.pendingSubmission ? '/pages/order/index' : '/pages/pay/index?orderId=' + encodeURIComponent(app.paymentInFlight?.orderId || '') }) }
onShow(() => { visible = true; loadMenu() })
onHide(() => { visible = false; requestId++; categoryRequest++; sheet.value = ''; busy.value = false })
onUnload(() => { visible = false; requestId++; categoryRequest++ })
</script>

<style scoped lang="scss">
.menu-page{height:calc(100vh - var(--window-top,0px) - var(--window-bottom,0px));display:flex;flex-direction:column;background:#fff;color:#1f2933;overflow:hidden}
.mode-row{display:flex;gap:12rpx;padding:12rpx 28rpx 20rpx;border-bottom:1rpx solid #edf0ed}.mode-button{margin:0;flex:1;line-height:68rpx;font-size:26rpx;background:#f3f5f3;color:#627069;border-radius:12rpx}.mode-button.active{background:#e8f3ed;color:#176b4b;font-weight:600}.mode-button::after,.category-button::after,.cart-summary::after,.add-button::after,.subtract-button::after,.close-button::after{border:0}.mode-button[disabled]{opacity:.5}
.context-note{padding:12rpx 28rpx;background:#f0f7f3;color:#37634d;font-size:24rpx;display:flex;align-items:center;justify-content:space-between}.text-action{padding:8rpx}.pending-note{background:#fff6e8;color:#845718}.menu-state{flex:1;display:flex;align-items:center;justify-content:center;flex-direction:column;padding:56rpx;gap:24rpx;text-align:center}.state-title{font-size:32rpx;font-weight:600}.muted{font-size:24rpx;color:#647169}.catalog{flex:1;min-height:0;display:flex}.category-list{width:160rpx;flex-shrink:0;background:#f5f7f5;height:100%}.category-button{margin:0;padding:30rpx 16rpx;line-height:1.45;font-size:25rpx;border-radius:0;color:#617067;background:transparent;text-align:center}.category-button.selected{background:#fff;color:#176b4b;font-weight:600}.product-list{flex:1;min-width:0;height:100%;padding:0 24rpx;box-sizing:border-box}.category-heading{padding:26rpx 0 6rpx;font-size:26rpx;font-weight:600}.product-row{display:flex;gap:20rpx;padding:26rpx 0;border-bottom:1rpx solid #edf0ed;align-items:flex-start}.product-photo,.product-placeholder{width:140rpx;height:140rpx;border-radius:12rpx;flex-shrink:0;background:#edf2ee}.product-placeholder{display:flex;align-items:center;justify-content:center}.plate-mark{width:70rpx;height:70rpx;border:3rpx solid #a7b6ac;border-radius:50%;box-sizing:border-box;outline:1rpx solid #a7b6ac;outline-offset:-12rpx}.product-info{flex:1;min-width:0}.product-name{font-size:28rpx;font-weight:600;line-height:1.5;display:block;overflow-wrap:anywhere}.product-description{font-size:22rpx;line-height:1.5;color:#68756e;margin-top:8rpx;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.product-actions{display:flex;justify-content:space-between;align-items:center;gap:10rpx;margin-top:20rpx;flex-wrap:wrap}.product-price,.cart-item-price{color:#176b4b;font-size:30rpx;font-weight:600;font-variant-numeric:tabular-nums}.quantity-actions{display:flex;align-items:center;gap:8rpx}.quantity-label{font-size:20rpx;color:#647169}.add-button,.subtract-button{width:64rpx;height:64rpx;min-width:64rpx;line-height:64rpx;padding:0;margin:0;display:flex;align-items:center;justify-content:center;background:#176b4b;border-radius:50%}.add-button[disabled],.subtract-button[disabled]{opacity:.45}.subtract-button{background:#eef4f0}.plus-mark,.minus-mark{width:22rpx;height:3rpx;background:#fff;position:relative}.plus-mark::after{content:'';position:absolute;left:10rpx;top:-10rpx;width:3rpx;height:22rpx;background:#fff}.minus-mark{background:#176b4b}.spec-button{font-size:22rpx;line-height:58rpx;padding:0 14rpx;margin:0;background:#176b4b;color:#fff;border-radius:10rpx}.small-state{display:flex;flex-direction:column;align-items:center;padding:42rpx 12rpx;gap:16rpx;font-size:25rpx;color:#647169}.list-bottom{text-align:center;padding:38rpx 4rpx;font-size:20rpx;color:#6c7771}.cart-bar{display:flex;align-items:center;padding:18rpx 24rpx;gap:16rpx;border-top:1rpx solid #e5eae6;background:#fff;flex-shrink:0}.cart-summary{flex:1;display:flex;align-items:center;text-align:left;padding:0;line-height:1.5;background:transparent;gap:20rpx;margin:0}.cart-icon{position:relative;width:64rpx;display:flex;align-items:center;justify-content:center}.basket-mark{width:38rpx;height:26rpx;border:3rpx solid #176b4b;border-top:0;transform:skew(-6deg);position:relative}.basket-mark::before{content:'';position:absolute;left:-10rpx;top:-10rpx;width:12rpx;height:3rpx;background:#176b4b}.basket-mark::after{content:'';position:absolute;left:3rpx;bottom:-11rpx;width:5rpx;height:5rpx;background:#176b4b;border-radius:50%;box-shadow:24rpx 0 #176b4b}.cart-count{position:absolute;right:-2rpx;top:-20rpx;min-width:28rpx;line-height:28rpx;background:#176b4b;color:#fff;text-align:center;border-radius:20rpx;font-size:18rpx;padding:0 5rpx}.cart-amount{font-size:34rpx;font-weight:600;color:#1f2933}.cart-caption{font-size:20rpx;color:#627069;line-height:1.4}.checkout-button{margin:0;min-width:170rpx;padding:0 24rpx;line-height:82rpx;color:#fff;background:#176b4b;border-radius:12rpx;font-size:28rpx}.checkout-button[disabled]{background:#edf1ed;color:#69756d}.sheet-mask{position:fixed;inset:0;background:rgba(20,34,25,.4);z-index:999;display:flex;align-items:flex-end}.menu-sheet{width:100%;background:#fff;border-radius:28rpx 28rpx 0 0;padding-bottom:env(safe-area-inset-bottom);max-height:82vh;display:flex;flex-direction:column}.sheet-header{display:flex;align-items:center;justify-content:space-between;padding:28rpx 30rpx;border-bottom:1rpx solid #edf0ed}.sheet-title{font-size:32rpx;font-weight:600}.close-button{width:72rpx;height:72rpx;display:flex;align-items:center;justify-content:center;background:transparent;padding:0;margin:0}.close-mark{width:26rpx;height:3rpx;transform:rotate(45deg);background:#68756e}.close-mark::after{content:'';display:block;width:26rpx;height:3rpx;transform:rotate(90deg);background:#68756e}.sheet-body{max-height:52vh;min-height:100rpx;box-sizing:border-box;padding:12rpx 30rpx}.sheet-footer{display:flex;align-items:center;gap:24rpx;padding:24rpx 30rpx;border-top:1rpx solid #edf0ed}.sheet-footer .primary-button{flex:1;margin:0}.cart-item{display:flex;align-items:center;justify-content:space-between;gap:20rpx;padding:24rpx 0;border-bottom:1rpx solid #edf0ed}.cart-item-info{display:flex;flex:1;flex-direction:column;gap:10rpx;min-width:0}.stepper{display:flex;align-items:center;gap:20rpx}.stepper>text{min-width:32rpx;text-align:center}.cart-item-price{font-size:26rpx}.detail-photo{width:100%;height:300rpx;border-radius:12rpx;margin-top:16rpx}.detail-description{display:block;margin:24rpx 0;color:#647169;font-size:26rpx}.flavor-group{padding:22rpx 0}.flavor-name{font-size:26rpx;font-weight:600}.flavor-options{display:flex;flex-wrap:wrap;gap:14rpx;margin-top:18rpx}.flavor-option{font-size:26rpx;line-height:70rpx;padding:0 26rpx;border-radius:10rpx;margin:0;background:#f2f5f2;color:#526458}.flavor-option.chosen{color:#176b4b;background:#e5f2e9}.detail-price{font-size:38rpx;font-weight:600;color:#176b4b}.setmeal-line{display:flex;justify-content:space-between;padding:20rpx 0;border-bottom:1rpx solid #edf0ed;font-size:26rpx}.text-button{background:transparent;color:#176b4b;font-size:25rpx;margin:0;padding:10rpx 20rpx;line-height:1.5}.primary-button,.outline-button{font-size:28rpx;line-height:84rpx;border-radius:12rpx;padding:0 28rpx}.primary-button{background:#176b4b;color:#fff}.outline-button{background:#f0f5f1;color:#176b4b}.primary-button[disabled]{background:#e8eee9;color:#6a786d}
</style>
