import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { BellRing, CalendarX2, CheckCircle2, PackageCheck, Plus } from 'lucide-react'
import { Card } from '@/components/ui/primitives'
import { Badge, Avatar, EmptyState, Skeleton } from '@/components/ui/primitives'
import { SearchInput } from '@/components/ui/Input'
import { Tabs } from '@/components/ui/Tabs'
import { Pagination } from '@/components/ui/Pagination'
import { Button } from '@/components/ui/Button'
import { fetchBookings, updateBookingStage, markBookingPickedUp, markBookingReadyForPickup, sendPickupReminder, type PagedResponse } from '@/lib/api'
import { ApiError } from '@/lib/apiClient'
import { useToastStore } from '@/store/useToastStore'
import type { Booking, BookingStatus } from '@/lib/types'
import { formatCurrency, formatDate } from '@/lib/utils'
import { BOOKING_STATUS_TONE, CATEGORY_TONE, STAGE_LABEL } from '@/lib/statusTone'
import { nextStage } from '@/components/booking/BookingStatusPanel'
import { ArrowRight } from 'lucide-react'
import { NewBookingModal } from '@/components/booking/NewBookingModal'

const STATUS_TABS: { id: BookingStatus | 'ALL'; label: string }[] = [
  { id: 'ALL', label: 'All' }, { id: 'PENDING', label: 'Pending' }, { id: 'CONFIRMED', label: 'Confirmed' },
  { id: 'COMPLETED', label: 'Completed' }, { id: 'CANCELLED', label: 'Cancelled' },
]

const SELECT_CLASS = 'h-10 rounded-[var(--radius-sm)] border border-border bg-surface-2 px-3 text-sm text-ink outline-none focus:border-accent focus:ring-2 focus:ring-accent/20'

