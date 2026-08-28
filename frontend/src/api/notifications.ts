/**
 * 通知 SSE 服务
 *
 * 管理与后端 /api/notifications/stream 的持久 SSE 连接，
 * 接收后端推送的通知事件并分发到 window。
 */

const RECONNECT_DELAY = 3000
const MAX_RECONNECT_DELAY = 30000

let eventSource: EventSource | null = null
let currentUserId: string | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectDelay = RECONNECT_DELAY

/**
 * 连接到通知 SSE 端点。
 * 如果已连接到同一用户，则忽略；如果用户不同，则重新连接。
 */
export function connectNotificationSSE(userId: string): void {
  if (!userId) return

  // 如果已连接到同一用户，跳过
  if (eventSource && currentUserId === userId) {
    return
  }

  // 关闭旧连接
  disconnectNotificationSSE()

  try {
    const url = `/api/notifications/stream?userId=${encodeURIComponent(userId)}`
    eventSource = new EventSource(url)
    currentUserId = userId

    eventSource.addEventListener('data_updated', (event) => {
      try {
        const payload = JSON.parse((event as MessageEvent).data)
        const data = payload.data as { type?: string; action?: string }
        if (data?.type) {
          window.dispatchEvent(new CustomEvent(`${data.type}-data-updated`, { detail: data }))
        }
      } catch (e) {
        console.warn('[NotificationSSE] Failed to parse data_updated event:', e)
      }
    })

    // 提醒到点时，后端推送名为 `reminder` 的事件，派发事件由全局弹窗组件居中展示
    eventSource.addEventListener('reminder', (event) => {
      try {
        const payload = JSON.parse((event as MessageEvent).data)
        const message =
          typeof payload.data === 'string' ? payload.data : JSON.stringify(payload.data ?? '')
        window.dispatchEvent(new CustomEvent('reminder-fired', { detail: { message } }))
      } catch (e) {
        console.warn('[NotificationSSE] Failed to parse reminder event:', e)
      }
    })

    eventSource.onopen = () => {
      reconnectDelay = RECONNECT_DELAY
      console.log('[NotificationSSE] Connected for user:', userId)
    }

    eventSource.onerror = () => {
      console.warn('[NotificationSSE] Connection error, will reconnect...')
      scheduleReconnect(userId)
    }
  } catch (e) {
    console.error('[NotificationSSE] Failed to connect:', e)
    scheduleReconnect(userId)
  }
}

/**
 * 断开 SSE 连接。
 */
export function disconnectNotificationSSE(): void {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (eventSource) {
    eventSource.close()
    eventSource = null
    currentUserId = null
  }
}

function scheduleReconnect(userId: string): void {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
  }

  reconnectTimer = setTimeout(() => {
    if (eventSource?.readyState === EventSource.CLOSED || !eventSource) {
      console.log('[NotificationSSE] Reconnecting...')
      reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY)
      connectNotificationSSE(userId)
    }
  }, reconnectDelay)
}
