import { ArrowRight, Check, Undo2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { OrderStageStepper } from '@/components/ui/Stepper'
import { ORDER_STAGES, STAGE_LABEL } from '@/lib/statusTone'
import type { OrderStage } from '@/lib/types'

const STAGES = ORDER_STAGES as readonly OrderStage[]

/** The stage that follows `stage`, or null once the order is delivered. */
export const nextStage = (stage: OrderStage): OrderStage | null => STAGES[STAGES.indexOf(stage) + 1] ?? null
export const prevStage = (stage: OrderStage): OrderStage | null => STAGES[STAGES.indexOf(stage) - 1] ?? null

interface Props {
  stage: OrderStage
  disabled?: boolean
  busy?: boolean
  onChange: (stage: OrderStage) => void
}

/**
 * One-tap status updates. The common action (move to the next step) is a single big button; going back
 * one step is an undo link; jumping to an arbitrary step is tucked away so it never gets in the way.
 */
export function BookingStatusPanel({ stage, disabled, busy, onChange }: Props) {
  const next = nextStage(stage)
  const prev = prevStage(stage)
  return (
    <div className="space-y-4">
      <OrderStageStepper current={stage} />
      <div className="flex flex-wrap items-center gap-3">
        <div className="text-sm">
          <span className="text-ink-muted">Now: </span>
          <span className="font-semibold text-ink">{STAGE_LABEL[stage]}</span>
        </div>
        <div className="ml-auto flex flex-wrap items-center gap-2">
          {prev && (
            <Button variant="ghost" size="sm" disabled={disabled || busy} onClick={() => onChange(prev)} aria-label={`Undo: go back to ${STAGE_LABEL[prev]}`}>
              <Undo2 className="h-4 w-4" /> Back
            </Button>
          )}
          {next ? (
            <Button disabled={disabled} loading={busy} onClick={() => onChange(next)}>
              Mark “{STAGE_LABEL[next]}” <ArrowRight className="h-4 w-4" />
            </Button>
          ) : (
            <span className="inline-flex items-center gap-1.5 text-sm font-medium text-success"><Check className="h-4 w-4" /> Order complete</span>
          )}
        </div>
      </div>
      <details className="text-sm">
        <summary className="cursor-pointer text-ink-muted hover:text-ink">Jump to a different step</summary>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {STAGES.map((s) => (
            <button key={s} type="button" disabled={disabled || busy || s === stage} onClick={() => onChange(s)}
              className={`rounded-full border px-2.5 py-1 text-xs ${s === stage ? 'border-accent bg-accent/10 text-accent' : 'border-border text-ink-muted hover:bg-surface-2'} disabled:cursor-not-allowed`}>
              {STAGE_LABEL[s]}
            </button>
          ))}
        </div>
      </details>
    </div>
  )
}
