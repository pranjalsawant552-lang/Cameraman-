import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Eye, HeartHandshake, Timer } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { LensScene } from '@/components/public/LensScene'
import { PageHero } from '@/components/public/PageHero'
import { Counter, Magnetic, Reveal, SectionHeading, Tilt } from '@/components/public/motionKit'
import { fetchCmsPage, fetchPublicSiteInfo, parseAboutStats } from '@/lib/api'
import type { CmsPage } from '@/lib/api'
import { STUDIO_NAME } from '@/lib/branding'
import { usePageMeta } from '@/lib/usePageMeta'

const VALUES = [
  { icon: HeartHandshake, title: 'Personal, always', desc: 'A small team and a real conversation about what you want before the camera comes out.' },
  { icon: Eye, title: 'An eye for the moment', desc: 'We watch for the in-between frames — the laugh, the glance — not just the posed ones.' },
  { icon: Timer, title: 'Easy from start to finish', desc: 'Book online, follow your shoot, and review your gallery without waiting on a phone call.' },
]

export default function PublicAbout() {
  usePageMeta('About', `About ${STUDIO_NAME} — studio and on-location photography, handled by one team from first booking to final delivery.`)
  const [page, setPage] = useState<CmsPage | undefined>(undefined)
  const [stats, setStats] = useState<{ value: string; label: string }[]>([])

  useEffect(() => {
    fetchCmsPage('about').then(setPage)
    fetchPublicSiteInfo().then((info) => setStats(parseAboutStats(info.aboutStatsJson))).catch(() => {})
  }, [])

  const paragraphs = page?.content
    ? page.content.split('\n').filter(Boolean)
    : [
        `${STUDIO_NAME} started as a single studio room in Pune and has grown into a full-service photography practice — studio portraits, weddings, maternity and newborn sessions, and on-location event coverage, all handled by one team from first booking to final delivery.`,
        'We keep every shoot personal: a small team, real conversations about what you want before the camera comes out, and galleries you can review and select from online instead of waiting on a phone call.',
      ]

  return (
    <div>
      <PageHero eyebrow="About us" title={page?.title ?? `About ${STUDIO_NAME}`} accentLast={1} subtitle="The people and the thinking behind the photographs." />

      <section className="mx-auto grid max-w-6xl items-center gap-14 px-4 py-20 sm:px-6 lg:grid-cols-[1.1fr_0.9fr]">
        <Reveal>
          <div className="space-y-5 text-base leading-relaxed text-ink-muted sm:text-lg">
            {paragraphs.map((p, i) => (
              <p key={i} className={i === 0 ? 'first-letter:float-left first-letter:mr-3 first-letter:font-display first-letter:text-6xl first-letter:leading-[0.85] first-letter:text-accent' : undefined}>{p}</p>
            ))}
          </div>
          <div className="mt-9">
            <Magnetic><Link to="/portal/book"><Button size="lg" className="rounded-full px-7">Book a session with us <ArrowRight className="h-4 w-4" /></Button></Link></Magnetic>
          </div>
        </Reveal>
        <Reveal delay={0.15}><LensScene className="max-w-[420px]" /></Reveal>
      </section>

      {stats.length > 0 && (
        <section className="border-y border-border bg-surface/40">
          <div className="mx-auto grid max-w-6xl grid-cols-2 gap-8 px-4 py-14 sm:px-6 lg:grid-cols-4">
            {stats.map((s, i) => (
              <Reveal key={s.label} delay={i * 0.08} className="text-center">
                <p className="font-display text-5xl text-gradient sm:text-6xl"><Counter value={s.value} /></p>
                <p className="mt-2 text-xs font-medium uppercase tracking-[0.14em] text-ink-faint">{s.label}</p>
              </Reveal>
            ))}
          </div>
        </section>
      )}

      <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
        <SectionHeading eyebrow="How we work" title="What you can count on" align="center" />
        <div className="mt-12 grid gap-5 md:grid-cols-3">
          {VALUES.map((v, i) => (
            <Reveal key={v.title} delay={i * 0.1}>
              <Tilt className="h-full rounded-[var(--radius-xl)]" max={9}>
                <div className="h-full rounded-[var(--radius-xl)] border border-border bg-surface/80 p-7 backdrop-blur">
                  <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-accent/12 text-accent" style={{ transform: 'translateZ(40px)' }}><v.icon className="h-6 w-6" /></span>
                  <p className="mt-5 font-display text-xl text-ink" style={{ transform: 'translateZ(24px)' }}>{v.title}</p>
                  <p className="mt-2 text-sm leading-relaxed text-ink-muted">{v.desc}</p>
                </div>
              </Tilt>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-4xl px-4 pb-24 sm:px-6">
        <Reveal>
          <div className="border-spin relative overflow-hidden rounded-[var(--radius-xl)] border border-border bg-surface p-10 text-center">
            <div className="aurora" aria-hidden><i /><i /><i /></div>
            <div className="relative">
              <h2 className="font-display text-3xl text-ink sm:text-4xl">Let's make something you'll keep</h2>
              <div className="mt-7 flex flex-wrap justify-center gap-3">
                <Link to="/packages"><Button size="lg" className="rounded-full">See packages</Button></Link>
                <Link to="/contact"><Button size="lg" variant="outline" className="rounded-full bg-bg/40 backdrop-blur">Get in touch</Button></Link>
              </div>
            </div>
          </div>
        </Reveal>
      </section>
    </div>
  )
}
