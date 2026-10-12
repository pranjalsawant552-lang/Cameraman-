import { useEffect, useState } from 'react'
import { BookOpen, Images, Plus } from 'lucide-react'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { Tabs } from '@/components/ui/Tabs'
import { Pagination } from '@/components/ui/Pagination'
import { Button } from '@/components/ui/Button'
import { ClientCombobox } from '@/components/shared/ClientCombobox'
import { Modal } from '@/components/ui/Modal'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { AlbumPhotosModal } from '@/components/gallery/AlbumPhotosModal'
import { fetchBookings, fetchAlbums, createAlbum, updateAlbumStatus, type Album, type AlbumStatus, type CreateAlbumInput, type PagedResponse } from '@/lib/api'
import { formatCurrency, formatDate } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

const TABS = [
  { id: 'ALL', label: 'All' }, { id: 'PENDING', label: 'Pending' },
  { id: 'DESIGN_IN_PROGRESS', label: 'Designing' }, { id: 'REVIEW', label: 'In review' },
  { id: 'REVISION_REQUESTED', label: 'Revisions' }, { id: 'APPROVED', label: 'Approved' },
  { id: 'PRINTING', label: 'Printing' }, { id: 'DELIVERED', label: 'Delivered' },
]

const STATUS_TONE: Record<AlbumStatus, 'neutral' | 'accent' | 'warning' | 'success' | 'info'> = {
  PENDING: 'neutral', DESIGN_IN_PROGRESS: 'info', REVIEW: 'accent',
  REVISION_REQUESTED: 'warning', APPROVED: 'success', PRINTING: 'accent', DELIVERED: 'success',
}

const NEXT_STATUS: Record<AlbumStatus, AlbumStatus[]> = {
  PENDING: ['DESIGN_IN_PROGRESS'],
  DESIGN_IN_PROGRESS: ['REVIEW'],
  REVIEW: ['REVISION_REQUESTED', 'APPROVED'],
  REVISION_REQUESTED: ['DESIGN_IN_PROGRESS'],
  APPROVED: ['PRINTING'],
  PRINTING: ['DELIVERED'],
  DELIVERED: [],
}

