import { useRef } from 'react'
import { motion, useScroll, useSpring, useReducedMotion } from 'framer-motion'
import { PROCESS_STEPS } from '@/lib/publicContent'
import { Reveal, Tilt } from '@/components/public/motionKit'
import { cn } from '@/lib/utils'

/** Four-step "how it works" with a progress line that draws itself as you scroll. */
export function ProcessTimeline({ className }: { className?: string }) {
  const ref = useRef<HTMLDivElement>(null)
  const reduce = useReducedMotion()
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start 75%', 'end 55%'] })
  const progress = useSpring(scrollYProgress, { stiffness: 90, damping: 24 })

  return (
    <div ref={ref} className={cn('relative', className)}>
      {/* horizontal rail (lg+) */}
      <div className="absolute left-0 right-0 top-[27px] hidden h-px bg-border lg:block" aria-hidden>
        <motion.div className="h-full origin-left bg-gradient-to-r from-accent to-aurora-2" style={{ scaleX: reduce ? 1 : progress }} />
      </div>
      {/* vertical rail (<lg) */}
      <div className="absolute bottom-4 left-[27px] top-4 w-px bg-border lg:hidden" aria-hidden>
        <motion.div className="h-full origin-top bg-gradient-to-b from-accent to-aurora-2" style={{ scaleY: reduce ? 1 : progress }} />
      </div>

      <ol className="relative grid gap-8 lg:grid-cols-4 lg:gap-6">
        {PROCESS_STEPS.map((s, i) => (
          <li key={s.title}>
            <Reveal delay={i * 0.1} className="flex gap-5 lg:block">
              <span className="relative z-10 flex h-14 w-14 shrink-0 items-center justify-center rounded-full border border-accent/50 bg-bg font-display text-xl text-accent shadow-[var(--shadow-pop)]">
                {i + 1}
              </span>
              <Tilt className="mt-0 flex-1 rounded-[var(--radius-lg)] lg:mt-6" max={6} scale={1.01}>
                <div className="h-full rounded-[var(--radius-lg)] border border-border bg-surface/70 p-5 backdrop-blur">
                  <p className="font-display text-lg text-ink">{s.title}</p>
                  <p className="mt-1.5 text-sm leading-relaxed text-ink-muted">{s.desc}</p>
                </div>
              </Tilt>
            </Reveal>
          </li>
        ))}
      </ol>
    </div>
  )
}
