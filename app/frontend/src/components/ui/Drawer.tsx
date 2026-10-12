import { useEffect, useId, useRef, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { cn } from '@/lib/utils'
import { X } from 'lucide-react'

const FOCUSABLE = 'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * Side panel for "look at one record without leaving the list". Same accessibility behaviour as
 * Modal: role="dialog" + aria-modal, labelled by its title, Escape closes, focus moves in, Tab stays
 * inside and focus returns to the opener. Full width on a phone, a 28rem panel from the right otherwise.
 */
export function Drawer({ open, onClose, title, subtitle, children, footer, side = 'right', bare = false }: {
  open: boolean
  onClose: () => void
  title: string
  subtitle?: string
  children: ReactNode
  footer?: ReactNode
  side?: 'left' | 'right'
  /** No body padding and no footer, for content that lays itself out (the mobile menu). */
  bare?: boolean
}) {
  const titleId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  useEffect(() => {
    if (!open) return
    const opener = document.activeElement as HTMLElement | null
    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const raf = requestAnimationFrame(() => panelRef.current?.focus())

    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') { e.stopPropagation(); onCloseRef.current(); return }
      if (e.key !== 'Tab' || !panelRef.current) return
      const items = Array.from(panelRef.current.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((el) => el.offsetParent !== null)
      if (items.length === 0) { e.preventDefault(); return }
      const first = items[0], last = items[items.length - 1]
      if (e.shiftKey && (document.activeElement === first || document.activeElement === panelRef.current)) { e.preventDefault(); last.focus() }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      cancelAnimationFrame(raf)
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = prevOverflow
      opener?.focus?.()
    }
  }, [open])

  return (
    <AnimatePresence>
      {open && (
        <div className={cn('fixed inset-0 z-50 flex', side === 'left' ? 'justify-start' : 'justify-end')}>
          <motion.div className="absolute inset-0 bg-black/50 backdrop-blur-sm" aria-hidden="true" onClick={onClose}
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.18 }} />
          <motion.aside
            ref={panelRef} role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}
            initial={{ x: side === 'left' ? '-100%' : '100%' }} animate={{ x: 0 }} exit={{ x: side === 'left' ? '-100%' : '100%' }} transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
            className={cn('relative z-10 flex h-full w-full flex-col border-border bg-surface shadow-[var(--shadow-card)] focus:outline-none sm:max-w-md', side === 'left' ? 'border-r' : 'border-l')}
          >
            <div className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
              <div className="min-w-0">
                <h2 id={titleId} className="truncate font-display text-lg text-ink">{title}</h2>
                {subtitle && <p className="truncate text-xs text-ink-faint">{subtitle}</p>}
              </div>
              <button type="button" onClick={onClose} aria-label="Close panel"
                className="rounded-full p-1.5 text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent">
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className={cn('flex min-h-0 flex-1 flex-col overflow-y-auto', !bare && 'px-5 py-5')}>{children}</div>
            {footer && <div className="flex justify-end gap-2 border-t border-border px-5 py-4">{footer}</div>}
          </motion.aside>
        </div>
      )}
    </AnimatePresence>
  )
}
