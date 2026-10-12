import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Heart, Check, Lock, Unlock } from 'lucide-react'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { Tabs } from '@/components/ui/Tabs'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import {
  approveSelection, fetchAllSelections, fetchGalleryPhotos, fetchSelectedPhotos, lockSelectionByStaff, reopenSelection,
  type GalleryPhoto, type PhotoSelectionInfo, type SelectionStatus,
} from '@/lib/api'
import { ApiError } from '@/lib/apiClient'
import { useToastStore } from '@/store/useToastStore'
import { formatDate } from '@/lib/utils'

const TONE: Record<SelectionStatus, string> = { OPEN: 'warning', LOCKED: 'info', APPROVED: 'success', EXPIRED: 'neutral' }
const TABS: { id: SelectionStatus | 'ALL'; label: string }[] = [
  { id: 'ALL', label: 'All' }, { id: 'OPEN', label: 'Open' }, { id: 'LOCKED', label: 'To approve' }, { id: 'APPROVED', label: 'Approved' },
]
const PAGE_SIZE = 20

export default function Selections() {
  const push = useToastStore((s) => s.push)
  const [status, setStatus] = useState<SelectionStatus | 'ALL'>('LOCKED')
  const [page, setPage] = useState(0)
  const [rows, setRows] = useState<PhotoSelectionInfo[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [open, setOpen] = useState<PhotoSelectionInfo | null>(null)
  const [photos, setPhotos] = useState<GalleryPhoto[]>([])
  const [detailLoading, setDetailLoading] = useState(false)
  const [acting, setActing] = useState(false)

  const load = useCallback(() => {
    setLoading(true); setError(null)
    fetchAllSelections({ status: status === 'ALL' ? undefined : status, page, size: PAGE_SIZE })
      .then((r) => { setRows(r.content); setTotal(r.totalElements); setTotalPages(r.totalPages) })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load selections.'))
      .finally(() => setLoading(false))
  }, [status, page])
  useEffect(load, [load])

  async function openDetail(sel: PhotoSelectionInfo) {
    setOpen(sel); setDetailLoading(true); setPhotos([])
    try {
      const [chosen, all] = await Promise.all([fetchSelectedPhotos(sel.id), fetchGalleryPhotos(sel.galleryId)])
      const ids = new Set(chosen.map((c) => c.photoId))
      setPhotos(all.filter((p) => ids.has(p.id)))
    } catch (e) {
      push(e instanceof ApiError ? e.message : 'Could not load the selected photos.')
    } finally { setDetailLoading(false) }
  }

  async function act(fn: () => Promise<PhotoSelectionInfo>, done: string) {
    setActing(true)
    try {
      const updated = await fn()
      setOpen(updated); push(done); load()
    } catch (e) {
      push(e instanceof ApiError ? e.message : 'That action failed.')
    } finally { setActing(false) }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div><h1 className="font-display text-2xl text-ink">Photo selections</h1><p className="text-sm text-ink-muted">Clients' chosen photos. Approve a submitted selection to start album design.</p></div>
        <Tabs tabs={TABS} active={status} onChange={(s) => { setStatus(s); setPage(0) }} />
      </div>

      <Card className="overflow-hidden p-0">
        {loading ? (
          <div className="space-y-3 p-6">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-12" />)}</div>
        ) : error ? (
          <EmptyState icon={<Heart className="h-10 w-10" />} title="Couldn't load selections" description={error} action={<Button onClick={load}>Try again</Button>} />
        ) : rows.length === 0 ? (
          <EmptyState icon={<Heart className="h-10 w-10" />} title="Nothing here" description="Create a selection from a gallery's page to invite a client to pick photos." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead><tr className="border-b border-border text-left text-xs uppercase tracking-wide text-ink-faint">
                <th scope="col" className="px-4 py-3">Client</th><th scope="col" className="px-4 py-3">Picked</th><th scope="col" className="px-4 py-3">Status</th><th scope="col" className="px-4 py-3">Submitted</th><th scope="col" className="px-4 py-3" />
              </tr></thead>
              <tbody>
                {rows.map((s) => (
                  <tr key={s.id} className="border-b border-border last:border-0 hover:bg-surface-2/50">
                    <td className="px-4 py-3 text-ink">{s.clientName ?? '—'}</td>
                    <td className="px-4 py-3 text-ink-muted">{s.selectedCount}{s.selectionLimit != null ? ` / ${s.selectionLimit}` : ''}</td>
                    <td className="px-4 py-3"><Badge tone={TONE[s.status] as any}>{s.status}</Badge></td>
                    <td className="px-4 py-3 text-ink-muted">{s.lockedAt ? formatDate(s.lockedAt) : '—'}</td>
                    <td className="px-4 py-3 text-right"><Button size="sm" variant="outline" onClick={() => openDetail(s)}>Review</Button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pagination page={page} totalPages={totalPages} totalElements={total} pageSize={PAGE_SIZE} onChange={setPage} />
      </Card>

      <Modal open={!!open} onClose={() => setOpen(null)} title={open ? `Selection: ${open.clientName ?? 'client'}` : ''} size="lg"
        footer={open && (
          <>
            {open.status === 'OPEN' && <Button variant="outline" loading={acting} disabled={open.selectedCount === 0} onClick={() => act(() => lockSelectionByStaff(open.id), 'Selection locked')}><Lock className="h-4 w-4" /> Lock for client</Button>}
            {open.status === 'LOCKED' && <Button variant="outline" loading={acting} onClick={() => act(() => reopenSelection(open.id), 'Selection re-opened')}><Unlock className="h-4 w-4" /> Re-open</Button>}
            {open.status === 'LOCKED' && <Button loading={acting} onClick={() => act(() => approveSelection(open.id), 'Approved. Booking moved to album design and the client was notified')}><Check className="h-4 w-4" /> Approve</Button>}
          </>
        )}>
        {open && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-2 text-sm text-ink-muted">
              <Badge tone={TONE[open.status] as any}>{open.status}</Badge>
              <span>{open.selectedCount} photos</span>
              {open.bookingId && <Link to={`/admin/bookings/${open.bookingId}`} className="text-accent hover:underline">Open booking</Link>}
              <Link to={`/admin/galleries/${open.galleryId}`} className="text-accent hover:underline">Open gallery</Link>
              <Link to="/admin/albums" className="text-accent hover:underline">Albums</Link>
            </div>
            {open.clientNotes && <p className="rounded-[var(--radius-md)] bg-surface-2 p-3 text-sm text-ink"><span className="text-ink-muted">Client notes: </span>{open.clientNotes}</p>}
            {detailLoading ? <Skeleton className="h-40" /> : photos.length === 0 ? <p className="text-sm text-ink-muted">No photos selected yet.</p> : (
              <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
                {photos.map((p) => (
                  <div key={p.id} className="relative aspect-[4/5] overflow-hidden rounded-[var(--radius-sm)] bg-surface-2">
                    <img src={p.url} alt={p.originalFilename} loading="lazy" className="h-full w-full object-cover" />
                    <span className="absolute left-1 top-1 rounded bg-black/60 px-1 text-[10px] text-white">#{p.displayNumber}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}
