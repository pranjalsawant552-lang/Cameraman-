import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Aperture, CalendarPlus, CornerDownLeft, CircleHelp, Home, Info, LogIn, Mail, Palette, Search, Sparkles, Tag, UserPlus } from 'lucide-react'
import { THEMES, useThemeStore } from '@/store/useThemeStore'
import { setThemeAnimated } from '@/lib/themeTransition'
import { cn } from '@/lib/utils'

interface Command { id: string; label: string; hint: string; icon: typeof Home; run: () => void; keywords?: string }

/** ⌘K / Ctrl+K quick launcher for every public page, sign-in, booking and the four themes. */
export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  return <AnimatePresence>{open && <PaletteBody onClose={onClose} />}</AnimatePresence>
}

function PaletteBody({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate()
  const theme = useThemeStore((s) => s.theme)
  const [query, setQuery] = useState('')
  const [index, setIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  const commands = useMemo<Command[]>(() => {
    const go = (to: string) => () => { navigate(to) }
    return [
      { id: 'home', label: 'Home', hint: 'Page', icon: Home, run: go('/') },
      { id: 'services', label: 'Services', hint: 'Page', icon: Sparkles, run: go('/services'), keywords: 'what we shoot weddings portraits' },
      { id: 'packages', label: 'Packages & pricing', hint: 'Page', icon: Tag, run: go('/packages'), keywords: 'price cost rates' },
      { id: 'about', label: 'About the studio', hint: 'Page', icon: Info, run: go('/about'), keywords: 'story team' },
      { id: 'faq', label: 'FAQ', hint: 'Page', icon: CircleHelp, run: go('/faq'), keywords: 'questions help' },
      { id: 'contact', label: 'Contact', hint: 'Page', icon: Mail, run: go('/contact'), keywords: 'message enquiry phone' },
      { id: 'book', label: 'Book a shoot', hint: 'Action', icon: CalendarPlus, run: go('/portal/book'), keywords: 'reserve appointment session' },
      { id: 'login', label: 'Sign in', hint: 'Account', icon: LogIn, run: go('/login') },
      { id: 'register', label: 'Create account', hint: 'Account', icon: UserPlus, run: go('/register'), keywords: 'sign up' },
      ...THEMES.map<Command>((t) => ({
        id: `theme-${t.id}`,
        label: `Theme: ${t.label}`,
        hint: theme === t.id ? 'Current' : 'Appearance',
        icon: t.id === 'noir' || t.id === 'aperture' ? Aperture : Palette,
        run: () => setThemeAnimated(t.id),
        keywords: `${t.blurb} appearance colour color dark light`,
      })),
    ]
  }, [navigate, theme])

  const results = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return commands
    return commands.filter((c) => `${c.label} ${c.keywords ?? ''}`.toLowerCase().includes(q))
  }, [commands, query])

  useEffect(() => {
    const raf = requestAnimationFrame(() => inputRef.current?.focus())
    return () => cancelAnimationFrame(raf)
  }, [])

  function runAt(i: number) {
    const c = results[i]
    if (!c) return
    onClose()
    c.run()
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Escape') { e.preventDefault(); onClose() }
    else if (e.key === 'ArrowDown') { e.preventDefault(); setIndex((i) => Math.min(i + 1, results.length - 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setIndex((i) => Math.max(i - 1, 0)) }
    else if (e.key === 'Enter') { e.preventDefault(); runAt(index) }
  }

  return (
        <motion.div
          className="fixed inset-0 z-[70] flex items-start justify-center bg-black/55 px-4 pt-[14vh] backdrop-blur-sm"
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
          onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}
        >
          <motion.div
            role="dialog" aria-modal="true" aria-label="Quick navigation"
            initial={{ opacity: 0, y: -16, scale: 0.96, rotateX: -10 }}
            animate={{ opacity: 1, y: 0, scale: 1, rotateX: 0 }}
            exit={{ opacity: 0, y: -10, scale: 0.97 }}
            transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
            onKeyDown={onKeyDown}
            className="glass w-full max-w-lg overflow-hidden rounded-[var(--radius-xl)] shadow-[var(--shadow-card)]"
            style={{ transformOrigin: 'top center' }}
          >
            <div className="flex items-center gap-3 border-b border-border px-4">
              <Search className="h-4 w-4 text-ink-faint" />
              <input
                ref={inputRef}
                autoFocus
                value={query}
                onChange={(e) => { setQuery(e.target.value); setIndex(0) }}
                placeholder="Jump to a page, book a shoot, switch theme…"
                aria-label="Search commands"
                role="combobox"
                aria-expanded="true"
                aria-controls="cmd-list"
                aria-activedescendant={results[index] ? `cmd-${results[index].id}` : undefined}
                className="h-14 w-full bg-transparent text-sm text-ink outline-none placeholder:text-ink-faint"
              />
              <kbd className="rounded border border-border px-1.5 py-0.5 text-[10px] text-ink-faint">Esc</kbd>
            </div>
            <ul id="cmd-list" role="listbox" className="max-h-[50vh] overflow-y-auto p-2">
              {results.length === 0 && <li className="px-3 py-8 text-center text-sm text-ink-muted">Nothing matches “{query}”.</li>}
              {results.map((c, i) => {
                const Icon = c.icon
                return (
                  <li key={c.id} id={`cmd-${c.id}`} role="option" aria-selected={i === index}>
                    <button
                      onMouseMove={() => setIndex(i)}
                      onClick={() => runAt(i)}
                      className={cn('flex w-full items-center gap-3 rounded-[var(--radius-md)] px-3 py-2.5 text-left text-sm transition-colors', i === index ? 'bg-accent/12 text-ink' : 'text-ink-muted')}
                    >
                      <span className={cn('flex h-8 w-8 items-center justify-center rounded-full', i === index ? 'bg-accent text-accent-ink' : 'bg-surface-2')}><Icon className="h-4 w-4" /></span>
                      <span className="flex-1 font-medium">{c.label}</span>
                      <span className="text-xs text-ink-faint">{c.hint}</span>
                      {i === index && <CornerDownLeft className="h-3.5 w-3.5 text-ink-faint" />}
                    </button>
                  </li>
                )
              })}
            </ul>
          </motion.div>
        </motion.div>
  )
}
