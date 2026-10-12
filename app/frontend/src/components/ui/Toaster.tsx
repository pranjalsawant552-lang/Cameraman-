import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle2, Info, XCircle } from 'lucide-react'
import { useToastStore } from '@/store/useToastStore'
import { cn } from '@/lib/utils'

const ICONS = { success: CheckCircle2, error: XCircle, info: Info }
const TONE_CLASS = { success: 'text-success', error: 'text-danger', info: 'text-info' }

export function Toaster() {
  const { toasts, dismiss } = useToastStore()
  return (
    <div className="fixed bottom-5 right-5 z-[100] flex flex-col gap-2">
      <AnimatePresence>
        {toasts.map((t) => {
          const Icon = ICONS[t.tone]
          return (
            <motion.div
              key={t.id}
              initial={{ opacity: 0, y: 12, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, x: 40, transition: { duration: 0.15 } }}
              transition={{ type: 'spring', stiffness: 400, damping: 30 }}
              onClick={() => dismiss(t.id)}
              className="flex max-w-sm cursor-pointer items-start gap-2.5 rounded-[var(--radius-md)] border border-border bg-surface px-4 py-3 shadow-[var(--shadow-card)]"
            >
              <Icon className={cn('mt-0.5 h-4.5 w-4.5 shrink-0', TONE_CLASS[t.tone])} />
              <p className="text-sm text-ink">{t.message}</p>
            </motion.div>
          )
        })}
      </AnimatePresence>
    </div>
  )
}
