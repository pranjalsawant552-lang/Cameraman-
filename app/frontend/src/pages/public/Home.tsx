import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion, useReducedMotion, useScroll, useTransform } from 'framer-motion'
import { ArrowRight, Aperture, ChevronDown, ChevronLeft, ChevronRight, Quote, Star } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Bokeh } from '@/components/public/Bokeh'
import { Coverflow, type CoverItem } from '@/components/public/Coverflow'
import { LensScene } from '@/components/public/LensScene'
import { ProcessTimeline } from '@/components/public/ProcessTimeline'
import { Counter, Eyebrow, Magnetic, Reveal, RotatingWords, SectionHeading, SplitText, Tilt } from '@/components/public/motionKit'
import { spotlightHandlers } from '@/components/public/pointer'
import { fetchActivePackages, fetchCmsSlides, fetchCmsTestimonials, fetchPublicSiteInfo, parseAboutStats, parseServices } from '@/lib/api'
import type { AboutStat, CmsSlide, CmsTestimonial } from '@/lib/api'
import type { CatalogPackage } from '@/lib/types'
import { DEFAULT_SERVICES } from '@/lib/publicContent'
import { resolveServiceIcon } from '@/lib/serviceIcons'
import { usePageMeta } from '@/lib/usePageMeta'
import { formatCurrency, initials } from '@/lib/utils'

function TestimonialCarousel({ items }: { items: CmsTestimonial[] }) {
  const [i, setI] = useState(0)
  const [paused, setPaused] = useState(false)
  const reduce = useReducedMotion()
  const n = items.length

  useEffect(() => {
    if (reduce || paused || n < 2) return
    const id = window.setInterval(() => setI((x) => (x + 1) % n), 6500)
    return () => window.clearInterval(id)
  }, [reduce, paused, n])

  const t = items[i]
  if (!t) return null
  return (
    <div onPointerEnter={() => setPaused(true)} onPointerLeave={() => setPaused(false)} className="relative mx-auto max-w-3xl" style={{ perspective: 1400 }}>
      <div className="relative pb-10">
      {/* depth cards behind the active one */}
      <div aria-hidden className="absolute inset-x-6 bottom-0 top-4 rounded-[var(--radius-xl)] border border-border bg-surface/50" style={{ transform: 'translateZ(-60px) scale(0.96)' }} />
      <div aria-hidden className="absolute inset-x-12 bottom-[-6px] top-8 rounded-[var(--radius-xl)] border border-border bg-surface/30" style={{ transform: 'translateZ(-120px) scale(0.92)' }} />
      <AnimatePresence mode="wait">
        <motion.figure
          key={t.id}
          initial={{ opacity: 0, rotateX: -18, y: 24 }}
          animate={{ opacity: 1, rotateX: 0, y: 0 }}
          exit={{ opacity: 0, rotateX: 14, y: -16 }}
          transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
          className="glass relative rounded-[var(--radius-xl)] p-8 text-center sm:p-12"
          style={{ transformOrigin: 'center bottom' }}
        >
          <Quote className="mx-auto h-8 w-8 text-accent/60" />
          <div className="mt-4 flex justify-center gap-1" aria-label={`${t.rating} out of 5 stars`}>
            {Array.from({ length: Math.max(0, Math.min(5, t.rating)) }).map((_, k) => <Star key={k} className="h-4 w-4 fill-accent text-accent" />)}
          </div>
          <blockquote className="mt-5 font-display text-xl leading-relaxed text-ink sm:text-2xl">“{t.review}”</blockquote>
          <figcaption className="mt-7 flex items-center justify-center gap-3">
            {t.clientPhotoUrl ? (
              <img src={t.clientPhotoUrl} alt="" className="h-11 w-11 rounded-full object-cover ring-2 ring-accent/40" />
            ) : (
              <span className="flex h-11 w-11 items-center justify-center rounded-full bg-accent/15 text-sm font-semibold text-accent ring-2 ring-accent/30">{initials(t.clientName)}</span>
            )}
            <span className="text-left">
              <span className="block text-sm font-semibold text-ink">{t.clientName}</span>
              {t.packageType && <span className="block text-xs text-ink-faint">{t.packageType}</span>}
            </span>
          </figcaption>
        </motion.figure>
      </AnimatePresence>
      </div>
      {n > 1 && (
        <div className="mt-7 flex items-center justify-center gap-4">
          <button onClick={() => setI((x) => (x - 1 + n) % n)} aria-label="Previous review" className="flex h-9 w-9 items-center justify-center rounded-full border border-border text-ink-muted transition hover:border-accent hover:text-accent"><ChevronLeft className="h-4 w-4" /></button>
          <div className="flex gap-2">
            {items.map((x, k) => <button key={x.id} onClick={() => setI(k)} aria-label={`Review ${k + 1}`} aria-current={k === i} className={`h-1.5 rounded-full transition-all ${k === i ? 'w-7 bg-accent' : 'w-1.5 bg-border hover:bg-ink-faint'}`} />)}
          </div>
          <button onClick={() => setI((x) => (x + 1) % n)} aria-label="Next review" className="flex h-9 w-9 items-center justify-center rounded-full border border-border text-ink-muted transition hover:border-accent hover:text-accent"><ChevronRight className="h-4 w-4" /></button>
        </div>
      )}
    </div>
  )
}

