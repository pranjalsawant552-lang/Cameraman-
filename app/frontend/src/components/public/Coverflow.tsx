import { useEffect, useRef, useState } from 'react'
import { motion, useReducedMotion } from 'framer-motion'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface CoverItem { src: string; title: string; subtitle?: string; href?: string }

/** 3D coverflow carousel: the active card faces front, neighbours fan away in depth. Swipe, click, arrow keys or autoplay. */
export function Coverflow({ items, className }: { items: CoverItem[]; className?: string }) {
  const n = items.length
  const [active, setActive] = useState(0)
  const [paused, setPaused] = useState(false)
  const reduce = useReducedMotion()
  const startX = useRef<number | null>(null)

  useEffect(() => {
    if (reduce || paused || n < 2) return
    const id = window.setInterval(() => setActive((a) => (a + 1) % n), 4200)
    return () => window.clearInterval(id)
  }, [reduce, paused, n])

  if (n === 0) return null
  const go = (d: number) => setActive((a) => (a + d + n) % n)

  return (
    <div
      className={cn('relative', className)}
      onPointerEnter={() => setPaused(true)}
      onPointerLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      <div
        tabIndex={0}
        role="group"
        aria-roledescription="carousel"
        aria-label="Featured work"
        onKeyDown={(e) => { if (e.key === 'ArrowRight') go(1); if (e.key === 'ArrowLeft') go(-1) }}
        onPointerDown={(e) => { startX.current = e.clientX }}
        onPointerUp={(e) => {
          if (startX.current === null) return
          const dx = e.clientX - startX.current
          startX.current = null
          if (Math.abs(dx) > 45) go(dx < 0 ? 1 : -1)
        }}
        className="relative h-[380px] touch-pan-y select-none outline-none sm:h-[460px]"
        style={{ perspective: 1500 }}
      >
        {items.map((it, i) => {
          let off = i - active
          if (off > n / 2) off -= n
          if (off < -n / 2) off += n
          const abs = Math.abs(off)
          const hidden = abs > 3
          return (
            <motion.figure
              key={it.src + i}
              aria-hidden={off !== 0}
              onClick={() => off !== 0 && setActive(i)}
              initial={false}
              animate={{
                x: `${off * 56}%`,
                z: -abs * 150,
                rotateY: off === 0 ? 0 : off > 0 ? -42 : 42,
                scale: 1 - abs * 0.06,
                opacity: hidden ? 0 : 1 - abs * 0.2,
              }}
              transition={{ type: 'spring', stiffness: 150, damping: 20 }}
              style={{ zIndex: 10 - abs, pointerEvents: hidden ? 'none' : 'auto', transformStyle: 'preserve-3d' }}
              className={cn('absolute left-1/2 top-0 h-full w-[56%] max-w-[300px] -translate-x-1/2 sm:w-[300px]', off !== 0 && 'cursor-pointer')}
            >
              <div className="relative h-full overflow-hidden rounded-[var(--radius-xl)] border border-border bg-surface-2 shadow-[0_40px_60px_-30px_rgba(0,0,0,0.65)]">
                <img src={it.src} alt={off === 0 ? it.title : ''} draggable={false} loading="lazy" className="h-full w-full object-cover" />
                <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/75 via-black/30 to-transparent p-5 pt-16">
                  <p className="font-display text-lg text-white">{it.title}</p>
                  {it.subtitle && <p className="mt-0.5 line-clamp-2 text-xs text-white/75">{it.subtitle}</p>}
                </div>
              </div>
              {/* floor reflection */}
              <div aria-hidden className="absolute -bottom-6 left-[8%] right-[8%] h-6 rounded-[100%] bg-black/40 blur-xl" />
            </motion.figure>
          )
        })}
      </div>

      {n > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <button onClick={() => go(-1)} aria-label="Previous" className="flex h-10 w-10 items-center justify-center rounded-full border border-border bg-surface/70 text-ink backdrop-blur transition hover:border-accent hover:text-accent"><ChevronLeft className="h-5 w-5" /></button>
          <div className="flex items-center gap-2" role="tablist" aria-label="Choose slide">
            {items.map((it, i) => (
              <button
                key={i}
                role="tab"
                aria-selected={i === active}
                aria-label={`Show ${it.title}`}
                onClick={() => setActive(i)}
                className={cn('h-1.5 rounded-full transition-all', i === active ? 'w-7 bg-accent' : 'w-1.5 bg-border hover:bg-ink-faint')}
              />
            ))}
          </div>
          <button onClick={() => go(1)} aria-label="Next" className="flex h-10 w-10 items-center justify-center rounded-full border border-border bg-surface/70 text-ink backdrop-blur transition hover:border-accent hover:text-accent"><ChevronRight className="h-5 w-5" /></button>
        </div>
      )}
    </div>
  )
}
