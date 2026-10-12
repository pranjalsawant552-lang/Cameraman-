import { useEffect, useRef } from 'react'
import { cn } from '@/lib/utils'

interface Orb { x: number; y: number; r: number; vx: number; vy: number; a: number; c: number; disc: boolean; depth: number }

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.trim().replace('#', '')
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h
  const n = parseInt(full, 16)
  return Number.isNaN(n) ? [200, 160, 100] : [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

function readPalette(): [number, number, number][] {
  const cs = getComputedStyle(document.documentElement)
  return ['--c-accent', '--c-aurora-1', '--c-aurora-2', '--c-aurora-3'].map((v) => hexToRgb(cs.getPropertyValue(v) || '#c6a15b'))
}

/**
 * Drifting out-of-focus light discs — the look of lens bokeh — drawn on a canvas in the
 * current theme's colours. Reacts to the pointer with depth-based parallax, pauses when
 * off-screen or the tab is hidden, and renders a single still frame for reduced-motion.
 */
export function Bokeh({ count = 24, className }: { count?: number; className?: string }) {
  const ref = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = ref.current
    const ctx = canvas?.getContext('2d')
    if (!canvas || !ctx) return
    const cnv = canvas
    const g = ctx

    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const dpr = Math.min(window.devicePixelRatio || 1, 1.5)
    let w = 0, h = 0, raf = 0, visible = true
    let palette = readPalette()
    const pointer = { x: 0.5, y: 0.5, sx: 0.5, sy: 0.5 }
    const light = document.documentElement.getAttribute('data-theme') === 'daylight' || document.documentElement.getAttribute('data-theme') === 'editorial'

    const orbs: Orb[] = Array.from({ length: count }, () => {
      const depth = Math.random()
      return {
        x: Math.random(), y: Math.random(),
        r: 14 + depth * 70 + Math.random() * 18,
        vx: (Math.random() - 0.5) * 0.00006,
        vy: -(0.00004 + Math.random() * 0.00012),
        a: (light ? 0.10 : 0.07) + Math.random() * (light ? 0.16 : 0.2),
        c: Math.floor(Math.random() * 4),
        disc: Math.random() > 0.55,
        depth,
      }
    })

    function resize() {
      const r = cnv.getBoundingClientRect()
      w = r.width; h = r.height
      cnv.width = Math.max(1, Math.floor(w * dpr))
      cnv.height = Math.max(1, Math.floor(h * dpr))
      g.setTransform(dpr, 0, 0, dpr, 0, 0)
      if (reduce) draw(0)
    }

    function draw(dt: number) {
      g.clearRect(0, 0, w, h)
      pointer.sx += (pointer.x - pointer.sx) * 0.05
      pointer.sy += (pointer.y - pointer.sy) * 0.05
      for (const o of orbs) {
        o.x += o.vx * dt; o.y += o.vy * dt
        if (o.y < -0.15) { o.y = 1.15; o.x = Math.random() }
        if (o.x < -0.1) o.x = 1.1
        if (o.x > 1.1) o.x = -0.1
        const px = o.x * w + (pointer.sx - 0.5) * -60 * o.depth
        const py = o.y * h + (pointer.sy - 0.5) * -40 * o.depth
        const [r, gg, b] = palette[o.c]
        if (o.disc) {
          g.beginPath(); g.arc(px, py, o.r, 0, Math.PI * 2)
          g.fillStyle = `rgba(${r},${gg},${b},${o.a * 0.55})`; g.fill()
          g.lineWidth = 1.2; g.strokeStyle = `rgba(${r},${gg},${b},${Math.min(o.a * 1.8, 0.5)})`; g.stroke()
        } else {
          const grad = g.createRadialGradient(px, py, 0, px, py, o.r)
          grad.addColorStop(0, `rgba(${r},${gg},${b},${o.a})`)
          grad.addColorStop(0.55, `rgba(${r},${gg},${b},${o.a * 0.4})`)
          grad.addColorStop(1, `rgba(${r},${gg},${b},0)`)
          g.fillStyle = grad; g.beginPath(); g.arc(px, py, o.r, 0, Math.PI * 2); g.fill()
        }
      }
    }

    let last = performance.now()
    function frame(now: number) {
      const dt = Math.min(now - last, 64)
      last = now
      if (visible && !document.hidden) draw(dt)
      raf = requestAnimationFrame(frame)
    }

    const ro = new ResizeObserver(resize)
    ro.observe(cnv)
    resize()

    const io = new IntersectionObserver(([e]) => { visible = e.isIntersecting })
    io.observe(cnv)

    const mo = new MutationObserver(() => { palette = readPalette(); if (reduce) draw(0) })
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })

    function onMove(e: PointerEvent) { pointer.x = e.clientX / window.innerWidth; pointer.y = e.clientY / window.innerHeight }
    if (!reduce) {
      window.addEventListener('pointermove', onMove, { passive: true })
      raf = requestAnimationFrame(frame)
    }

    return () => {
      cancelAnimationFrame(raf)
      ro.disconnect(); io.disconnect(); mo.disconnect()
      window.removeEventListener('pointermove', onMove)
    }
  }, [count])

  return <canvas ref={ref} aria-hidden className={cn('pointer-events-none absolute inset-0 h-full w-full', className)} />
}