export default function PublicHome() {
  usePageMeta('Studio & on-location photography', 'Studio and on-location photography — weddings, portraits, maternity, newborn and events. Book online and review your gallery in your own client portal.')

  const [slides, setSlides] = useState<CmsSlide[]>([])
  const [testimonials, setTestimonials] = useState<CmsTestimonial[]>([])
  const [services, setServices] = useState(DEFAULT_SERVICES)
  const [stats, setStats] = useState<AboutStat[]>([])
  const [packages, setPackages] = useState<CatalogPackage[]>([])

  useEffect(() => {
    fetchCmsSlides().then(setSlides).catch(() => {})
    fetchCmsTestimonials().then(setTestimonials).catch(() => {})
    fetchPublicSiteInfo().then((info) => {
      const parsed = parseServices(info.servicesJson)
      if (parsed.length > 0) setServices(parsed)
      setStats(parseAboutStats(info.aboutStatsJson))
    }).catch(() => {})
    // Needs GET /packages/active to be public (see SecurityConfig); quietly hides the teaser otherwise.
    fetchActivePackages().then((p) => setPackages(p.filter((x) => x.active && !x.archived).sort((a, b) => a.sortOrder - b.sortOrder))).catch(() => {})
  }, [])

  const hero = slides[0]
  const slideImages = useMemo(() => slides.map((s) => s.imageUrl).filter((u): u is string => !!u), [slides])
  const coverItems: CoverItem[] = useMemo(
    () => slides.filter((s) => s.imageUrl).map((s) => ({ src: s.imageUrl as string, title: s.title, subtitle: s.subtitle })),
    [slides],
  )
  const avgRating = testimonials.length ? testimonials.reduce((a, t) => a + t.rating, 0) / testimonials.length : 0
  const rotating = useMemo(() => services.slice(0, 6).map((s) => s.title), [services])

  // Hero parallax on scroll
  const heroRef = useRef<HTMLElement>(null)
  const { scrollYProgress } = useScroll({ target: heroRef, offset: ['start start', 'end start'] })
  const copyY = useTransform(scrollYProgress, [0, 1], [0, 90])
  const sceneY = useTransform(scrollYProgress, [0, 1], [0, -60])
  const heroFade = useTransform(scrollYProgress, [0, 0.8], [1, 0.2])

  const headline = hero?.title ?? 'Photographs your family will keep for generations'

  return (
    <div>
      {/* ───────────── Hero ───────────── */}
      <section ref={heroRef} className="relative isolate overflow-hidden pb-16 pt-32 sm:pt-36 lg:min-h-[100svh] lg:pb-20">
        <div className="aurora" aria-hidden><i /><i /><i /></div>
        <div className="grid-lines" aria-hidden />
        <Bokeh count={26} />

        <div className="relative mx-auto grid max-w-6xl grid-cols-1 items-center gap-10 px-4 sm:px-6 lg:grid-cols-[1.05fr_0.95fr]">
          <motion.div style={{ y: copyY, opacity: heroFade }}>
            <Eyebrow>Studio &amp; on-location photography</Eyebrow>
            <h1 className="mt-6 font-display text-[2.6rem] leading-[1.05] text-ink sm:text-6xl xl:text-7xl">
              <SplitText text={headline} accentLast={2} />
            </h1>
            <Reveal delay={0.5}>
              <p className="mt-6 max-w-lg text-base leading-relaxed text-ink-muted sm:text-lg">
                {hero?.subtitle ?? 'Studio and on-location photography in Pune — weddings, portraits, maternity, newborn and events, shot with care and delivered fast.'}
              </p>
              <p className="mt-5 flex items-center gap-2 font-display text-lg text-ink sm:text-xl">
                <Aperture className="h-5 w-5 text-accent" /> We shoot <RotatingWords words={rotating} className="text-accent" />
              </p>
            </Reveal>
            <Reveal delay={0.65} className="mt-9 flex flex-wrap items-center gap-4">
              <Magnetic><Link to={hero?.ctaUrl && hero.ctaUrl.startsWith('/') ? hero.ctaUrl : '/portal/book'}><Button size="lg" className="rounded-full px-7">{hero?.ctaText ?? 'Create an account & book'} <ArrowRight className="h-4 w-4" /></Button></Link></Magnetic>
              <Magnetic><Link to="/packages"><Button size="lg" variant="outline" className="rounded-full bg-surface/50 px-7 backdrop-blur">See packages</Button></Link></Magnetic>
            </Reveal>
            {avgRating > 0 && (
              <Reveal delay={0.8} className="mt-9 flex items-center gap-3 text-sm text-ink-muted">
                <span className="flex gap-0.5">{Array.from({ length: 5 }).map((_, k) => <Star key={k} className={`h-4 w-4 ${k < Math.round(avgRating) ? 'fill-accent text-accent' : 'text-border'}`} />)}</span>
                <span><strong className="text-ink">{avgRating.toFixed(1)}</strong> average from {testimonials.length} client review{testimonials.length === 1 ? '' : 's'}</span>
              </Reveal>
            )}
          </motion.div>

          <motion.div style={{ y: sceneY }} className="relative">
            <LensScene images={slideImages.slice(1, 4)} />
          </motion.div>
        </div>

        <a href="#what-we-shoot" aria-label="Scroll to services" className="absolute bottom-6 left-1/2 hidden -translate-x-1/2 flex-col items-center gap-0.5 text-xs text-ink-faint transition hover:text-accent lg:flex">
          Scroll <ChevronDown className="h-4 w-4 animate-bounce" />
        </a>
      </section>

      {/* ───────────── Marquee ───────────── */}
      <div className="film-edge" aria-hidden />
      <div className="marquee overflow-hidden border-b border-border bg-surface/40 py-5" aria-hidden>
        <div className="marquee-track">
          {[0, 1].map((dup) => (
            <div key={dup} className="flex shrink-0 items-center">
              {services.map((s) => (
                <span key={s.title + dup} className="flex items-center gap-8 pr-8 font-display text-2xl text-ink-muted sm:text-3xl">
                  {s.title}<Aperture className="h-5 w-5 text-accent" />
                </span>
              ))}
            </div>
          ))}
        </div>
      </div>

      {/* ───────────── Stats ───────────── */}
      {stats.length > 0 && (
        <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {stats.slice(0, 4).map((s, i) => (
              <Reveal key={s.label} delay={i * 0.08}>
                <Tilt className="rounded-[var(--radius-lg)]" max={8}>
                  <div className="glass h-full rounded-[var(--radius-lg)] p-6 text-center">
                    <p className="font-display text-4xl text-gradient sm:text-5xl"><Counter value={s.value} /></p>
                    <p className="mt-2 text-xs font-medium uppercase tracking-[0.12em] text-ink-faint">{s.label}</p>
                  </div>
                </Tilt>
              </Reveal>
            ))}
          </div>
        </section>
      )}

      {/* ───────────── Services ───────────── */}
      <section id="what-we-shoot" className="relative mx-auto max-w-6xl scroll-mt-24 px-4 py-20 sm:px-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <SectionHeading eyebrow="What we shoot" title="Every story, framed with care" subtitle="From a quiet newborn session to a three-day wedding — one team looks after you from first booking to final delivery." />
          <Reveal><Link to="/services" className="group inline-flex items-center gap-1.5 text-sm font-semibold text-accent">All services <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" /></Link></Reveal>
        </div>
        <div className="mt-12 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {services.map((s, i) => {
            const Icon = resolveServiceIcon(s.icon)
            return (
              <Reveal key={s.title} delay={(i % 3) * 0.08}>
                <Tilt className="h-full rounded-[var(--radius-lg)]" max={10}>
                  <div {...spotlightHandlers()} className="spotlight group h-full rounded-[var(--radius-lg)] border border-border bg-surface/80 p-6 backdrop-blur transition-colors hover:border-accent/50">
                    <div className="flex items-start justify-between">
                      <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-accent/12 text-accent shadow-[inset_0_0_0_1px_color-mix(in_srgb,var(--c-accent)_30%,transparent)]" style={{ transform: 'translateZ(40px)' }}>
                        <Icon className="h-6 w-6" />
                      </span>
                      <span className="font-display text-3xl text-ink/10">{String(i + 1).padStart(2, '0')}</span>
                    </div>
                    <p className="mt-5 font-display text-xl text-ink" style={{ transform: 'translateZ(24px)' }}>{s.title}</p>
                    <p className="mt-2 text-sm leading-relaxed text-ink-muted">{s.desc}</p>
                    <Link to={`/contact?topic=${encodeURIComponent(s.title)}`} className="mt-5 inline-flex items-center gap-1.5 text-sm font-semibold text-accent opacity-80 transition-all group-hover:gap-2.5 group-hover:opacity-100">
                      Enquire <ArrowRight className="h-4 w-4" />
                    </Link>
                  </div>
                </Tilt>
              </Reveal>
            )
          })}
        </div>
      </section>

      {/* ───────────── Process ───────────── */}
      <section className="relative border-y border-border bg-surface/40 py-20">
        <div className="mx-auto max-w-6xl px-4 sm:px-6">
          <SectionHeading eyebrow="How it works" title="From “hello” to your gallery in four steps" align="center" />
          <ProcessTimeline className="mt-14" />
        </div>
      </section>

      {/* ───────────── Featured work (3D coverflow) ───────────── */}
      {coverItems.length > 0 && (
        <section className="relative overflow-hidden py-20">
          <div className="aurora opacity-60" aria-hidden><i /><i /><i /></div>
          <div className="relative mx-auto max-w-6xl px-4 sm:px-6">
            <SectionHeading eyebrow="Featured work" title="A look through the lens" subtitle="Drag, swipe or use the arrow keys to move through recent frames." align="center" />
            <Reveal className="mt-12"><Coverflow items={coverItems} /></Reveal>
          </div>
        </section>
      )}

      {/* ───────────── Packages teaser ───────────── */}
      {packages.length > 0 && (
        <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <SectionHeading eyebrow="Packages" title="Clear packages, no guesswork" subtitle="Pick what fits and book it online — the details of every package are one click away." />
            <Reveal><Link to="/packages" className="group inline-flex items-center gap-1.5 text-sm font-semibold text-accent">Compare all packages <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" /></Link></Reveal>
          </div>
          <div className="mt-12 grid grid-cols-1 gap-5 md:grid-cols-3">
            {packages.slice(0, 3).map((p, i) => (
              <Reveal key={p.id} delay={i * 0.1}>
                <Tilt className="h-full rounded-[var(--radius-xl)]" max={8}>
                  <div className={`h-full rounded-[var(--radius-xl)] border bg-surface/80 p-7 backdrop-blur ${i === 1 ? 'border-spin border-transparent' : 'border-border'}`}>
                    <p className="text-xs font-semibold uppercase tracking-[0.12em] text-accent">{p.category.replace(/_/g, ' ')}</p>
                    <p className="mt-2 font-display text-2xl text-ink">{p.name}</p>
                    <p className="mt-4 flex items-baseline gap-2">
                      <span className="font-display text-4xl text-gradient">{formatCurrency(p.effectivePrice)}</span>
                      {p.discountedPrice != null && p.discountedPrice < p.basePrice && <span className="text-sm text-ink-faint line-through">{formatCurrency(p.basePrice)}</span>}
                    </p>
                    {p.description && <p className="mt-3 line-clamp-3 text-sm leading-relaxed text-ink-muted">{p.description}</p>}
                    <Link to="/packages" className="mt-6 inline-flex"><Button variant="outline" className="rounded-full">View details <ArrowRight className="h-4 w-4" /></Button></Link>
                  </div>
                </Tilt>
              </Reveal>
            ))}
          </div>
        </section>
      )}

      {/* ───────────── Testimonials ───────────── */}
      {testimonials.length > 0 && (
        <section className="relative border-t border-border bg-surface/40 py-20">
          <div className="mx-auto max-w-6xl px-4 sm:px-6">
            <SectionHeading eyebrow="Kind words" title="From people we've photographed" align="center" />
            <Reveal className="mt-12"><TestimonialCarousel items={testimonials} /></Reveal>
          </div>
        </section>
      )}

      {/* ───────────── CTA ───────────── */}
      <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
        <Reveal>
          <div className="border-spin relative overflow-hidden rounded-[var(--radius-xl)] border border-border bg-surface px-6 py-14 text-center sm:px-14 sm:py-20">
            <div className="aurora" aria-hidden><i /><i /><i /></div>
            <Aperture aria-hidden className="spin-slow absolute -right-16 -top-16 h-72 w-72 text-accent/10" strokeWidth={0.6} />
            <Aperture aria-hidden className="spin-slow-rev absolute -bottom-20 -left-12 h-60 w-60 text-aurora-2/15" strokeWidth={0.6} />
            <div className="relative">
              <h2 className="mx-auto max-w-2xl font-display text-3xl leading-tight text-ink sm:text-5xl">Ready to book your <span className="text-gradient">session</span>?</h2>
              <p className="mx-auto mt-4 max-w-lg text-base text-ink-muted">Create a free account to check availability, request a date, and follow your gallery once it's ready.</p>
              <div className="mt-9 flex flex-wrap items-center justify-center gap-4">
                <Magnetic><Link to="/portal/book"><Button size="lg" className="rounded-full px-8">Book a session <ArrowRight className="h-4 w-4" /></Button></Link></Magnetic>
                <Magnetic><Link to="/contact"><Button size="lg" variant="outline" className="rounded-full bg-bg/40 px-8 backdrop-blur">Ask a question</Button></Link></Magnetic>
              </div>
            </div>
          </div>
        </Reveal>
      </section>
    </div>
  )
}
