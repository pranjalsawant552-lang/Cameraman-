import { useEffect, useState } from 'react'
import { History } from 'lucide-react'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { SearchInput } from '@/components/ui/Input'
import { Pagination } from '@/components/ui/Pagination'
import { fetchAuditLogs, type AuditLogEntry, type PagedResponse } from '@/lib/api'
import { formatDate } from '@/lib/utils'

export default function AuditLogs() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PagedResponse<AuditLogEntry> | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    fetchAuditLogs({ search, page, size: 20 }).then((res) => { setData(res); setLoading(false) })
  }, [search, page])
  useEffect(() => { setPage(0) }, [search])

  return (
    <div className="space-y-5">
      <p className="max-w-2xl text-sm text-ink-muted">
        A read-only history of sensitive actions — invoice edits/cancellations, price changes, stock adjustments,
        permission changes — for accountability and troubleshooting.
      </p>
      <div className="w-full max-w-xs"><SearchInput value={search} onChange={setSearch} placeholder="Search by action, entity, or staff…" /></div>

      {loading ? (
        <div className="space-y-2">{Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-12 rounded-[var(--radius-md)]" />)}</div>
      ) : !data || data.content.length === 0 ? (
        <Card><EmptyState icon={<History className="h-10 w-10" />} title="No audit log entries" description="Actions recorded by the system will show up here." /></Card>
      ) : (
        <>
          <Card className="overflow-hidden p-0">
            <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                  <th scope="col" className="px-6 py-3">Action</th>
                  <th scope="col" className="px-4 py-3">Entity</th>
                  <th scope="col" className="px-4 py-3">Performed by</th>
                  <th scope="col" className="px-4 py-3">Details</th>
                  <th scope="col" className="px-4 py-3">When</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.content.map((log) => (
                  <tr key={log.id} className="transition-colors hover:bg-surface-2">
                    <td className="px-6 py-3.5"><Badge tone="neutral">{log.action}</Badge></td>
                    <td className="px-4 py-3.5 text-ink-muted">{log.entityType} <span className="text-ink-faint">#{log.entityId.slice(0, 8)}</span></td>
                    <td className="px-4 py-3.5 text-ink">{log.performedByName}</td>
                    <td className="max-w-xs truncate px-4 py-3.5 text-ink-muted">{log.details ?? '—'}</td>
                    <td className="px-4 py-3.5 text-ink-faint">{formatDate(log.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
</div>
          </Card>
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} pageSize={data.size} onChange={setPage} />
        </>
      )}
    </div>
  )
}
