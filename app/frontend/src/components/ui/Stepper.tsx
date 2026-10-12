import { Check } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { OrderStage } from '@/lib/types'

import { ORDER_STAGES, STAGE_LABEL } from '@/lib/statusTone'

const STAGES: { id: OrderStage; label: string }[] = ORDER_STAGES.map((id) => ({ id, label: STAGE_LABEL[id] }))

export function OrderStageStepper({ current }: { current: OrderStage }) {
  const currentIdx = STAGES.findIndex((s) => s.id === current)
  return (
    <div className="-mx-1 overflow-x-auto px-1 pb-2">
    <div className="flex min-w-[820px] items-center">
      {STAGES.map((stage, i) => {
        const done = i < currentIdx
        const active = i === currentIdx
        return (
          <div key={stage.id} className="flex flex-1 items-center last:flex-none">
            <div className="flex flex-col items-center gap-2">
              <div
                className={cn(
                  'flex h-8 w-8 items-center justify-center rounded-full border-2 text-xs font-semibold transition-colors duration-300',
                  done && 'border-accent bg-accent text-accent-ink',
                  active && 'border-accent bg-accent-bg text-accent',
                  !done && !active && 'border-border bg-surface text-ink-faint'
                )}
                style={active ? { backgroundColor: 'color-mix(in srgb, var(--c-accent) 15%, transparent)' } : undefined}
              >
                {done ? <Check className="h-4 w-4" /> : i + 1}
              </div>
              <span className={cn('whitespace-nowrap text-xs font-medium', active ? 'text-ink' : 'text-ink-faint')}>{stage.label}</span>
            </div>
            {i < STAGES.length - 1 && (
              <div className="mx-2 h-0.5 flex-1 rounded-full bg-border overflow-hidden -mt-5">
                <div
                  className="h-full bg-accent transition-all duration-500 ease-out"
                  style={{ width: i < currentIdx ? '100%' : '0%' }}
                />
              </div>
            )}
          </div>
        )
      })}
    </div>
    </div>
  )
}

/** Generic numbered wizard progress bar (used by the client booking flow). */
export function WizardSteps({ steps, current }: { steps: string[]; current: number }) {
  return (
    <ol className="flex items-center gap-2 overflow-x-auto pb-1" aria-label="Progress">
      {steps.map((label, i) => {
        const done = i < current
        const active = i === current
        return (
          <li key={label} className="flex shrink-0 items-center gap-2" aria-current={active ? 'step' : undefined}>
            <span
              className={cn(
                'flex h-7 w-7 items-center justify-center rounded-full border text-xs font-semibold',
                done && 'border-accent bg-accent text-accent-ink',
                active && 'border-accent text-accent',
                !done && !active && 'border-border text-ink-faint',
              )}
            >
              {done ? <Check className="h-3.5 w-3.5" /> : i + 1}
            </span>
            <span className={cn('text-sm', active ? 'font-medium text-ink' : 'text-ink-faint')}>{label}</span>
            {i < steps.length - 1 && <span className="h-px w-5 bg-border" />}
          </li>
        )
      })}
    </ol>
  )
}
