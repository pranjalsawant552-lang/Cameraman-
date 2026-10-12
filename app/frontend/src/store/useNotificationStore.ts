import { create } from 'zustand'
import type { AppNotification } from '@/lib/types'
import { fetchNotifications, markAllNotificationsRead, markNotificationRead, subscribeToLiveNotifications } from '@/lib/api'

interface NotificationState {
  items: AppNotification[]
  unreadCount: number
  loaded: boolean
  justArrivedId: string | null
  init: () => Promise<void>
  markRead: (id: string) => Promise<void>
  markAllRead: () => Promise<void>
}

let liveInitialized = false

export const useNotificationStore = create<NotificationState>((set, get) => ({
  items: [],
  unreadCount: 0,
  loaded: false,
  justArrivedId: null,
  init: async () => {
    if (get().loaded) return
    const items = await fetchNotifications()
    set({ items, unreadCount: items.filter((n) => !n.read).length, loaded: true })

    if (!liveInitialized) {
      liveInitialized = true
      subscribeToLiveNotifications((n) => {
        set((s) => ({ items: [n, ...s.items], unreadCount: s.unreadCount + 1, justArrivedId: n.id }))
        setTimeout(() => set((s) => (s.justArrivedId === n.id ? { justArrivedId: null } : {})), 2600)
      })
    }
  },
  markRead: async (id) => {
    set((s) => ({
      items: s.items.map((n) => (n.id === id ? { ...n, read: true } : n)),
      unreadCount: Math.max(0, s.items.find((n) => n.id === id && !n.read) ? s.unreadCount - 1 : s.unreadCount),
    }))
    await markNotificationRead(id)
  },
  markAllRead: async () => {
    set((s) => ({ items: s.items.map((n) => ({ ...n, read: true })), unreadCount: 0 }))
    await markAllNotificationsRead()
  },
}))
