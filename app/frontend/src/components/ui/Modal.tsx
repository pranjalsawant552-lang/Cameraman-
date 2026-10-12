import { useEffect, useId, useRef, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

const FOCUSABLE = 'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * Accessible dialog: role="dialog" + aria-modal, labelled by its title, closes on Escape,
 * moves focus inside when opened, keeps Tab/Shift+Tab inside, and returns focus to the
 * element that opened it. The page behind is scroll-locked while it is open.
 */
export function Modal({
  open, onClose, title, children, footer, size = 'md',
}: {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
}) {
  const titleId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  useEffect(() => {
    if (!open) return
    const previouslyFocused = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    // Wait a frame so the panel is mounted, then focus the first field (or the panel itself).
    const raf = requestAnimationFrame(() => {
      const panel = panelRef.current
      if (!panel) return
      const first = panel.querySelector<HTMLElement>('input, textarea, select') ?? panel.querySelector<HTMLElement>(FOCUSABLE)
      ;(first ?? panel).focus()
    })

    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') { e.stopPropagation(); onCloseRef.current(); return }
      if (e.key !== 'Tab' || !panelRef.current) return
      const items = Array.from(panelRef.current.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((el) => el.offsetParent !== null)
      if (items.length === 0) { e.preventDefault(); return }
      const first = items[0]; const last = items[items.length - 1]
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus() }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', onKeyDown)

    return () => {
      cancelAnimationFrame(raf)
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
      previouslyFocused?.focus?.()
    }
  }, [open])

  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="absolute inset-0 bg-black/50 backdrop-blur-sm"
            onClick={onClose}
            aria-hidden="true"
          />
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            tabIndex={-1}
            initial={{ opacity: 0, y: 16, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 10, scale: 0.98 }}
            transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
            className={cn(
              'relative z-10 w-full max-h-[85vh] overflow-y-auto rounded-[var(--radius-lg)] border border-border bg-surface shadow-[var(--shadow-card)] focus:outline-none',
              size === 'sm' && 'max-w-sm',
              size === 'md' && 'max-w-lg',
              size === 'lg' && 'max-w-2xl'
            )}
          >
            <div className="sticky top-0 z-10 flex items-center justify-between border-b border-border bg-surface px-6 py-4">
              <h3 id={titleId} className="font-display text-lg text-ink">{title}</h3>
              <button type="button" onClick={onClose} aria-label="Close dialog" className="rounded-full p-1.5 text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent">
                <X className="h-4.5 w-4.5" />
              </button>
            </div>
            <div className="px-6 py-5">{children}</div>
            {footer && <div className="sticky bottom-0 flex justify-end gap-2 border-t border-border bg-surface px-6 py-4">{footer}</div>}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  )
}
