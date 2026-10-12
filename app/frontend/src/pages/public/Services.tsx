import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, CalendarPlus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { PageHero } from '@/components/public/PageHero'
import { ProcessTimeline } from '@/components/public/ProcessTimeline'
import { Magnetic, Reveal, SectionHeading, Tilt } from '@/components/public/motionKit'
import { spotlightHandlers } from '@/components/public/pointer'
import { fetchPublicSiteInfo, parseServices } from '@/lib/api'
import { DEFAULT_SERVICES, slugify } from '@/lib/publicContent'
import { resolveServiceIcon } from '@/lib/serviceIcons'
import { usePageMeta } from '@/lib/usePageMeta'

export default function PublicServices() {
  usePageMeta('Services', 'Studio portraits, weddings, maternity and newborn, family sessions, corporate and on-location event photography.')
  const [services, setServices] = useState(DEFAULT_SERVICES)

  useEffect(() => {
    fetchPublicSiteInfo().then((info) => {
      const parsed = parseServices(info.servicesJson)
      if (parsed.length > 0) setServices(parsed)
    }).catch(() => {})
  }, [])

  // Support deep links such as /services#family-sessions once the list has rendered.
  useEffect(() => {
    const id = window.location.hash.slice(1)
    if (id) document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [services])

  return (
    <div>
      <PageHero
        eyebrow="Services"
        title="Whatever you're celebrating, we shoot it"
        accentLast={2}
        subtitle="Studio sessions and on-location coverage, planned with you beforehand so the day itself feels easy."
      >
        <div className="flex flex-wrap gap-3">
          <Magnetic><Link to="/portal/book"><Button size="lg" className="rounded-full"><CalendarPlus className="h-4 w-4" /> Book a shoot</Button></Link></Magnetic>
          <Magnetic><Link to="/packages"><Button size="lg" variant="outline" className="rounded-full bg-surface/50 backdrop-blur">View packages</Button></Link></Magnetic>
        </div>
      </PageHero>

      <section className="mx-auto max-w-6xl space-y-6 px-4 py-20 sm:px-6">
        {services.map((s, i) => {
          const Icon = resolveServiceIcon(s.icon)
          const flip = i % 2 === 1
          return (
            <Reveal key={s.title}>
              <article id={slugify(s.title)} className="scroll-mt-28">
                <Tilt className="rounded-[var(--radius-xl)]" max={3} scale={1.005} glare={false}>
                  <div {...spotlightHandlers()} className={`spotlight grid items-center gap-8 rounded-[var(--radius-xl)] border border-border bg-surface/80 p-6 backdrop-blur sm:p-10 md:grid-cols-[220px_1fr] ${flip ? 'md:grid-cols-[1fr_220px]' : ''}`}>
                    <div className={`flex justify-center ${flip ? 'md:order-2' : ''}`} style={{ perspective: 800 }}>
                      <div
                        className="preserve-3d relative flex h-40 w-40 items-center justify-center rounded-[2rem] border border-accent/30 bg-gradient-to-br from-accent/20 to-aurora-2/10"
                        style={{ transform: 'rotateX(14deg) rotateY(-18deg)', boxShadow: '24px 28px 40px -20px rgba(0,0,0,0.45)' }}
                      >
                        <Icon className="h-16 w-16 text-accent" style={{ transform: 'translateZ(50px)' }} strokeWidth={1.4} />
                        <span className="absolute left-4 top-3 font-display text-4xl text-ink/10" style={{ transform: 'translateZ(20px)' }}>{String(i + 1).padStart(2, '0')}</span>
                      </div>
                    </div>
                    <div>
                      <h2 className="font-display text-2xl text-ink sm:text-3xl">{s.title}</h2>
                      <p className="mt-3 max-w-xl text-base leading-relaxed text-ink-muted">{s.desc}</p>
                      <div className="mt-6 flex flex-wrap items-center gap-3">
                        <Link to={`/contact?topic=${encodeURIComponent(s.title)}`}><Button className="rounded-full">Enquire about this <ArrowRight className="h-4 w-4" /></Button></Link>
                        <Link to="/portal/book"><Button variant="ghost" className="rounded-full">Book online</Button></Link>
                      </div>
                    </div>
                  </div>
                </Tilt>
              </article>
            </Reveal>
          )
        })}
      </section>

      <section className="border-y border-border bg-surface/40 py-20">
        <div className="mx-auto max-w-6xl px-4 sm:px-6">
          <SectionHeading eyebrow="The journey" title="What working with us looks like" align="center" />
          <ProcessTimeline className="mt-14" />
        </div>
      </section>

      <section className="mx-auto max-w-3xl px-4 py-20 text-center sm:px-6">
        <Reveal>
          <h2 className="font-display text-3xl text-ink sm:text-4xl">Not sure which fits?</h2>
          <p className="mt-3 text-ink-muted">Tell us what you have in mind and we'll point you to the right session.</p>
          <div className="mt-7 flex flex-wrap justify-center gap-3">
            <Link to="/contact"><Button size="lg" className="rounded-full">Talk to us</Button></Link>
            <Link to="/faq"><Button size="lg" variant="outline" className="rounded-full">Read the FAQ</Button></Link>
          </div>
        </Reveal>
      </section>
    </div>
  )
}
