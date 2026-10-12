import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { ArrowRight, BookImage, Check, Clock, Film, GitCompareArrows, HardDrive, Image as ImageIcon, Package as PackageIcon, Truck, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { PageHero } from '@/components/public/PageHero'
import { Magnetic, Reveal, Tilt } from '@/components/public/motionKit'
import { fetchActivePackages } from '@/lib/api'
import type { CatalogPackage } from '@/lib/types'
import { usePageMeta } from '@/lib/usePageMeta'
import { cn, formatCurrency } from '@/lib/utils'

/** The backend sends more than the shared CatalogPackage type declares — read the extras defensively. */
type PublicPackage = CatalogPackage & {
  editedCount?: number
  printCount?: number
  includesAlbum?: boolean
  includesVideo?: boolean
  includesUsb?: boolean
  priceWithGst?: number
  items?: { id?: string; description: string; quantity: number }[]
}

type Sort = 'recommended' | 'low' | 'high'
const MAX_COMPARE = 3

const pretty = (s: string) => s.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())

function facts(p: PublicPackage) {
  const out: { icon: typeof Clock; text: string }[] = []
  if (p.durationHours) out.push({ icon: Clock, text: `${p.durationHours} hr${p.durationHours === 1 ? '' : 's'} of shooting` })
  if (p.photoCount) out.push({ icon: ImageIcon, text: `${p.photoCount} photos` })
  if (p.editedCount) out.push({ icon: ImageIcon, text: `${p.editedCount} fully edited` })
  if (p.printCount) out.push({ icon: ImageIcon, text: `${p.printCount} prints` })
  if (p.deliveryDays) out.push({ icon: Truck, text: `Delivery in ${p.deliveryDays} days` })
  if (p.includesAlbum) out.push({ icon: BookImage, text: 'Album included' })
  if (p.includesVideo) out.push({ icon: Film, text: 'Video included' })
  if (p.includesUsb) out.push({ icon: HardDrive, text: 'USB drive included' })
  return out
}

function Price({ p, big }: { p: PublicPackage; big?: boolean }) {
  const discounted = p.discountedPrice != null && p.discountedPrice < p.basePrice
  return (
    <div>
      <p className="flex flex-wrap items-baseline gap-x-2">
        <span className={cn('font-display text-gradient', big ? 'text-5xl' : 'text-3xl')}>{formatCurrency(p.effectivePrice)}</span>
        {discounted && <span className="text-sm text-ink-faint line-through">{formatCurrency(p.basePrice)}</span>}
      </p>
      {p.priceWithGst != null && p.priceWithGst > p.effectivePrice && (
        <p className="mt-0.5 text-xs text-ink-faint">{formatCurrency(p.priceWithGst)} including GST</p>
      )}
    </div>
  )
}

