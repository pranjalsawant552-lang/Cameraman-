import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { OrderStageStepper } from '@/components/ui/Stepper'
import { fetchMyBookings, cancelBooking, rescheduleBooking } from '@/lib/api'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Textarea } from '@/components/ui/Input'
import { RescheduleModal } from '@/components/shared/RescheduleModal'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'
import { hhmm } from '@/lib/bookingUtils'
import type { Booking } from '@/lib/types'
import { useAuthStore } from '@/store/useAuthStore'
import { formatCurrency, formatDate } from '@/lib/utils'
import { BOOKING_STATUS_TONE, CATEGORY_TONE } from '@/lib/statusTone'
import { CalendarX2 } from 'lucide-react'

export default function PortalBookings() {
  const { user } = useAuthStore()
  const [myBookings, setMyBookings] = useState<Booking[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [cancelTarget, setCancelTarget] = useState<Booking | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelling, setCancelling] = useState(false)
  const [rescheduleTarget, setRescheduleTarget] = useState<Booking | null>(null)
  const push = useToastStore((s) => s.push)

  const load = useCallback(() => {
    if (!user?.id) { setLoading(false); return }
    setError(null)
    fetchMyBookings(user.id)
      .then(setMyBookings)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load your bookings.'))
      .finally(() => setLoading(false))
  }, [user?.id])
  useEffect(load, [load])

  async function confirmCancel() {
    if (!cancelTarget || !cancelReason.trim()) return
    setCancelling(true)
    try {
      await cancelBooking(cancelTarget.id, cancelReason.trim())
      push('Booking cancelled')
      setCancelTarget(null); setCancelReason(''); load()
    } catch (e) {
      // e.g. "within 24 hours of the shoot and can no longer be self-cancelled. Please contact us"
      push(e instanceof ApiError ? e.message : 'Could not cancel this booking.')
    } finally { setCancelling(false) }
  }

  if (loading) {
    return <div className="space-y-4">{Array.from({ length: 2 }).map((_, i) => <Skeleton key={i} className="h-40 rounded-[var(--radius-lg)]" />)}</div>
  }

  if (error) {
    return <Card><EmptyState icon={<CalendarX2 className="h-10 w-10" />} title="Couldn't load your bookings" description={error} action={<Button onClick={() => { setLoading(true); load() }}>Try again</Button>} /></Card>
  }

  if (myBookings.length === 0) {
    return <Card><EmptyState icon={<CalendarX2 className="h-10 w-10" />} title="No bookings yet" description="Book your first session in a few taps." action={<Link to="/portal/book"><Button>Book a session</Button></Link>} /></Card>
  }

  return (
    <div className="space-y-4">
      {myBookings.map((b) => (
        <Card key={b.id} className="p-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <div className="flex items-center gap-2">
                <p className="font-display text-lg text-ink">{b.packageName}</p>
                <Badge tone={CATEGORY_TONE[b.shootCategory] as any}>{b.shootCategory.replace('_', ' ')}</Badge>
              </div>
              <p className="mt-1 text-sm text-ink-muted">{formatDate(b.bookingDate, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</p>
              <p className="text-xs text-ink-faint">{b.bookingNumber} · {b.photographerName ?? 'Photographer to be assigned'}</p>
            </div>
            <div className="text-right">
              <Badge tone={BOOKING_STATUS_TONE[b.status] as any}>{b.status}</Badge>
              <p className="mt-2 text-sm text-ink">{formatCurrency(b.totalAmount)}</p>
              {b.balanceAmount > 0 && <p className="text-xs text-warning">{formatCurrency(b.balanceAmount)} due</p>}
            </div>
          </div>
          {b.status !== 'CANCELLED' && (
            <div className="mt-6 border-t border-border pt-6">
              <OrderStageStepper current={b.orderStage} />
            </div>
          )}
          {b.courierTrackingNumber && <p className="mt-3 text-xs text-ink-muted">Dispatched via {b.courierProvider ?? 'courier'}: tracking {b.courierTrackingNumber}</p>}
          {b.expectedDeliveryDate && b.orderStage !== 'DELIVERED' && <p className="mt-1 text-xs text-ink-muted">Expected delivery {formatDate(b.expectedDeliveryDate)}</p>}
          {b.status !== 'CANCELLED' && (
            <div className="mt-3 flex flex-wrap gap-3 text-xs">
              <Link to="/portal/gallery" className="text-accent hover:underline">Open my gallery</Link>
              <Link to="/portal/invoices" className="text-accent hover:underline">Invoices &amp; receipts</Link>
            </div>
          )}
          {b.startTime && <p className="mt-3 text-xs text-ink-faint">Starts at {hhmm(b.startTime)}{b.venue ? ` · ${b.venue}` : ''}</p>}
          {b.status === 'CANCELLED' && b.cancelledReason && <p className="mt-3 text-xs text-ink-faint">Cancelled: {b.cancelledReason}</p>}
          {['PENDING', 'CONFIRMED', 'RESCHEDULED'].includes(b.status) && (
            <div className="mt-4 flex flex-wrap gap-2">
              <Button size="sm" variant="outline" onClick={() => setRescheduleTarget(b)}>Reschedule</Button>
              <Button size="sm" variant="ghost" onClick={() => setCancelTarget(b)}>Cancel booking</Button>
              <p className="w-full text-xs text-ink-faint">Changes close shortly before the shoot (the studio's policy window). After that, please contact us.</p>
            </div>
          )}
        </Card>
      ))}
      {rescheduleTarget && (
        <RescheduleModal
          open
          title={`Reschedule ${rescheduleTarget.bookingNumber}`}
          currentDate={rescheduleTarget.bookingDate}
          currentStartTime={hhmm(rescheduleTarget.startTime) || '10:00'}
          onClose={() => setRescheduleTarget(null)}
          onReschedule={async (date, start, end, reason) => {
            await rescheduleBooking(rescheduleTarget.id, date, start, end || undefined as unknown as string, reason ?? 'Client requested a new time')
            load()
          }}
        />
      )}
      <Modal open={!!cancelTarget} onClose={() => setCancelTarget(null)} title="Cancel this booking?"
        footer={<><Button variant="outline" onClick={() => setCancelTarget(null)}>Keep booking</Button><Button variant="danger" loading={cancelling} disabled={!cancelReason.trim()} onClick={confirmCancel}>Cancel booking</Button></>}>
        <Textarea label="Reason (required)" value={cancelReason} maxLength={300} rows={3} onChange={(e) => setCancelReason(e.target.value)} />
      </Modal>
    </div>
  )
}
