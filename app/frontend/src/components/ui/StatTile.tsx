import { useEffect, useRef, useState } from 'react'
import { animate } from 'framer-motion'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

export function useCountUp(value: number, duration = 0.9) {
  const [display, setDisplay] = useState(0)
  const prev = useRef(0)
  useEffect(() => {
    const controls = animate(prev.current, value, {
      duration,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (v) => setDisplay(v),
    })
    prev.current = value
    return () => controls.stop()
  }, [value, duration])
  return display
}

export function StatTile({
  icon: Icon, label, value, format, trend, tone = 'accent',
}: {
  icon: LucideIcon
  label: string
  value: number
  format?: (v: number) => string
  trend?: { direction: 'up' | 'down'; label: string }
  tone?: 'accent' | 'success' | 'warning' | 'danger'
}) {
  const animated = useCountUp(value)
  const display = format ? format(animated) : Math.round(animated).toLocaleString('en-IN')

  const toneClass = {
    accent: 'bg-accent/12 text-accent',
    success: 'bg-success-bg text-success',
    warning: 'bg-warning-bg text-warning',
    danger: 'bg-danger-bg text-danger',
  }[tone]

  return (
    <div className="rounded-[var(--radius-lg)] border border-border bg-surface p-5 shadow-[var(--shadow-card)] transition-transform duration-200 hover:-translate-y-0.5">
      <div className="flex items-start justify-between">
        <div className={cn('flex h-10 w-10 items-center justify-center rounded-[var(--radius-md)]', toneClass)}>
          <Icon className="h-5 w-5" />
        </div>
        {trend && (
          <span className={cn('text-xs font-medium', trend.direction === 'up' ? 'text-success' : 'text-danger')}>
            {trend.direction === 'up' ? '↑' : '↓'} {trend.label}
          </span>
        )}
      </div>
      <p className="mt-4 font-display text-3xl text-ink">{display}</p>
      <p className="mt-1 text-sm text-ink-muted">{label}</p>
    </div>
  )
}
