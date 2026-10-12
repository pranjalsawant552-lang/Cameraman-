import { useEffect, useRef, type CSSProperties } from 'react'
import { motion, useMotionValue, useReducedMotion, useSpring, useTransform } from 'framer-motion'
import { useCoarsePointer } from '@/components/public/pointer'
import { cn } from '@/lib/utils'

const BLADES = 9

/** One iris blade: a curved wedge pivoting around the outer edge of the aperture. */
function Aperture() {
  const blades = Array.from({ length: BLADES }, (_, i) => i)
  return (
    <svg viewBox="-50 -50 100 100" className="h-full w-full" aria-hidden>
      <defs>
        <linearGradient id="blade-g" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="var(--c-surface-3)" />
          <stop offset="1" stopColor="var(--c-bg)" />
        </linearGradient>
      </defs>
      <circle r="49" fill="var(--c-bg)" />
      <motion.g
        animate={{ rotate: [0, 16, 0] }}
        transition={{ duration: 7, repeat: Infinity, ease: 'easeInOut' }}
      >
        {blades.map((i) => (
          <g key={i} transform={`rotate(${(360 / BLADES) * i})`}>
            <path
              d="M 0 -49 C 22 -44, 40 -26, 44 -4 L 12 -8 C 8 -16, 4 -26, 0 -49 Z"
              fill="url(#blade-g)"
              stroke="var(--c-border)"
              strokeWidth="0.6"
            />
            <path d="M 4 -42 C 18 -38, 32 -26, 38 -10" fill="none" stroke="var(--c-accent)" strokeOpacity="0.35" strokeWidth="0.5" />
          </g>
        ))}
      </motion.g>
      <circle r="13" fill="var(--c-bg)" />
    </svg>
  )
}

function Orbit({ z, clip }: { z: number; clip: string }) {
  return (
    <div className="absolute inset-[-12%]" style={{ transform: `translateZ(${z}px) rotateZ(-14deg) scaleY(0.3)`, clipPath: clip }}>
      <div className="spin-slow absolute inset-0 rounded-full border border-dashed" style={{ borderColor: 'color-mix(in srgb, var(--c-accent) 60%, transparent)' }}>
        <span className="absolute left-1/2 top-0 h-3.5 w-3.5 rounded-full bg-accent shadow-[0_0_18px_var(--c-accent)]" style={{ transform: 'translate(-50%,-50%) scaleY(3.3)' }} />
        <span className="absolute bottom-[8%] left-[20%] h-2.5 w-2.5 rounded-full bg-aurora-2 shadow-[0_0_14px_var(--c-aurora-2)]" style={{ transform: 'scaleY(3.3)' }} />
      </div>
    </div>
  )
}

function Chip({ children, style, delay = 0 }: { children: React.ReactNode; style: CSSProperties; delay?: number }) {
  return (
    <div className="absolute" style={{ ...style, transformStyle: 'preserve-3d' }}>
      <div
        className="float-y glass rounded-full px-3 py-1 font-mono text-[11px] font-semibold tracking-wider text-ink shadow-[var(--shadow-card)]"
        style={{ animationDelay: `${delay}s` }}
      >
        {children}
      </div>
    </div>
  )
}

/**
 * A 3D camera lens built from stacked, depth-separated rings (pure CSS 3D — no WebGL,
 * no extra dependency). It tilts toward the pointer, shows a breathing aperture iris,
 * floating exposure chips and, when provided, framed prints hanging at different depths.
 */
