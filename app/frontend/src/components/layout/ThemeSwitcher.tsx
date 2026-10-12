import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Check, Palette } from 'lucide-react'
import { THEMES, useThemeStore, type ThemeMeta } from '@/store/useThemeStore'
import { setThemeAnimated } from '@/lib/themeTransition'
import { cn } from '@/lib/utils'

/** A tiny, live-coloured mock of a page in the given theme. */
function ThemePreview({ t }: { t: ThemeMeta }) {
  const p = t.preview
  return (
    <div className="relative h-[74px] w-full overflow-hidden rounded-[10px] border" style={{ background: p.bg, borderColor: `${p.ink}22` }} aria-hidden>
      <span className="absolute -right-4 -top-5 h-14 w-14 rounded-full opacity-60 blur-xl" style={{ background: p.glow }} />
      <span className="absolute left-2 top-2 h-1.5 w-8 rounded-full" style={{ background: p.ink, opacity: 0.85 }} />
      <span className="absolute left-2 top-5 h-1 w-14 rounded-full" style={{ background: p.ink, opacity: 0.35 }} />
      <span className="absolute left-2 top-[30px] h-4 w-9 rounded-md" style={{ background: p.accent }} />
      <span className="absolute bottom-2 left-2 h-5 w-[calc(50%-12px)] rounded-md" style={{ background: p.surface, border: `1px solid ${p.ink}1f` }} />
      <span className="absolute bottom-2 right-2 h-5 w-[calc(50%-12px)] rounded-md" style={{ background: p.surface, border: `1px solid ${p.ink}1f` }} />
    </div>
  )
}

export function ThemeSwitcher({ variant = 'icon' }: { variant?: 'icon' | 'full' }) {
  const { theme } = useThemeStore()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [])

  return (
    <div className="relative" ref={ref}>
      {variant === 'icon' ? (
        <button
          onClick={() => setOpen((o) => !o)}
          aria-label="Change appearance"
          aria-haspopup="dialog"
          aria-expanded={open}
          className="flex h-10 w-10 items-center justify-center rounded-full text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink"
        >
          <Palette className="h-[18px] w-[18px]" />
        </button>
      ) : (
        <button
          onClick={() => setOpen((o) => !o)}
          aria-haspopup="dialog"
          aria-expanded={open}
          className="flex w-full items-center justify-between rounded-[var(--radius-md)] border border-border bg-surface-2 px-4 py-3 text-sm font-medium text-ink transition-colors hover:bg-surface-3"
        >
          <span className="flex items-center gap-2"><Palette className="h-4 w-4" /> Appearance</span>
          <span className="text-ink-muted capitalize">{theme}</span>
        </button>
      )}

      <AnimatePresence>
        {open && (
          <motion.div
            role="dialog"
            aria-label="Choose a look"
            initial={{ opacity: 0, y: -8, scale: 0.96, rotateX: -8 }}
            animate={{ opacity: 1, y: 0, scale: 1, rotateX: 0 }}
            exit={{ opacity: 0, y: -8, scale: 0.96 }}
            transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
            style={{ transformOrigin: 'top right' }}
            className={cn(
              'absolute z-50 w-[22rem] max-w-[calc(100vw-2rem)] rounded-[var(--radius-lg)] border border-border bg-surface p-3 shadow-[var(--shadow-card)]',
              variant === 'icon' ? 'right-0 top-12' : 'left-0 top-full mt-2'
            )}
          >
            <p className="px-1 pb-2 text-xs font-medium text-ink-faint">Choose a look</p>
            <div className="grid grid-cols-2 gap-2">
              {THEMES.map((t) => (
                <button
                  key={t.id}
                  aria-pressed={theme === t.id}
                  onClick={(e) => {
                    setThemeAnimated(t.id, { x: e.clientX, y: e.clientY })
                    setOpen(false)
                  }}
                  className={cn(
                    'group relative flex flex-col gap-2 rounded-[var(--radius-md)] border p-2 text-left transition-all hover:-translate-y-0.5',
                    theme === t.id ? 'border-accent ring-2 ring-accent/25' : 'border-border hover:border-ink-faint'
                  )}
                >
                  <ThemePreview t={t} />
                  <div className="px-1 pb-1">
                    <p className="flex items-center justify-between text-sm font-semibold text-ink">
                      {t.label}
                      {theme === t.id && <Check className="h-4 w-4 text-accent" />}
                    </p>
                    <p className="text-[11px] leading-snug text-ink-faint">{t.blurb}</p>
                  </div>
                </button>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
