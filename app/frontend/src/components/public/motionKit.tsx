import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import {
  AnimatePresence, animate, motion, useInView, useMotionTemplate, useMotionValue,
  useReducedMotion, useSpring,
} from 'framer-motion'
import { useCoarsePointer } from '@/components/public/pointer'
import { cn } from '@/lib/utils'

const EASE = [0.16, 1, 0.3, 1] as const

/** Fades and lifts its children into view the first time they scroll on screen. */
export function Reveal({ children, delay = 0, y = 28, className }: { children: ReactNode; delay?: number; y?: number; className?: string }) {
  const reduce = useReducedMotion()
  return (
    <motion.div
      className={className}
      initial={reduce ? false : { opacity: 0, y }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-70px' }}
      transition={{ duration: 0.75, delay, ease: EASE }}
    >
      {children}
    </motion.div>
  )
}

/** Headline that reveals word by word with a soft blur-in. The last `accentLast` words get the gradient. */
export function SplitText({ text, className, accentLast = 0, delay = 0 }: { text: string; className?: string; accentLast?: number; delay?: number }) {
  const reduce = useReducedMotion()
  const words = text.split(/\s+/).filter(Boolean)
  return (
    <span className={className} aria-label={text}>
      {words.map((w, i) => {
        const accent = accentLast > 0 && i >= words.length - accentLast
        return (
          <span key={i} aria-hidden className="inline-block overflow-hidden pb-[0.12em] align-bottom">
            <motion.span
              className={cn('inline-block', accent && 'text-gradient')}
              initial={reduce ? false : { y: '110%', opacity: 0, filter: 'blur(8px)' }}
              animate={{ y: 0, opacity: 1, filter: 'blur(0px)' }}
              transition={{ duration: 0.85, delay: delay + i * 0.07, ease: EASE }}
            >
              {w}
            </motion.span>
            {i < words.length - 1 ? '\u00A0' : ''}
          </span>
        )
      })}
    </span>
  )
}

/** Cycles through short phrases with a vertical flip. Holds the first phrase for reduced-motion users. */
export function RotatingWords({ words, className }: { words: string[]; className?: string }) {
  const reduce = useReducedMotion()
  const [i, setI] = useState(0)
  useEffect(() => {
    if (reduce || words.length < 2) return
    const id = window.setInterval(() => setI((n) => (n + 1) % words.length), 2600)
    return () => window.clearInterval(id)
  }, [reduce, words.length])
  if (words.length === 0) return null
  return (
    <span className={cn('relative inline-flex h-[1.4em] min-w-[10ch] overflow-hidden align-bottom', className)} aria-live="polite">
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span
          key={words[i]}
          className="whitespace-nowrap"
          initial={{ y: '100%', opacity: 0, rotateX: -60 }}
          animate={{ y: 0, opacity: 1, rotateX: 0 }}
          exit={{ y: '-100%', opacity: 0, rotateX: 60 }}
          transition={{ duration: 0.55, ease: EASE }}
        >
          {words[i]}
        </motion.span>
      </AnimatePresence>
    </span>
  )
}

/** 3D tilt card with a pointer-following glare. Inert on touch devices and for reduced motion. */
export function Tilt({ children, className, max = 9, scale = 1.02, glare = true }: { children: ReactNode; className?: string; max?: number; scale?: number; glare?: boolean }) {
  const ref = useRef<HTMLDivElement>(null)
  const rx = useSpring(useMotionValue(0), { stiffness: 220, damping: 20 })
  const ry = useSpring(useMotionValue(0), { stiffness: 220, damping: 20 })
  const gx = useMotionValue(50)
  const gy = useMotionValue(50)
  const [hover, setHover] = useState(false)
  const coarse = useCoarsePointer()
  const reduce = useReducedMotion()
  const off = coarse || reduce
  const glareBg = useMotionTemplate`radial-gradient(380px circle at ${gx}% ${gy}%, color-mix(in srgb, var(--c-accent) 24%, transparent), transparent 62%)`

  function onMove(e: React.PointerEvent) {
    const el = ref.current
    if (!el || off) return
    const r = el.getBoundingClientRect()
    const px = (e.clientX - r.left) / r.width
    const py = (e.clientY - r.top) / r.height
    ry.set((px - 0.5) * 2 * max)
    rx.set(-(py - 0.5) * 2 * max)
    gx.set(px * 100)
    gy.set(py * 100)
  }
  function onLeave() {
    rx.set(0); ry.set(0); setHover(false)
  }

  return (
    <div className={className} style={{ perspective: 1100 }}>
      <motion.div
        ref={ref}
        onPointerMove={onMove}
        onPointerEnter={() => !off && setHover(true)}
        onPointerLeave={onLeave}
        style={{ rotateX: off ? 0 : rx, rotateY: off ? 0 : ry, transformStyle: 'preserve-3d' }}
        animate={{ scale: hover ? scale : 1 }}
        transition={{ duration: 0.25 }}
        className="relative h-full rounded-[inherit]"
      >
        {children}
        {glare && !off && (
          <motion.div
            aria-hidden
            className="pointer-events-none absolute inset-0 rounded-[inherit]"
            style={{ background: glareBg, opacity: hover ? 1 : 0, transition: 'opacity 250ms' }}
          />
        )}
      </motion.div>
    </div>
  )
}

/** Pulls its child gently toward the cursor — used on primary calls to action. */
export function Magnetic({ children, strength = 0.28, className }: { children: ReactNode; strength?: number; className?: string }) {
  const ref = useRef<HTMLDivElement>(null)
  const x = useSpring(useMotionValue(0), { stiffness: 240, damping: 16, mass: 0.4 })
  const y = useSpring(useMotionValue(0), { stiffness: 240, damping: 16, mass: 0.4 })
  const coarse = useCoarsePointer()
  const reduce = useReducedMotion()
  return (
    <motion.div
      ref={ref}
      className={cn('inline-block', className)}
      style={{ x, y }}
      onPointerMove={(e) => {
        if (coarse || reduce || !ref.current) return
        const r = ref.current.getBoundingClientRect()
        x.set((e.clientX - (r.left + r.width / 2)) * strength)
        y.set((e.clientY - (r.top + r.height / 2)) * strength)
      }}
      onPointerLeave={() => { x.set(0); y.set(0) }}
    >
      {children}
    </motion.div>
  )
}

/** Number that counts up when scrolled into view. Non-numeric values (and bare years) render as-is. */
export function Counter({ value, className }: { value: string; className?: string }) {
  const ref = useRef<HTMLSpanElement>(null)
  const inView = useInView(ref, { once: true, margin: '-40px' })
  const reduce = useReducedMotion()
  const m = value.match(/^(\D*?)(\d[\d,]*(?:\.\d+)?)(.*)$/)
  const target = m ? parseFloat(m[2].replace(/,/g, '')) : 0
  const isYear = !!m && /^(19|20)\d{2}$/.test(m[2]) && !m[1] && !m[3]
  const animated = !!m && !isYear && !reduce
  const [shown, setShown] = useState(animated ? 0 : target)

  useEffect(() => {
    if (!animated || !inView) return
    const controls = animate(0, target, { duration: 1.8, ease: EASE, onUpdate: setShown })
    return () => controls.stop()
  }, [animated, inView, target])

  if (!m) return <span className={className}>{value}</span>
  const decimals = (m[2].split('.')[1] ?? '').length
  const text = m[2].includes(',')
    ? new Intl.NumberFormat('en-IN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals }).format(shown)
    : shown.toFixed(decimals)
  return <span ref={ref} className={className}>{m[1]}{isYear ? m[2] : text}{m[3]}</span>
}

export function Eyebrow({ children, className, style }: { children: ReactNode; className?: string; style?: CSSProperties }) {
  return (
    <span style={style} className={cn('inline-flex items-center gap-2 rounded-full border border-border bg-surface/60 px-3.5 py-1.5 text-xs font-semibold uppercase tracking-[0.14em] text-accent backdrop-blur', className)}>
      <span className="relative flex h-1.5 w-1.5">
        <span className="absolute inline-flex h-full w-full rounded-full bg-accent" style={{ animation: 'pulse-ring 1.8s ease-out infinite' }} />
        <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-accent" />
      </span>
      {children}
    </span>
  )
}

export function SectionHeading({ eyebrow, title, subtitle, align = 'left', className }: { eyebrow?: string; title: string; subtitle?: string; align?: 'left' | 'center'; className?: string }) {
  return (
    <Reveal className={cn(align === 'center' && 'mx-auto text-center', 'max-w-2xl', className)}>
      {eyebrow && <Eyebrow>{eyebrow}</Eyebrow>}
      <h2 className="mt-4 font-display text-3xl leading-tight text-ink sm:text-4xl">{title}</h2>
      {subtitle && <p className="mt-3 text-base leading-relaxed text-ink-muted">{subtitle}</p>}
    </Reveal>
  )
}
