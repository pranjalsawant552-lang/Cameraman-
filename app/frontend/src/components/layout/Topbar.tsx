import { MobileNav } from './MobileNav'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, X } from 'lucide-react'
import { NotificationBell } from './NotificationBell'
import { ThemeSwitcher } from './ThemeSwitcher'
import { Avatar } from '@/components/ui/primitives'
import { useAuthStore } from '@/store/useAuthStore'
import { fetchQuickSearch, resolveSearchResultRoute, type QuickSearchResult, type SearchResultItem } from '@/lib/api'

const GROUPS: { key: keyof Omit<QuickSearchResult, 'query'>; label: string }[] = [
  { key: 'clients', label: 'Clients' },
  { key: 'bookings', label: 'Bookings' },
  { key: 'galleries', label: 'Galleries' },
  { key: 'invoices', label: 'Invoices' },
  { key: 'products', label: 'Products' },
]

export function Topbar({ title }: { title: string }) {
  const user = useAuthStore((s) => s.user)
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<QuickSearchResult | null>(null)
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const q = query.trim()
    if (q.length < 2) { setResults(null); return }
    setLoading(true)
    const timeout = setTimeout(() => {
      fetchQuickSearch(q).then((res) => { setResults(res); setLoading(false) }).catch(() => setLoading(false))
    }, 250)
    return () => clearTimeout(timeout)
  }, [query])

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  function handleSelect(item: SearchResultItem) {
    navigate(resolveSearchResultRoute(item))
    setOpen(false)
    setQuery('')
    setResults(null)
  }

  const hasAnyResults = !!results && GROUPS.some((g) => results[g.key].length > 0)

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center justify-between gap-3 border-b border-border bg-surface/80 px-4 backdrop-blur-md sm:px-6 lg:pl-8">
      <div className="flex min-w-0 items-center gap-2">
        <MobileNav />
        <h1 className="truncate font-display text-lg text-ink sm:text-xl">{title}</h1>
      </div>

      <div ref={containerRef} className="relative hidden max-w-sm flex-1 md:block">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
        <input
          type="search"
          aria-label="Search bookings, clients and invoices"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => setOpen(true)}
          onKeyDown={(e) => { if (e.key === 'Escape') { setOpen(false); (e.target as HTMLInputElement).blur() } }}
          placeholder="Search bookings, clients, invoices…"
          className="h-9 w-full rounded-full border border-border bg-surface-2 pl-9 pr-8 text-sm text-ink placeholder:text-ink-faint outline-none transition-all focus:border-accent focus:ring-2 focus:ring-accent/20"
        />
        {query && (
          <button type="button" aria-label="Clear search" onClick={() => { setQuery(''); setResults(null) }} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-ink-faint hover:text-ink">
            <X className="h-3.5 w-3.5" />
          </button>
        )}

        {open && query.trim().length >= 2 && (
          <div className="absolute left-0 right-0 top-11 z-30 max-h-96 overflow-y-auto rounded-[var(--radius-lg)] border border-border bg-surface shadow-[var(--shadow-pop)]">
            {loading && <p className="px-4 py-3 text-sm text-ink-faint">Searching…</p>}
            {!loading && !hasAnyResults && <p className="px-4 py-3 text-sm text-ink-faint">No matches for "{query}".</p>}
            {!loading && results && GROUPS.map((g) => {
              const items = results[g.key]
              if (items.length === 0) return null
              return (
                <div key={g.key} className="border-b border-border py-1.5 last:border-0">
                  <p className="px-4 pb-1 pt-1.5 text-[10px] font-semibold uppercase tracking-wide text-ink-faint">{g.label}</p>
                  {items.map((item) => (
                    <button key={item.id} onClick={() => handleSelect(item)} className="flex w-full flex-col items-start px-4 py-2 text-left transition-colors hover:bg-surface-2">
                      <span className="text-sm text-ink">{item.title.trim()}</span>
                      {item.subtitle && <span className="text-xs text-ink-faint">{item.subtitle}</span>}
                    </button>
                  ))}
                </div>
              )
            })}
          </div>
        )}
      </div>

      <div className="flex items-center gap-1.5">
        <ThemeSwitcher />
        <NotificationBell />
        <div className="ml-2 flex items-center gap-2.5 border-l border-border pl-3">
          <Avatar src={user?.profilePhotoUrl} name={`${user?.firstName} ${user?.lastName}`} size={36} />
          <div className="hidden sm:block">
            <p className="text-sm font-medium leading-tight text-ink">{user?.firstName} {user?.lastName}</p>
            <p className="text-[11px] leading-tight text-ink-faint capitalize">{user?.roles[0]?.toLowerCase().replace('_', ' ')}</p>
          </div>
        </div>
      </div>
    </header>
  )
}