export default function PublicPackages() {
  usePageMeta('Packages & pricing', 'Browse photography packages, compare what is included and book online.')

  const [packages, setPackages] = useState<PublicPackage[] | null>(null)
  const [failed, setFailed] = useState(false)
  const [category, setCategory] = useState('all')
  const [sort, setSort] = useState<Sort>('recommended')
  const [budget, setBudget] = useState<number | null>(null)
  const [compare, setCompare] = useState<string[]>([])
  const [compareOpen, setCompareOpen] = useState(false)

  useEffect(() => {
    fetchActivePackages()
      .then((p) => setPackages((p as PublicPackage[]).filter((x) => x.active && !x.archived)))
      .catch(() => { setFailed(true); setPackages([]) })
  }, [])

  const categories = useMemo(() => Array.from(new Set((packages ?? []).map((p) => p.category))), [packages])
  const [minPrice, maxPrice] = useMemo(() => {
    const prices = (packages ?? []).map((p) => p.effectivePrice)
    return prices.length ? [Math.min(...prices), Math.max(...prices)] : [0, 0]
  }, [packages])
  const cap = budget ?? maxPrice

  const visible = useMemo(() => {
    const list = (packages ?? []).filter((p) => (category === 'all' || p.category === category) && p.effectivePrice <= cap)
    if (sort === 'low') return [...list].sort((a, b) => a.effectivePrice - b.effectivePrice)
    if (sort === 'high') return [...list].sort((a, b) => b.effectivePrice - a.effectivePrice)
    return [...list].sort((a, b) => a.sortOrder - b.sortOrder)
  }, [packages, category, cap, sort])

  const chosen = (packages ?? []).filter((p) => compare.includes(p.id))
  function toggleCompare(id: string) {
    setCompare((c) => (c.includes(id) ? c.filter((x) => x !== id) : c.length >= MAX_COMPARE ? c : [...c, id]))
  }

  const loading = packages === null
  const compareRows: { label: string; get: (p: PublicPackage) => string }[] = [
    { label: 'Price', get: (p) => formatCurrency(p.effectivePrice) },
    { label: 'Category', get: (p) => pretty(p.category) },
    { label: 'Shoot time', get: (p) => (p.durationHours ? `${p.durationHours} hr` : '—') },
    { label: 'Photos', get: (p) => (p.photoCount ? String(p.photoCount) : '—') },
    { label: 'Edited', get: (p) => (p.editedCount ? String(p.editedCount) : '—') },
    { label: 'Prints', get: (p) => (p.printCount ? String(p.printCount) : '—') },
    { label: 'Delivery', get: (p) => (p.deliveryDays ? `${p.deliveryDays} days` : '—') },
    { label: 'Album', get: (p) => (p.includesAlbum ? 'Yes' : '—') },
    { label: 'Video', get: (p) => (p.includesVideo ? 'Yes' : '—') },
    { label: 'USB drive', get: (p) => (p.includesUsb ? 'Yes' : '—') },
  ]

  return (
    <div>
      <PageHero eyebrow="Packages" title="Find the package that fits your day" accentLast={2} subtitle="Filter by shoot type, set a budget, and compare up to three packages side by side." />

      <section className="mx-auto max-w-6xl px-4 py-14 sm:px-6">
        {/* controls */}
        {!loading && packages && packages.length > 0 && (
          <Reveal className="glass mb-10 flex flex-col gap-5 rounded-[var(--radius-xl)] p-5 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-wrap gap-2" role="tablist" aria-label="Filter by category">
              {['all', ...categories].map((c) => (
                <button
                  key={c}
                  role="tab"
                  aria-selected={category === c}
                  onClick={() => setCategory(c)}
                  className={cn('relative rounded-full px-4 py-1.5 text-sm font-medium transition-colors', category === c ? 'text-accent-ink' : 'text-ink-muted hover:text-ink')}
                >
                  {category === c && <motion.span layoutId="pkg-chip" className="absolute inset-0 rounded-full bg-accent" transition={{ type: 'spring', stiffness: 380, damping: 32 }} />}
                  <span className="relative">{c === 'all' ? 'All' : pretty(c)}</span>
                </button>
              ))}
            </div>
            <div className="flex flex-wrap items-center gap-5">
              {maxPrice > minPrice && (
                <label className="flex items-center gap-3 text-sm text-ink-muted">
                  <span className="whitespace-nowrap">Budget up to <strong className="text-ink">{formatCurrency(cap)}</strong></span>
                  <input
                    type="range"
                    min={minPrice}
                    max={maxPrice}
                    step={Math.max(100, Math.round((maxPrice - minPrice) / 50 / 100) * 100)}
                    value={cap}
                    onChange={(e) => setBudget(Number(e.target.value))}
                    aria-label="Maximum budget"
                    className="h-1.5 w-36 cursor-pointer accent-[var(--c-accent)]"
                  />
                </label>
              )}
              <select
                value={sort}
                onChange={(e) => setSort(e.target.value as Sort)}
                aria-label="Sort packages"
                className="h-9 rounded-full border border-border bg-surface-2 px-3 text-sm text-ink outline-none focus:border-accent"
              >
                <option value="recommended">Recommended</option>
                <option value="low">Price: low to high</option>
                <option value="high">Price: high to low</option>
              </select>
            </div>
          </Reveal>
        )}

        {/* loading */}
        {loading && (
          <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3" aria-busy="true" aria-label="Loading packages">
            {[0, 1, 2].map((i) => <div key={i} className="h-80 animate-pulse rounded-[var(--radius-xl)] border border-border bg-surface/60" />)}
          </div>
        )}

        {/* unavailable / empty */}
        {!loading && packages && packages.length === 0 && (
          <Reveal className="mx-auto max-w-xl rounded-[var(--radius-xl)] border border-dashed border-border bg-surface/60 p-10 text-center">
            <PackageIcon className="mx-auto h-10 w-10 text-accent" />
            <h2 className="mt-4 font-display text-2xl text-ink">{failed ? 'Packages are shared on request' : 'No packages are listed right now'}</h2>
            <p className="mt-2 text-sm text-ink-muted">Tell us about your shoot and we'll send you options that fit. If you already have an account, you can also see packages when you book from your portal.</p>
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <Link to="/contact"><Button className="rounded-full">Request a quote</Button></Link>
              <Link to="/portal/book"><Button variant="outline" className="rounded-full">Open booking</Button></Link>
            </div>
          </Reveal>
        )}

        {/* no match for filters */}
        {!loading && packages && packages.length > 0 && visible.length === 0 && (
          <div className="rounded-[var(--radius-xl)] border border-dashed border-border p-10 text-center text-ink-muted">
            Nothing matches those filters.{' '}
            <button className="font-medium text-accent hover:underline" onClick={() => { setCategory('all'); setBudget(null) }}>Reset filters</button>
          </div>
        )}

        {/* grid */}
        <motion.div layout className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          <AnimatePresence mode="popLayout">
            {visible.map((p, i) => {
              const picked = compare.includes(p.id)
              const f = facts(p)
              const featured = i === 0 && visible.length > 1 && sort === 'recommended' && category === 'all'
              return (
                <motion.div
                  layout
                  key={p.id}
                  initial={{ opacity: 0, y: 30, scale: 0.96 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.94 }}
                  transition={{ duration: 0.45, delay: Math.min(i, 5) * 0.05, ease: [0.16, 1, 0.3, 1] }}
                >
                  <Tilt className="h-full rounded-[var(--radius-xl)]" max={7}>
                    <article className={cn('relative flex h-full flex-col overflow-hidden rounded-[var(--radius-xl)] border bg-surface/85 backdrop-blur', featured ? 'border-spin border-transparent' : picked ? 'border-accent' : 'border-border')}>
                      <div className="relative h-36 overflow-hidden bg-gradient-to-br from-accent/25 via-aurora-2/15 to-aurora-3/20">
                        {p.thumbnailUrl
                          ? <img src={p.thumbnailUrl} alt="" loading="lazy" className="h-full w-full object-cover transition-transform duration-700 hover:scale-110" />
                          : <PackageIcon aria-hidden className="absolute -right-4 -top-4 h-40 w-40 text-accent/20" strokeWidth={0.8} />}
                        <span className="absolute left-4 top-4 rounded-full bg-bg/80 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.1em] text-accent backdrop-blur">{pretty(p.category)}</span>
                        {featured && <span className="absolute right-4 top-4 rounded-full bg-accent px-3 py-1 text-[11px] font-semibold text-accent-ink">Featured</span>}
                      </div>
                      <div className="flex flex-1 flex-col p-6">
                        <h2 className="font-display text-2xl text-ink">{p.name}</h2>
                        <div className="mt-3"><Price p={p} /></div>
                        {p.description && <p className="mt-3 line-clamp-3 text-sm leading-relaxed text-ink-muted">{p.description}</p>}
                        {f.length > 0 && (
                          <ul className="mt-4 space-y-2 text-sm text-ink-muted">
                            {f.slice(0, 5).map((x) => (
                              <li key={x.text} className="flex items-center gap-2.5"><x.icon className="h-4 w-4 shrink-0 text-accent" />{x.text}</li>
                            ))}
                          </ul>
                        )}
                        {p.items && p.items.length > 0 && (
                          <ul className="mt-3 space-y-1.5 border-t border-border pt-3 text-sm text-ink-muted">
                            {p.items.slice(0, 4).map((it, k) => (
                              <li key={it.id ?? k} className="flex items-start gap-2"><Check className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success" />{it.quantity > 1 ? `${it.quantity} × ` : ''}{it.description}</li>
                            ))}
                          </ul>
                        )}
                        <div className="mt-auto flex items-center justify-between gap-3 pt-6">
                          <button
                            onClick={() => toggleCompare(p.id)}
                            disabled={!picked && compare.length >= MAX_COMPARE}
                            aria-pressed={picked}
                            className={cn('inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-medium transition disabled:opacity-40', picked ? 'border-accent bg-accent/12 text-accent' : 'border-border text-ink-muted hover:border-accent hover:text-ink')}
                          >
                            {picked ? <Check className="h-3.5 w-3.5" /> : <GitCompareArrows className="h-3.5 w-3.5" />} {picked ? 'Added' : 'Compare'}
                          </button>
                          <Link to="/portal/book"><Button size="sm" className="rounded-full">Book this <ArrowRight className="h-3.5 w-3.5" /></Button></Link>
                        </div>
                      </div>
                    </article>
                  </Tilt>
                </motion.div>
              )
            })}
          </AnimatePresence>
        </motion.div>

        <Reveal className="mt-14 text-center text-sm text-ink-muted">
          Need something custom? <Link to="/contact" className="font-medium text-accent hover:underline">Ask for a tailored quote</Link>.
        </Reveal>
      </section>

      {/* floating compare tray */}
      <AnimatePresence>
        {compare.length > 0 && (
          <motion.div
            initial={{ y: 80, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 80, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 260, damping: 26 }}
            className="glass fixed inset-x-3 bottom-4 z-40 mx-auto flex max-w-xl items-center justify-between gap-3 rounded-full px-4 py-2.5 shadow-[var(--shadow-card)]"
          >
            <p className="truncate pl-1 text-sm text-ink-muted"><strong className="text-ink">{compare.length}</strong> selected · max {MAX_COMPARE}</p>
            <div className="flex items-center gap-2">
              <button onClick={() => setCompare([])} className="rounded-full px-3 py-1.5 text-xs text-ink-muted hover:text-ink">Clear</button>
              <Magnetic strength={0.15}><Button size="sm" className="rounded-full" disabled={compare.length < 2} onClick={() => setCompareOpen(true)}>Compare {compare.length < 2 ? '(pick 2+)' : ''}</Button></Magnetic>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <Modal open={compareOpen && chosen.length >= 2} onClose={() => setCompareOpen(false)} title="Compare packages" size="lg">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[520px] text-left text-sm">
            <thead>
              <tr>
                <th className="w-28 pb-3" />
                {chosen.map((p) => (
                  <th key={p.id} className="pb-3 pr-3 align-bottom">
                    <div className="flex items-start justify-between gap-2">
                      <span className="font-display text-base text-ink">{p.name}</span>
                      <button aria-label={`Remove ${p.name}`} onClick={() => toggleCompare(p.id)} className="text-ink-faint hover:text-danger"><X className="h-4 w-4" /></button>
                    </div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {compareRows.map((r) => (
                <tr key={r.label} className="border-t border-border">
                  <th scope="row" className="py-2.5 pr-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{r.label}</th>
                  {chosen.map((p) => <td key={p.id} className="py-2.5 pr-3 text-ink">{r.get(p)}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="mt-5 flex justify-end"><Link to="/portal/book"><Button className="rounded-full">Book a session <ArrowRight className="h-4 w-4" /></Button></Link></div>
      </Modal>
    </div>
  )
}
