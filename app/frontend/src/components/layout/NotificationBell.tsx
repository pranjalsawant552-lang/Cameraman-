import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Bell, CheckCheck, CreditCard, Gift, ImageIcon, PackageX, Sparkles, CalendarPlus, CalendarX } from 'lucide-react'
import { useNotificationStore } from '@/store/useNotificationStore'
import { cn, timeAgo } from '@/lib/utils'
import type { NotificationType } from '@/lib/types'

const ICONS: Record<NotificationType, typeof Bell> = {
  BOOKING_CREATED: CalendarPlus,
  BOOKING_CANCELLED: CalendarX,
  PAYMENT_RECEIVED: CreditCard,
  LOW_STOCK: PackageX,
  OUT_OF_STOCK: PackageX,
  LEAVE_REQUESTED: Bell,
  LEAVE_APPROVED: Bell,
  LEAVE_REJECTED: Bell,
  GALLERY_PUBLISHED: ImageIcon,
  SUGGESTIONS_PENDING: Sparkles,
  SUGGESTIONS_AUTO_SENT: Gift,
}

export function NotificationBell() {
  const { items, unreadCount, loaded, justArrivedId, init, markRead, markAllRead } = useNotificationStore()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => { init() }, [init])

  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [])

  return (
    <div className="relative" ref={ref}>
      <motion.button
        onClick={() => setOpen((o) => !o)}
        animate={justArrivedId ? { rotate: [0, -12, 10, -8, 0] } : {}}
        transition={{ duration: 0.5 }}
        className="relative flex h-10 w-10 items-center justify-center rounded-full text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink"
      >
        <Bell className="h-[18px] w-[18px]" />
        {unreadCount > 0 && (
          <span className="absolute right-2 top-2 flex h-2 w-2">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent opacity-75" />
            <span className="relative inline-flex h-2 w-2 rounded-full bg-accent" />
          </span>
        )}
      </motion.button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -8, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.97 }}
            transition={{ duration: 0.16 }}
            className="absolute right-0 top-12 z-50 w-96 overflow-hidden rounded-[var(--radius-lg)] border border-border bg-surface shadow-[var(--shadow-card)]"
          >
            <div className="flex items-center justify-between border-b border-border px-4 py-3">
              <p className="font-display text-base text-ink">Notifications</p>
              {unreadCount > 0 && (
                <button onClick={() => markAllRead()} className="flex items-center gap-1 text-xs font-medium text-accent hover:underline">
                  <CheckCheck className="h-3.5 w-3.5" /> Mark all read
                </button>
              )}
            </div>
            <div className="max-h-96 overflow-y-auto">
              {!loaded ? (
                <div className="p-6 text-center text-sm text-ink-muted">Loading…</div>
              ) : items.length === 0 ? (
                <div className="p-8 text-center text-sm text-ink-muted">You're all caught up.</div>
              ) : (
                items.map((n) => {
                  const Icon = ICONS[n.type] ?? Bell
                  return (
                    <button
                      key={n.id}
                      onClick={() => markRead(n.id)}
                      className={cn(
                        'flex w-full items-start gap-3 border-b border-border/60 px-4 py-3.5 text-left transition-colors last:border-0 hover:bg-surface-2',
                        !n.read && 'bg-accent/5'
                      )}
                    >
                      <div className={cn('mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full', !n.read ? 'bg-accent/15 text-accent' : 'bg-surface-2 text-ink-faint')}>
                        <Icon className="h-4 w-4" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className={cn('text-sm leading-snug', !n.read ? 'font-semibold text-ink' : 'text-ink-muted')}>{n.title}</p>
                        <p className="mt-0.5 line-clamp-2 text-xs text-ink-faint">{n.message}</p>
                        <p className="mt-1 text-[11px] text-ink-faint">{timeAgo(n.createdAt)}</p>
                      </div>
                      {!n.read && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-accent" />}
                    </button>
                  )
                })
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
