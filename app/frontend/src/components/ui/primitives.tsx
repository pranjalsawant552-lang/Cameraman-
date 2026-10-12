import { type HTMLAttributes, type ReactNode } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn, initials } from '@/lib/utils'

// ---------------- Card ----------------
export function Card({ className, children, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'bg-surface border border-border rounded-[var(--radius-lg)] shadow-[var(--shadow-card)]',
        className
      )}
      {...props}
    >
      {children}
    </div>
  )
}

export function CardHeader({ className, children, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn('flex items-center justify-between gap-3 px-6 pt-6', className)} {...props}>
      {children}
    </div>
  )
}

export function CardTitle({ className, children, ...props }: HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3 className={cn('font-display text-lg font-medium text-ink', className)} {...props}>
      {children}
    </h3>
  )
}

export function CardContent({ className, children, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn('p-6', className)} {...props}>
      {children}
    </div>
  )
}

// ---------------- Badge ----------------
const badgeVariants = cva('inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium', {
  variants: {
    tone: {
      neutral: 'bg-surface-2 text-ink-muted',
      accent: 'bg-accent/15 text-accent',
      success: 'bg-success-bg text-success',
      warning: 'bg-warning-bg text-warning',
      danger: 'bg-danger-bg text-danger',
      info: 'bg-info-bg text-info',
    },
  },
  defaultVariants: { tone: 'neutral' },
})

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement>, VariantProps<typeof badgeVariants> {
  dot?: boolean
}

export function Badge({ className, tone, dot, children, ...props }: BadgeProps) {
  return (
    <span className={cn(badgeVariants({ tone }), className)} {...props}>
      {dot && <span className="h-1.5 w-1.5 rounded-full bg-current" />}
      {children}
    </span>
  )
}

// ---------------- Avatar ----------------
export function Avatar({ src, name, size = 40, className }: { src?: string; name: string; size?: number; className?: string }) {
  return (
    <div
      className={cn('relative shrink-0 overflow-hidden rounded-full bg-surface-3 flex items-center justify-center border border-border', className)}
      style={{ width: size, height: size }}
    >
      {src ? (
        <img src={src} alt={name} className="h-full w-full object-cover" />
      ) : (
        <span className="font-display text-ink-muted" style={{ fontSize: size * 0.38 }}>{initials(name)}</span>
      )}
    </div>
  )
}

// ---------------- Switch ----------------
export function Switch({ checked, onChange, disabled, label }: { checked: boolean; onChange: (v: boolean) => void; disabled?: boolean; label?: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors duration-200 disabled:opacity-50',
        checked ? 'bg-accent' : 'bg-surface-3'
      )}
    >
      <span
        className={cn(
          'inline-block h-4.5 w-4.5 transform rounded-full bg-white shadow transition-transform duration-200 ease-out',
          checked ? 'translate-x-6' : 'translate-x-1'
        )}
        style={{ height: 18, width: 18 }}
      />
    </button>
  )
}

// ---------------- Skeleton ----------------
export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-[var(--radius-sm)] bg-surface-2', className)} />
}

// ---------------- Empty state ----------------
export function EmptyState({ icon, title, description, action }: { icon?: ReactNode; title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16 text-center">
      {icon && <div className="text-ink-faint">{icon}</div>}
      <div>
        <p className="font-display text-lg text-ink">{title}</p>
        {description && <p className="mt-1 max-w-sm text-sm text-ink-muted">{description}</p>}
      </div>
      {action}
    </div>
  )
}

// ---------------- Divider ----------------
export function Divider({ className }: { className?: string }) {
  return <div className={cn('h-px w-full bg-border', className)} />
}
