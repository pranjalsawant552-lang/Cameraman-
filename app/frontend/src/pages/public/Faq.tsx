import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { MessageCircle, Plus, Search } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { PageHero } from '@/components/public/PageHero'
import { Reveal } from '@/components/public/motionKit'
import { fetchCmsPage, fetchPublicSiteInfo } from '@/lib/api'
import { DEFAULT_FAQ, parseFaq, whatsappLink, type FaqItem } from '@/lib/publicContent'
import { usePageMeta } from '@/lib/usePageMeta'
import { cn } from '@/lib/utils'

export default function PublicFaq() {
  usePageMeta('FAQ', 'Answers to common questions about booking, shoot days, photo delivery and payments.')
  const [items, setItems] = useState<FaqItem[]>(DEFAULT_FAQ)
  const [wa, setWa] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [cat, setCat] = useState('All')
  const [open, setOpen] = useState<string | null>(DEFAULT_FAQ[0].q)

  useEffect(() => {
    // A CMS page with the key "faq" replaces the starter answers with the studio's own.
    fetchCmsPage('faq').then((p) => {
      const parsed = parseFaq(p?.content)
      if (parsed.length > 0) { setItems(parsed); setOpen(parsed[0].q) }
    }).catch(() => {})
    fetchPublicSiteInfo().then((i) => setWa(whatsappLink(i.whatsappNumber, 'Hi, I have a question about a shoot.'))).catch(() => {})
  }, [])

  // Search-engine friendly structured data for the visible questions.
  useEffect(() => {
    const el = document.createElement('script')
    el.type = 'application/ld+json'
    el.text = JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      mainEntity: items.map((i) => ({ '@type': 'Question', name: i.q, acceptedAnswer: { '@type': 'Answer', text: i.a } })),
    })
    document.head.appendChild(el)
    return () => { el.remove() }
  }, [items])

  const cats = useMemo(() => ['All', ...Array.from(new Set(items.map((i) => i.cat).filter((c): c is string => !!c)))], [items])
  const shown = useMemo(() => {
    const q = query.trim().toLowerCase()
    return items.filter((i) => (cat === 'All' || i.cat === cat) && (!q || `${i.q} ${i.a}`.toLowerCase().includes(q)))
  }, [items, query, cat])

  return (
    <div>
      <PageHero eyebrow="FAQ" title="Questions, answered" accentLast={1} subtitle="Everything people usually ask before booking. Can't find yours? Just message us." />

      <section className="mx-auto max-w-3xl px-4 py-14 sm:px-6">
        <Reveal className="relative">
          <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search questions…"
            aria-label="Search questions"
            className="glass h-12 w-full rounded-full pl-11 pr-4 text-sm text-ink outline-none transition placeholder:text-ink-faint focus:border-accent focus:ring-2 focus:ring-accent/20"
          />
        </Reveal>

        {cats.length > 2 && (
          <div className="mt-5 flex flex-wrap gap-2" role="tablist" aria-label="FAQ categories">
            {cats.map((c) => (
              <button key={c} role="tab" aria-selected={cat === c} onClick={() => setCat(c)} className={cn('rounded-full border px-3.5 py-1.5 text-sm transition', cat === c ? 'border-accent bg-accent text-accent-ink' : 'border-border text-ink-muted hover:border-ink-faint hover:text-ink')}>{c}</button>
            ))}
          </div>
        )}

        <div className="mt-8 space-y-3">
          {shown.length === 0 && <p className="py-10 text-center text-ink-muted">No questions match “{query}”. Try fewer words, or ask us directly below.</p>}
          {shown.map((it, i) => {
            const isOpen = open === it.q
            const id = `faq-${i}`
            return (
              <Reveal key={it.q} delay={Math.min(i, 6) * 0.04}>
                <div className={cn('overflow-hidden rounded-[var(--radius-lg)] border bg-surface/70 backdrop-blur transition-colors', isOpen ? 'border-accent/60' : 'border-border')}>
                  <h2>
                    <button onClick={() => setOpen(isOpen ? null : it.q)} aria-expanded={isOpen} aria-controls={id} className="flex w-full items-center justify-between gap-4 px-5 py-4 text-left">
                      <span className="font-medium text-ink">{it.q}</span>
                      <motion.span animate={{ rotate: isOpen ? 135 : 0 }} transition={{ duration: 0.25 }} className={cn('flex h-7 w-7 shrink-0 items-center justify-center rounded-full', isOpen ? 'bg-accent text-accent-ink' : 'bg-surface-2 text-ink-muted')}><Plus className="h-4 w-4" /></motion.span>
                    </button>
                  </h2>
                  <AnimatePresence initial={false}>
                    {isOpen && (
                      <motion.div id={id} role="region" initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}>
                        <p className="px-5 pb-5 text-sm leading-relaxed text-ink-muted">{it.a}</p>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </Reveal>
            )
          })}
        </div>

        <Reveal className="mt-14">
          <div className="border-spin relative overflow-hidden rounded-[var(--radius-xl)] border border-border bg-surface p-8 text-center">
            <h2 className="font-display text-2xl text-ink">Still have a question?</h2>
            <p className="mt-2 text-sm text-ink-muted">We usually reply within a business day.</p>
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <Link to="/contact"><Button className="rounded-full">Send a message</Button></Link>
              {wa && <a href={wa} target="_blank" rel="noopener noreferrer"><Button variant="outline" className="rounded-full"><MessageCircle className="h-4 w-4" /> WhatsApp us</Button></a>}
            </div>
          </div>
        </Reveal>
      </section>
    </div>
  )
}