export function LensScene({ images = [], className }: { images?: string[]; className?: string }) {
  const stage = useRef<HTMLDivElement>(null)
  const mx = useMotionValue(0)
  const my = useMotionValue(0)
  const sx = useSpring(mx, { stiffness: 70, damping: 14 })
  const sy = useSpring(my, { stiffness: 70, damping: 14 })
  const rotY = useTransform(sx, [-1, 1], [-24, 24])
  const rotX = useTransform(sy, [-1, 1], [18, -18])
  const coarse = useCoarsePointer()
  const reduce = useReducedMotion()
  const interactive = !coarse && !reduce

  useEffect(() => {
    if (!interactive) return
    function onMove(e: PointerEvent) {
      mx.set((e.clientX / window.innerWidth) * 2 - 1)
      my.set((e.clientY / window.innerHeight) * 2 - 1)
    }
    window.addEventListener('pointermove', onMove, { passive: true })
    return () => window.removeEventListener('pointermove', onMove)
  }, [interactive, mx, my])

  const ring = (size: number, z: number, extra?: CSSProperties): CSSProperties => ({
    width: `${size}%`, height: `${size}%`, left: `${(100 - size) / 2}%`, top: `${(100 - size) / 2}%`,
    transform: `translateZ(${z}px)`, ...extra,
  })

  const frames = images.slice(0, 3)
  const framePos: CSSProperties[] = [
    { left: '-6%', top: '8%', width: '34%', transform: 'translateZ(150px) rotateZ(-9deg)' },
    { right: '-8%', top: '26%', width: '30%', transform: 'translateZ(190px) rotateZ(7deg)' },
    { left: '8%', bottom: '-4%', width: '28%', transform: 'translateZ(120px) rotateZ(5deg)' },
  ]

  return (
    <div ref={stage} className={cn('relative mx-auto aspect-square w-full max-w-[520px]', className)} style={{ perspective: 1300 }} aria-hidden>
      {/* soft glow plate behind everything */}
      <div className="absolute inset-[6%] rounded-full opacity-70 blur-3xl" style={{ background: 'radial-gradient(circle, var(--c-aurora-1), var(--c-aurora-2) 55%, transparent 75%)' }} />

      <motion.div
        className="preserve-3d absolute inset-0"
        style={interactive ? { rotateX: rotX, rotateY: rotY } : undefined}
      >
        <div className={cn('preserve-3d absolute inset-0', !interactive && !reduce && 'lens-sway')}>
          {/* orbiting guide ring: drawn as a back half (behind the lens) and a front half (in front of it)
              so it reads as circling the lens without slicing through the layers */}
          <Orbit z={-40} clip="inset(0 0 50% 0)" />
          <Orbit z={210} clip="inset(50% 0 0 0)" />

          {/* barrel */}
          <div className="absolute rounded-full" style={ring(100, 0, {
            background: 'conic-gradient(from 210deg, var(--c-surface-3), var(--c-surface), var(--c-surface-3), var(--c-ink-faint), var(--c-surface-3), var(--c-surface), var(--c-surface-3))',
            boxShadow: '0 40px 80px -20px rgba(0,0,0,0.55), inset 0 0 0 2px var(--c-border), inset 0 0 40px rgba(0,0,0,0.35)',
          })} />
          {/* knurled zoom ring */}
          <div className="spin-slow-rev absolute rounded-full" style={ring(90, 18, {
            background: 'repeating-conic-gradient(from 0deg, var(--c-surface-2) 0 3deg, var(--c-bg) 3deg 6deg)',
            boxShadow: 'inset 0 0 0 2px var(--c-border), 0 0 0 1px color-mix(in srgb, var(--c-accent) 30%, transparent)',
          })} />
          <div className="absolute rounded-full" style={ring(76, 34, {
            background: 'radial-gradient(circle at 35% 30%, var(--c-surface-3), var(--c-surface) 60%, var(--c-bg))',
            boxShadow: 'inset 0 0 0 2px var(--c-border), inset 0 6px 24px rgba(0,0,0,0.4)',
          })} />
          {/* accent engraving ring */}
          <div className="absolute rounded-full" style={ring(70, 46, { border: '1.5px solid var(--c-accent)', boxShadow: '0 0 24px color-mix(in srgb, var(--c-accent) 45%, transparent), inset 0 0 18px color-mix(in srgb, var(--c-accent) 25%, transparent)' })} />
          {/* iris */}
          <div className="absolute overflow-hidden rounded-full" style={ring(60, 58, { boxShadow: 'inset 0 0 0 3px var(--c-bg), 0 0 30px rgba(0,0,0,0.5)' })}>
            <Aperture />
          </div>
          {/* front glass with coated reflections */}
          <div className="absolute rounded-full" style={ring(58, 84, {
            background: 'radial-gradient(circle at 30% 25%, rgba(255,255,255,0.38), transparent 28%), radial-gradient(circle at 70% 78%, color-mix(in srgb, var(--c-aurora-3) 40%, transparent), transparent 40%), radial-gradient(circle at 60% 30%, color-mix(in srgb, var(--c-aurora-2) 35%, transparent), transparent 45%)',
            mixBlendMode: 'screen',
          })} />
          <div className="absolute rounded-full" style={ring(20, 90, { background: 'radial-gradient(circle, rgba(255,255,255,0.7), transparent 70%)', left: '58%', top: '24%', filter: 'blur(1px)' })} />

          {/* exposure readouts floating in front */}
          <Chip style={{ left: '-4%', bottom: '18%', transform: 'translateZ(170px)' }}>f/1.4</Chip>
          <Chip style={{ right: '-2%', top: '8%', transform: 'translateZ(200px)' }} delay={1.2}>1/250</Chip>
          <Chip style={{ right: '6%', bottom: '4%', transform: 'translateZ(150px)' }} delay={2.1}>ISO 100</Chip>

          {/* framed prints from the studio's own slides */}
          {frames.map((src, i) => (
            <div key={src + i} className="absolute" style={{ ...framePos[i], transformStyle: 'preserve-3d' }}>
              <div className="float-y rounded-[6px] bg-white p-1.5 shadow-[0_24px_40px_-14px_rgba(0,0,0,0.6)]" style={{ animationDelay: `${i * 0.9}s` }}>
                <img src={src} alt="" loading="lazy" className="aspect-[4/5] w-full rounded-[3px] object-cover" />
              </div>
            </div>
          ))}
        </div>
      </motion.div>
    </div>
  )
}
