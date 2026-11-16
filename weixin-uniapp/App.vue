<script>
import { handleLaunch } from './services/launch.js'
let launchFlight = null
let launchKey = ''
function enter(options = {}) {
  const key = JSON.stringify(options.query || {})
  if (launchFlight && key === launchKey) return launchFlight
  launchKey = key
  const flight = handleLaunch(options).catch(error => {
    if (!error?.staleStore && !error?.staleIdentity) uni.showToast({ title: error?.msg || error?.message || '入口暂不可用，请重新选择商家', icon: 'none' })
  }).finally(() => { if (launchFlight === flight) launchFlight = null })
  launchFlight = flight
  return flight
}
export default {
  onLaunch(options) {
    return enter(options)
  },
  onShow(options = {}) {
    const query = options.query || {}
    if (!query.merchantId && !query.storeId && !query.scene && !query.token) return
    if (this.$appStore.pendingSubmission || this.$appStore.paymentInFlight) return
    return enter(options)
  },
}
</script>

<style lang="scss">
@import './styles/common.scss';
@import './styles/app.scss';
page { background:#f7f8fa; color:#1f2933; font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif; font-size:28rpx; line-height:1.55; }
button { font-family:inherit; }
button::after { border:0; }
input, textarea { caret-color:#176b4b; }
::selection { color:#174f39; background:#d5eee0; }
/* #ifdef H5 */
:focus-visible { outline:2px solid #176b4b; outline-offset:3px; }
uni-page-body { min-height:100%; background:#f7f8fa; }
/* #endif */
</style>
