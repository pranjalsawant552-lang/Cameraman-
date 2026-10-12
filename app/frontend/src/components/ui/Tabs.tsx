import { motion } from 'framer-motion'
import { cn } from '@/lib/utils'

export function Tabs<T extends string>({
  tabs, active, onChange,
}: {
  tabs: { id: T; label: string; count?: number }[]
  active: T
  onChange: (id: T) => void
}) {
  return (
    <div className="flex items-center gap-1 rounded-[var(--radius-md)] bg-surface-2 p-1">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          onClick={() => onChange(tab.id)}
          className={cn(
            'relative flex items-center gap-1.5 rounded-[calc(var(--radius-md)-4px)] px-3.5 py-1.5 text-sm font-medium transition-colors duration-150',
            active === tab.id ? 'text-accent-ink' : 'text-ink-muted hover:text-ink'
          )}
        >
          {active === tab.id && (
            <motion.span
              layoutId="tab-pill"
              className="absolute inset-0 rounded-[calc(var(--radius-md)-4px)] bg-accent"
              transition={{ type: 'spring', stiffness: 500, damping: 35 }}
            />
          )}
          <span className="relative z-10">{tab.label}</span>
          {tab.count !== undefined && (
            <span className={cn('relative z-10 rounded-full px-1.5 text-xs', active === tab.id ? 'bg-accent-ink/15' : 'bg-surface-3')}>
              {tab.count}
            </span>
          )}
        </button>
      ))}
    </div>
  )
}
