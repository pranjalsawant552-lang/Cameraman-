import type { ReactNode } from 'react'
import { Bokeh } from '@/components/public/Bokeh'
import { Eyebrow, Reveal, SplitText } from '@/components/public/motionKit'

/** Shared header for inner public pages: aurora wash, grid, bokeh and an animated title. */
export function PageHero({ eyebrow, title, accentLast = 1, subtitle, children }: { eyebrow: string; title: string; accentLast?: number; subtitle?: string; children?: ReactNode }) {
  return (
    <section className="relative overflow-hidden border-b border-border pb-14 pt-32 sm:pb-20 sm:pt-40">
      <div className="aurora" aria-hidden><i /><i /><i /></div>
      <div className="grid-lines" aria-hidden />
      <Bokeh count={14} />
      <div className="relative mx-auto max-w-6xl px-4 sm:px-6">
        <Eyebrow>{eyebrow}</Eyebrow>
        <h1 className="mt-5 max-w-3xl font-display text-4xl leading-[1.08] text-ink sm:text-6xl">
          <SplitText text={title} accentLast={accentLast} />
        </h1>
        {subtitle && (
          <Reveal delay={0.35}>
            <p className="mt-5 max-w-xl text-base leading-relaxed text-ink-muted sm:text-lg">{subtitle}</p>
          </Reveal>
        )}
        {children && <Reveal delay={0.5} className="mt-8">{children}</Reveal>}
      </div>
    </section>
  )
}
