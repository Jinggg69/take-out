import { h, onMounted, onBeforeUnmount } from 'vue'
import type { Ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { clientEnvironment } from '@/config'
import { buildOrderSocketUrl, connectOrderSocket } from '@/services/order-reminders'
import type { OrderMessage } from '@/services/order-reminders'

export function useOrderReminders(newOrderAudio: Ref<HTMLAudioElement | undefined>, reminderAudio: Ref<HTMLAudioElement | undefined>) {
  const router = useRouter()
  const notices = new Set<ReturnType<typeof ElNotification>>()
  let disconnect: (() => void) | undefined
  let audioHintShown = false
  const audioFailure = () => {
    if (!audioHintShown) { audioHintShown = true; ElMessage.info('请允许浏览器播放声音，订单消息仍会显示') }
  }
  function onOrder(order: OrderMessage) {
    const audio = order.type === 1 ? newOrderAudio.value : reminderAudio.value
    if (audio) {
      try { audio.currentTime = 0; void audio.play().catch(audioFailure) }
      catch { audioFailure() }
    }
    const navigate = () => {
      void router.push({ path: '/order', query: { orderId: String(order.orderId) } })
      notice.close()
    }
    const notice: ReturnType<typeof ElNotification> = ElNotification({
      title: order.type === 1 ? '待接单' : '催单', duration: 0,
      // Render backend content as text, preserving its content without interpreting HTML.
      message: h('div', { class: 'order-notification' }, [
        h('p', order.type === 1 ? `您有1个订单待处理，${order.content}，请及时接单` : order.content),
        h('button', { type: 'button', class: 'notification-action', onClick: (event: MouseEvent) => { event.stopPropagation(); navigate() } }, '查看订单'),
      ]),
      onClick: navigate,
      onClose: () => notices.delete(notice),
    })
    notices.add(notice)
  }
  onMounted(() => {
    try {
      const url = buildOrderSocketUrl(clientEnvironment.socketUrl, window.location.origin, Math.random().toString(36).slice(2))
      disconnect = connectOrderSocket(url, {
        onOrder,
        onError: () => {
          notices.add(ElNotification.error({ title: '订单提醒连接失败', message: '暂时无法接收订单提醒，请刷新页面重试', duration: 0 }))
        },
      })
    } catch {
      notices.add(ElNotification.warning({ title: '订单提醒不可用', message: '无法连接订单提醒，请检查浏览器或网络后刷新重试', duration: 0 }))
    }
  })
  onBeforeUnmount(() => {
    disconnect?.()
    for (const notice of notices) notice.close()
    notices.clear()
    newOrderAudio.value?.pause(); reminderAudio.value?.pause()
  })
}
