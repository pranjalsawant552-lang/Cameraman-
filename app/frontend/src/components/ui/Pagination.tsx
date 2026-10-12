import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Pagination({
  page, totalPages, totalElements, pageSize, onChange,
}: {
  page: number
  totalPages: number
  totalElements: number
  pageSize: number
  onChange: (page: number) => void
}) {
  if (totalElements === 0) return null
  const start = page * pageSize + 1
  const end = Math.min(totalElements, (page + 1) * pageSize)

  return (
    <div className="flex items-center justify-between border-t border-border px-6 py-3.5">
      <p className="text-sm text-ink-muted">
        Showing <span className="text-ink font-medium">{start}–{end}</span> of{' '}
        <span className="text-ink font-medium">{totalElements}</span>
      </p>
      <div className="flex items-center gap-1">
        <button
          onClick={() => onChange(page - 1)}
          disabled={page === 0}
          className="flex h-8 w-8 items-center justify-center rounded-[var(--radius-sm)] text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink disabled:opacity-30 disabled:pointer-events-none"
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
        {Array.from({ length: totalPages }, (_, i) => i)
          .filter((i) => i === 0 || i === totalPages - 1 || Math.abs(i - page) <= 1)
          .map((i, idx, arr) => (
            <span key={i} className="flex items-center">
              {idx > 0 && arr[idx - 1] !== i - 1 && <span className="px-1 text-ink-faint">···</span>}
              <button
                onClick={() => onChange(i)}
                className={cn(
                  'flex h-8 w-8 items-center justify-center rounded-[var(--radius-sm)] text-sm font-medium transition-colors',
                  i === page ? 'bg-accent text-accent-ink' : 'text-ink-muted hover:bg-surface-2 hover:text-ink'
                )}
              >
                {i + 1}
              </button>
            </span>
          ))}
        <button
          onClick={() => onChange(page + 1)}
          disabled={page >= totalPages - 1}
          className="flex h-8 w-8 items-center justify-center rounded-[var(--radius-sm)] text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink disabled:opacity-30 disabled:pointer-events-none"
        >
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  )
}