export default function Bookings() {
  const push = useToastStore((s) => s.push)
  const [locationType, setLocationType] = useState<'' | 'INDOOR' | 'OUTDOOR'>('')
  const [pickup, setPickup] = useState<'' | 'AWAITING_PICKUP' | 'PICKED_UP'>('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<BookingStatus | 'ALL'>('ALL')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PagedResponse<Booking> | null>(null)
  const [loading, setLoading] = useState(true)
  const [showNewBooking, setShowNewBooking] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  async function advance(b: Booking) {
    const next = nextStage(b.orderStage)
    if (!next) return
    setBusyId(b.id)
    try {
      await updateBookingStage(b.id, next)
      setData((d) => d && { ...d, content: d.content.map((x) => (x.id === b.id ? { ...x, orderStage: next } : x)) })
      push(`${b.bookingNumber}: ${STAGE_LABEL[next]}`)
    } catch (err) { push(err instanceof ApiError ? err.message : 'Could not update the stage.') }
    finally { setBusyId(null) }
  }

  useEffect(() => {
    setLoading(true)
    fetchBookings({ search, status: status === 'ALL' ? '' : status, locationType, pickup, page, size: 6 })
      .then((res) => setData(res))
      .catch((e) => { setData(null); push(e instanceof ApiError ? e.message : 'Could not load bookings.') })
      .finally(() => setLoading(false))
  }, [search, status, locationType, pickup, page, refreshKey, push])

  useEffect(() => { setPage(0) }, [search, status, locationType, pickup])

  async function runPickupAction(id: string, action: 'ready' | 'picked' | 'remind') {
    setBusyId(id)
    try {
      if (action === 'ready') { await markBookingReadyForPickup(id); push('Marked ready for pickup') }
      else if (action === 'picked') { await markBookingPickedUp(id); push('Marked as picked up') }
      else {
        const r = await sendPickupReminder(id)
        const via = [r.whatsappSent && 'WhatsApp', r.emailSent && 'email'].filter(Boolean).join(' and ')
        push(`Reminder sent via ${via}`)
      }
      setRefreshKey((k) => k + 1)
    } catch (e) {
      push(e instanceof ApiError ? e.message : 'Something went wrong. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="w-full max-w-xs"><SearchInput value={search} onChange={setSearch} placeholder="Search bookings…" /></div>
        <div className="flex flex-wrap items-center gap-3">
          <select aria-label="Filter by location" value={locationType} onChange={(e) => setLocationType(e.target.value as typeof locationType)} className={SELECT_CLASS}>
            <option value="">Indoor + Outdoor</option>
            <option value="INDOOR">Indoor only</option>
            <option value="OUTDOOR">Outdoor only</option>
          </select>
          <select aria-label="Filter by pickup" value={pickup} onChange={(e) => setPickup(e.target.value as typeof pickup)} className={SELECT_CLASS}>
            <option value="">All pickup states</option>
            <option value="AWAITING_PICKUP">Ready — awaiting pickup</option>
            <option value="PICKED_UP">Picked up</option>
          </select>
          <Tabs tabs={STATUS_TABS} active={status} onChange={setStatus} />
          <Button size="md" onClick={() => setShowNewBooking(true)}><Plus className="h-4 w-4" /> New booking</Button>
        </div>
      </div>

      <Card className="overflow-hidden p-0">
        {loading ? (
          <div className="divide-y divide-border">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-center gap-4 p-4">
                <Skeleton className="h-10 w-10 rounded-full" />
                <div className="flex-1 space-y-2"><Skeleton className="h-3.5 w-40" /><Skeleton className="h-3 w-24" /></div>
                <Skeleton className="h-5 w-20" />
              </div>
            ))}
          </div>
        ) : !data || data.content.length === 0 ? (
          <EmptyState
            icon={<CalendarX2 className="h-10 w-10" />}
            title="No bookings found"
            description="Try a different search term or filter, or create a new booking to get started."
            action={<Button variant="outline" onClick={() => { setSearch(''); setStatus('ALL'); setLocationType(''); setPickup('') }}>Clear filters</Button>}
          />
        ) : (
          <>
            <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                  <th scope="col" className="px-6 py-3">Client</th>
                  <th scope="col" className="px-4 py-3">Package</th>
                  <th scope="col" className="px-4 py-3">Date</th>
                  <th scope="col" className="px-4 py-3">Amount</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  <th scope="col" className="px-4 py-3">Location</th>
                  <th scope="col" className="px-4 py-3">Stage</th>
                  <th scope="col" className="px-4 py-3">Pickup</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.content.map((b) => (
                  <tr key={b.id} className="group cursor-pointer transition-colors hover:bg-surface-2">
                    <td className="px-6 py-3.5">
                      <Link to={`/admin/bookings/${b.id}`} className="flex items-center gap-3">
                        <Avatar src={b.clientAvatar} name={b.clientName} size={36} />
                        <div>
                          <p className="font-medium text-ink">{b.clientName}</p>
                          <p className="text-xs text-ink-faint">{b.bookingNumber}</p>
                        </div>
                      </Link>
                    </td>
                    <td className="px-4 py-3.5">
                      <p className="text-ink">{b.packageName}</p>
                      <Badge tone={CATEGORY_TONE[b.shootCategory] as any} className="mt-1">{b.shootCategory.replace('_', ' ')}</Badge>
                    </td>
                    <td className="px-4 py-3.5 text-ink-muted">{formatDate(b.bookingDate)}</td>
                    <td className="px-4 py-3.5">
                      <p className="text-ink">{formatCurrency(b.totalAmount)}</p>
                      {b.balanceAmount > 0 && <p className="text-xs text-warning">{formatCurrency(b.balanceAmount)} due</p>}
                    </td>
                    <td className="px-4 py-3.5"><Badge tone={BOOKING_STATUS_TONE[b.status] as any}>{b.status.replace('_', ' ')}</Badge></td>
                    <td className="px-4 py-3.5">
                      <Badge tone={b.locationType === 'OUTDOOR' ? 'info' : 'accent'}>{b.locationType === 'OUTDOOR' ? 'Outdoor' : 'Indoor'}</Badge>
                      {b.locationType === 'OUTDOOR' && b.venue && <p className="mt-1 max-w-[160px] truncate text-xs text-ink-faint" title={b.venue}>{b.venue}</p>}
                    </td>
                    <td className="px-4 py-3.5">
                      <p className="text-ink-muted">{STAGE_LABEL[b.orderStage] ?? b.orderStage}</p>
                      {b.status !== 'CANCELLED' && nextStage(b.orderStage) && (
                        <Button size="sm" variant="outline" className="mt-1" disabled={busyId === b.id} onClick={() => advance(b)}
                          title={`Mark as ${STAGE_LABEL[nextStage(b.orderStage)!]}`}>
                          <ArrowRight className="h-3.5 w-3.5" /> {STAGE_LABEL[nextStage(b.orderStage)!]}
                        </Button>
                      )}
                    </td>
                    <td className="px-4 py-3.5">
                      {b.pickupStatus === 'AWAITING_PICKUP' ? (
                        <div className="space-y-1.5">
                          <Badge tone="warning">Awaiting pickup{typeof b.daysAwaitingPickup === 'number' ? ` · ${b.daysAwaitingPickup}d` : ''}</Badge>
                          <div className="flex gap-1.5">
                            <Button size="sm" variant="outline" disabled={busyId === b.id} onClick={() => runPickupAction(b.id, 'remind')} title={b.pickupReminderCount ? `${b.pickupReminderCount} reminder(s) sent` : 'No reminder sent yet'}>
                              <BellRing className="h-3.5 w-3.5" /> Remind{b.pickupReminderCount ? ` (${b.pickupReminderCount})` : ''}
                            </Button>
                            <Button size="sm" variant="outline" disabled={busyId === b.id} onClick={() => runPickupAction(b.id, 'picked')}>
                              <CheckCircle2 className="h-3.5 w-3.5" /> Picked up
                            </Button>
                          </div>
                        </div>
                      ) : b.pickupStatus === 'PICKED_UP' ? (
                        <Badge tone="success">Picked up</Badge>
                      ) : b.status !== 'CANCELLED' ? (
                        <Button size="sm" variant="outline" disabled={busyId === b.id} onClick={() => runPickupAction(b.id, 'ready')}>
                          <PackageCheck className="h-3.5 w-3.5" /> Mark ready
                        </Button>
                      ) : <span className="text-xs text-ink-faint">—</span>}
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

      <NewBookingModal
        open={showNewBooking}
        onClose={() => setShowNewBooking(false)}
        onCreated={() => { setPage(0); setRefreshKey((k) => k + 1) }}
      />
    </div>
  )
}