export default function Albums() {
  const push = useToastStore((s) => s.push)
  const [status, setStatus] = useState('ALL')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PagedResponse<Album> | null>(null)
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)
  const [updatingId, setUpdatingId] = useState<string | null>(null)
  const [photosFor, setPhotosFor] = useState<Album | null>(null)

  function reload() {
    fetchAlbums({ status: status === 'ALL' ? '' : status, page, size: 10 }).then((res) => { setData(res); setLoading(false) })
  }
  useEffect(reload, [status, page])
  useEffect(() => { setPage(0) }, [status])

  async function handleStatusChange(album: Album, next: AlbumStatus) {
    setUpdatingId(album.id)
    try {
      await updateAlbumStatus(album.id, next)
      reload()
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not update the album status.')
    } finally {
      setUpdatingId(null)
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <Tabs tabs={TABS} active={status} onChange={setStatus} />
        <Button onClick={() => setShowNew(true)}><Plus className="h-4 w-4" /> New album</Button>
      </div>

      <Card className="overflow-hidden p-0">
        {loading ? (
          <div className="divide-y divide-border">{Array.from({ length: 5 }).map((_, i) => <div key={i} className="p-4"><Skeleton className="h-6 w-full" /></div>)}</div>
        ) : !data || data.content.length === 0 ? (
          <EmptyState icon={<BookOpen className="h-10 w-10" />} title="No albums found" description="Create an album to start the design → review → print workflow for a booking." />
        ) : (
          <>
            <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                  <th scope="col" className="px-6 py-3">Client</th>
                  <th scope="col" className="px-4 py-3">Album</th>
                  <th scope="col" className="px-4 py-3">Pages</th>
                  <th scope="col" className="px-4 py-3">Photos</th>
                  <th scope="col" className="px-4 py-3">Total</th>
                  <th scope="col" className="px-4 py-3">Revisions</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  <th scope="col" className="px-4 py-3">Created</th>
                  <th scope="col" className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.content.map((a) => (
                  <tr key={a.id} className="transition-colors hover:bg-surface-2">
                    <td className="px-6 py-3.5 font-medium text-ink">{a.clientName ?? '—'}</td>
                    <td className="px-4 py-3.5 text-ink-muted">{a.name} <span className="text-ink-faint">· {a.albumType}</span></td>
                    <td className="px-4 py-3.5 text-ink-muted">{a.pageCount || a.includedPages || '—'}</td>
                    <td className="px-4 py-3.5">
                      <Button size="sm" variant="outline" onClick={() => setPhotosFor(a)} aria-label={`Choose photos for ${a.name}`}>
                        <Images className="h-3.5 w-3.5" /> {a.photoCount ?? 0}
                      </Button>
                    </td>
                    <td className="px-4 py-3.5 text-ink">{formatCurrency(a.totalPrice)}</td>
                    <td className="px-4 py-3.5 text-ink-muted">{a.revisionCount}/{a.maxRevisions}</td>
                    <td className="px-4 py-3.5"><Badge tone={STATUS_TONE[a.status]}>{a.status.replace(/_/g, ' ')}</Badge></td>
                    <td className="px-4 py-3.5 text-ink-faint">{formatDate(a.createdAt)}</td>
                    <td className="px-4 py-3.5 text-right">
                      {NEXT_STATUS[a.status].length > 0 && (
                        <Select
                          value=""
                          disabled={updatingId === a.id}
                          onChange={(e) => e.target.value && handleStatusChange(a, e.target.value as AlbumStatus)}
                          className="h-8 w-40 text-xs"
                        >
                          <option value="">Move to…</option>
                          {NEXT_STATUS[a.status].map((s) => <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>)}
                        </Select>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
</div>
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} pageSize={data.size} onChange={setPage} />
          </>
        )}
      </Card>

      {photosFor && <AlbumPhotosModal album={photosFor} open onClose={() => setPhotosFor(null)} onSaved={reload} />}
      <NewAlbumModal open={showNew} onClose={() => setShowNew(false)} onCreated={reload} />
    </div>
  )
}

function NewAlbumModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const push = useToastStore((s) => s.push)
  const [form, setForm] = useState<CreateAlbumInput>({ name: '', albumType: 'SIZE_12X18', clientName: '', bookingId: '', pageCount: 20, includedPages: 20, basePrice: 0 })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [bookingOptions, setBookingOptions] = useState<{ id: string; label: string }[]>([])

  function reset() { setForm({ name: '', albumType: 'SIZE_12X18', clientName: '', bookingId: '', pageCount: 20, includedPages: 20, basePrice: 0 }); setError(null); setBookingOptions([]) }

  // Offer this client's bookings in a dropdown instead of asking staff to type a booking UUID.
  useEffect(() => {
    const q = (form.clientName ?? '').trim()
    if (!open || q.length < 2) { setBookingOptions([]); return }
    let cancelled = false
    const t = window.setTimeout(() => {
      fetchBookings({ search: q, size: 20 })
        .then((res) => { if (!cancelled) setBookingOptions(res.content.map((b) => ({ id: b.id, label: `${b.bookingNumber} · ${b.bookingDate}${b.packageName ? ` · ${b.packageName}` : ''}` }))) })
        .catch(() => { if (!cancelled) setBookingOptions([]) })
    }, 300)
    return () => { cancelled = true; window.clearTimeout(t) }
  }, [open, form.clientName])

  async function handleSubmit() {
    setError(null)
    if (!form.name.trim()) { setError('Album name is required.'); return }
    if (!form.clientName?.trim()) { setError('Client name is required.'); return }
    setSubmitting(true)
    try {
      // Blank optional ids must be omitted: '' violates the albums.booking_id / client_id foreign keys.
      await createAlbum({ ...form, bookingId: form.bookingId || undefined, clientId: form.clientId || undefined, clientName: form.clientName?.trim() })
      push('Album created')
      onCreated()
      reset()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the album. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={() => { onClose(); reset() }} title="New album" footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Creating…' : 'Create'}</Button></>}>
      <div className="space-y-4">
        <Input label="Album name" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} placeholder="e.g. Sharma Wedding Album" />
        <div className="grid grid-cols-2 gap-4">
          <ClientCombobox value={form.clientName ?? ''} onChange={(t) => setForm((f) => ({ ...f, clientName: t, clientId: undefined }))} onSelect={(c) => setForm((f) => ({ ...f, clientId: c.id }))} />
          <Select label="Booking (optional)" value={form.bookingId ?? ''} onChange={(e) => setForm((f) => ({ ...f, bookingId: e.target.value }))}>
            <option value="">No booking linked</option>
            {bookingOptions.map((b) => <option key={b.id} value={b.id}>{b.label}</option>)}
          </Select>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Select label="Album type" value={form.albumType} onChange={(e) => setForm((f) => ({ ...f, albumType: e.target.value }))}>
            <option value="SIZE_12X18">12 × 18 in</option>
            <option value="SIZE_18X24">18 × 24 in</option>
            <option value="SIZE_24X30">24 × 30 in</option>
          </Select>
          <Select label="Cover" value={form.coverType ?? ''} onChange={(e) => setForm((f) => ({ ...f, coverType: e.target.value || undefined }))}>
            <option value="">Not decided</option>
            <option value="LEATHERETTE">Leatherette</option>
            <option value="FABRIC">Fabric</option>
            <option value="ACRYLIC">Acrylic</option>
            <option value="HARDBOARD">Hardboard</option>
            <option value="CANVAS">Canvas</option>
          </Select>
        </div>
        <div className="grid grid-cols-3 gap-4">
          <Input label="Page count" type="number" min="1" value={form.pageCount} onChange={(e) => setForm((f) => ({ ...f, pageCount: Number(e.target.value) }))} />
          <Input label="Base price (₹)" type="number" min="0" value={form.basePrice} onChange={(e) => setForm((f) => ({ ...f, basePrice: Number(e.target.value) }))} />
          <Input label="Price/extra page" type="number" min="0" value={form.pricePerExtraPage ?? ''} onChange={(e) => setForm((f) => ({ ...f, pricePerExtraPage: Number(e.target.value) }))} />
        </div>
        <Input label="Material (optional)" value={form.material ?? ''} onChange={(e) => setForm((f) => ({ ...f, material: e.target.value }))} placeholder="e.g. Matte lamination" />
        <Textarea label="Notes" value={form.notes ?? ''} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))} rows={2} />
        {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
      </div>
    </Modal>
  )
}
