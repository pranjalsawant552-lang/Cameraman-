import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { API_BASE_URL } from './config'
import { tokenStorage } from './tokenStorage'

// The backend's WebSocket handshake lives at {origin}/ws, not under the
// /api prefix the REST client uses (see WebSocketConfig on the backend —
// it's registered at the application root, same level as /api). Strip a
// trailing /api (with or without a trailing slash) off VITE_API_BASE_URL to
// get the origin the socket should connect to.
function wsOrigin(): string {
  return API_BASE_URL.replace(/\/api\/?$/, '')
}

let client: Client | null = null
let refCount = 0
let connectHandlers: (() => void)[] = []

/** Lazily creates (or reuses) the single shared STOMP connection for this
 *  tab. Multiple callers (notification bell, admin feed, etc.) can each
 *  call connectRealtime() and get the same underlying socket — the
 *  connection is only torn down once every caller has disconnected. */
function connectRealtime(): Client {
  if (client) return client

  client = new Client({
    webSocketFactory: () => new SockJS(`${wsOrigin()}/ws`) as unknown as WebSocket,
    // Read the token fresh on every (re)connect attempt, not just once at
    // construction — access tokens rotate via apiClient's silent
    // refresh-and-retry, and stompjs reuses whatever `connectHeaders` held
    // at construction time on every automatic reconnect otherwise, which
    // would permanently fail reconnects after the first token refresh.
    beforeConnect: () => {
      if (client) client.connectHeaders = { Authorization: `Bearer ${tokenStorage.getAccessToken() ?? ''}` }
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => connectHandlers.forEach((h) => h()),
  })
  client.activate()
  return client
}

function releaseRealtime() {
  refCount = Math.max(0, refCount - 1)
  if (refCount === 0 && client) {
    client.deactivate()
    client = null
    connectHandlers = []
  }
}

/** Force-closes the shared socket immediately, regardless of subscriber
 *  count. Call on logout — otherwise the socket would sit connected under
 *  the previous user's identity until the tab reloads. */
export function disconnectRealtime() {
  refCount = 0
  connectHandlers = []
  if (client) {
    client.deactivate()
    client = null
  }
}

/** Subscribes to this user's private live notification queue
 *  (/user/queue/notifications, matching NotificationPublisher#pushLive on
 *  the backend). Returns an unsubscribe function. Silently does nothing if
 *  there's no access token yet (logged out). */
export function subscribeToNotificationQueue(onMessage: (body: unknown) => void): () => void {
  if (!tokenStorage.getAccessToken()) return () => {}
  refCount++
  const c = connectRealtime()
  let stompSub: { unsubscribe: () => void } | null = null

  const onConnect = () => {
    stompSub = c.subscribe('/user/queue/notifications', (msg: IMessage) => {
      try { onMessage(JSON.parse(msg.body)) } catch { /* ignore malformed frame */ }
    })
  }
  connectHandlers.push(onConnect)
  if (c.connected) onConnect()

  return () => {
    stompSub?.unsubscribe()
    connectHandlers = connectHandlers.filter((h) => h !== onConnect)
    releaseRealtime()
  }
}
