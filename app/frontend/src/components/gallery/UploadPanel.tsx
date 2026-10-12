import { CheckCircle2, Loader2, RotateCcw, X, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { formatBytes, type UploadItem } from '@/lib/galleryUpload'

const STATE_LABEL: Record<UploadItem['state'], string> = {
  queued: 'Waiting', uploading: 'Uploading', confirming: 'Finishing', done: 'Uploaded', error: 'Failed', cancelled: 'Cancelled',
}

/** Per-file progress for the direct-to-storage upload queue. */
export function UploadPanel({
  items, active, onCancel, onRetryFailed, onClear,
}: {
  items: UploadItem[]
  active: boolean
  onCancel: () => void
  onRetryFailed: () => void
  onClear: () => void
}) {
  if (items.length === 0) return null
  const done = items.filter((i) => i.state === 'done').length
  const failed = items.filter((i) => i.state === 'error').length
  const totalPct = Math.round(items.reduce((sum, i) => sum + (i.state === 'done' ? 100 : i.progress), 0) / items.length)

  return (
    <section aria-label="Upload progress" className="rounded-[var(--radius-md)] border border-border bg-surface p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-sm font-medium text-ink">
            {active ? 'Uploading' : 'Upload finished'}: {done} of {items.length} photos{failed > 0 ? `, ${failed} failed` : ''}
          </p>
          <div
            role="progressbar" aria-label="Overall upload progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={totalPct}
            className="mt-2 h-2 w-64 max-w-full overflow-hidden rounded-full bg-surface-3"
          >
            <div className="h-full bg-accent transition-all" style={{ width: `${totalPct}%` }} />
          </div>
        </div>
        <div className="flex gap-2">
          {active && <Button size="sm" variant="outline" onClick={onCancel}>Cancel remaining</Button>}
          {!active && failed > 0 && <Button size="sm" variant="secondary" onClick={onRetryFailed}><RotateCcw className="h-3.5 w-3.5" /> Retry failed</Button>}
          {!active && <Button size="sm" variant="ghost" onClick={onClear}>Dismiss</Button>}
        </div>
      </div>

      <ul className="mt-3 max-h-56 space-y-1.5 overflow-y-auto pr-1" aria-live="polite">
        {items.map((i) => (
          <li key={i.id} className="flex items-center gap-3 text-xs">
            <span className="w-4 shrink-0" aria-hidden="true">
              {i.state === 'done' ? <CheckCircle2 className="h-4 w-4 text-success" />
                : i.state === 'error' ? <XCircle className="h-4 w-4 text-danger" />
                : i.state === 'cancelled' ? <X className="h-4 w-4 text-ink-faint" />
                : i.state === 'queued' ? null
                : <Loader2 className="h-4 w-4 animate-spin text-accent" />}
            </span>
            <span className="min-w-0 flex-1 truncate text-ink" title={i.file.name}>{i.file.name}</span>
            <span className="hidden shrink-0 text-ink-faint sm:inline">{formatBytes(i.file.size)}</span>
            <span className="w-40 shrink-0 truncate text-right text-ink-muted" title={i.error ?? i.note}>
              {i.state === 'error' ? (i.error ?? 'Failed')
                : i.note && i.state !== 'queued' ? i.note
                : i.state === 'uploading' ? `${i.progress}%` : STATE_LABEL[i.state]}
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}
