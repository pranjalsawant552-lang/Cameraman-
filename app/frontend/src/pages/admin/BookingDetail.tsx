import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, BellRing, CalendarClock, CheckCircle2, FileText, Gift, Package, PackageCheck, Phone, Sparkles, Ban, Truck, UserCog, History } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, Badge, Avatar, Divider } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { RescheduleModal } from '@/components/shared/RescheduleModal'
import { hhmm } from '@/lib/bookingUtils'
import { Modal } from '@/components/ui/Modal'
import { BookingStatusPanel } from '@/components/booking/BookingStatusPanel'
import {
  fetchBookingById, fetchRecommendationsForBooking, updateBookingStage, cancelBooking, rescheduleBooking, assignBookingEditor,
  updateBookingCourier, markBookingReadyForPickup, markBookingPickedUp, sendPickupReminder, generateBookingInvoice, confirmBooking, fetchStaff, fetchIntakeAnswers, fetchIntakeQuestionsForCategory, type IntakeQuestion,
} from '@/lib/api'
import type { StaffMember } from '@/lib/types'
import { useAuthStore } from '@/store/useAuthStore'
import { isAdmin } from '@/lib/access'
import type { Booking, OrderStage, PackageUpgradeSuggestion, ProductSuggestion } from '@/lib/types'
import { formatCurrency, formatDate } from '@/lib/utils'
import { BOOKING_STATUS_TONE, CATEGORY_TONE, ORDER_STAGES, STAGE_LABEL } from '@/lib/statusTone'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

const STAGES: OrderStage[] = [...ORDER_STAGES]

export default function BookingDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [booking, setBooking] = useState<Booking | null>(null)
  const [addOns, setAddOns] = useState<ProductSuggestion[]>([])
  const [upgrades, setUpgrades] = useState<PackageUpgradeSuggestion[]>([])
  const [stageUpdating, setStageUpdating] = useState(false)
  const [showCancel, setShowCancel] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelling, setCancelling] = useState(false)
  const push = useToastStore((s) => s.push)
  const roles = useAuthStore((s) => s.user?.roles)
  const canAssignEditor = isAdmin(roles) || (roles ?? []).includes('MANAGER')
  const canEditCourier = canAssignEditor || (roles ?? []).includes('RECEPTIONIST')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [editors, setEditors] = useState<StaffMember[]>([])
  const [courier, setCourier] = useState({ provider: '', tracking: '' })
  const [savingOps, setSavingOps] = useState<string | null>(null)
  const [stageNote, setStageNote] = useState('')
  const [showReschedule, setShowReschedule] = useState(false)
  const [questions, setQuestions] = useState<IntakeQuestion[]>([])
  const [answers, setAnswers] = useState<Record<string, string>>({})

  const reload = () => {
    if (!id) return
    fetchBookingById(id)
      .then((b) => { setBooking(b ?? null); if (!b) setLoadError('Booking not found.') })
      .catch((e) => setLoadError(e instanceof ApiError ? e.message : 'Could not load this booking.'))
  }

  useEffect(() => {
    if (!id) return
    reload()
    fetchRecommendationsForBooking(id).then((r) => { setAddOns(r.productAddOns); setUpgrades(r.packageUpgrades) }).catch(() => undefined)
    fetchIntakeAnswers(id).then(setAnswers).catch(() => setAnswers({}))
  }, [id]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!booking) return
    setCourier({ provider: booking.courierProvider ?? '', tracking: booking.courierTrackingNumber ?? '' })
    fetchIntakeQuestionsForCategory(booking.shootCategory).then(setQuestions).catch(() => setQuestions([]))
  }, [booking?.id, booking?.courierProvider, booking?.courierTrackingNumber]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (canAssignEditor) fetchStaff().then((all) => setEditors(all.filter((m) => m.roles.includes('EDITOR')))).catch(() => setEditors([]))
  }, [canAssignEditor])

  async function runOp(key: string, fn: () => Promise<void>, ok: string) {
    setSavingOps(key)
    try { await fn(); push(ok); reload() }
    catch (err) { push(err instanceof ApiError ? err.message : 'That change could not be saved.') }
    finally { setSavingOps(null) }
  }

  async function handleStageChange(stage: OrderStage) {
    if (!booking || stage === booking.orderStage) return
    setStageUpdating(true)
    const previous = booking.orderStage
    setBooking({ ...booking, orderStage: stage }) // optimistic
    try {
      await updateBookingStage(booking.id, stage, stageNote.trim() || undefined)
      setStageNote('')
      push(`Stage updated to ${STAGE_LABEL[stage].toLowerCase()}`)
      reload()
    } catch (err) {
      setBooking({ ...booking, orderStage: previous }) // roll back on failure
      push(err instanceof ApiError ? err.message : 'Could not update the stage. Please try again.')
    } finally {
      setStageUpdating(false)
    }
  }

  async function handleCancel() {
    if (!booking) return
    setCancelling(true)
    try {
      await cancelBooking(booking.id, cancelReason.trim() || 'Cancelled by staff')
      setBooking({ ...booking, status: 'CANCELLED' })
      push('Booking cancelled')
      setShowCancel(false)
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not cancel the booking. Please try again.')
    } finally {
      setCancelling(false)
    }
  }

  if (loadError) return <div className="space-y-3"><p role="alert" className="text-sm text-danger">{loadError}</p><Link to="/admin/bookings" className="text-sm text-accent hover:underline">Back to bookings</Link></div>
  if (!booking) return <div className="text-sm text-ink-muted">Loading…</div>

  return (
    <>
    <div className="space-y-5">
      <Link to="/admin/bookings" className="flex items-center gap-1.5 text-sm font-medium text-ink-muted hover:text-ink">
        <ArrowLeft className="h-4 w-4" /> Back to bookings
      </Link>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex items-center gap-4">
          <Avatar src={booking.clientAvatar} name={booking.clientName} size={56} />
          <div>
            <div className="flex items-center gap-2">
              <h2 className="font-display text-2xl text-ink">{booking.clientName}</h2>
              <Badge tone={BOOKING_STATUS_TONE[booking.status] as any}>{booking.status.replace('_', ' ')}</Badge>
            </div>
            <p className="mt-0.5 text-sm text-ink-muted">{booking.bookingNumber} · {booking.packageName}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => window.open(`tel:${booking.clientMobile}`)}><Phone className="h-4 w-4" /> Contact client</Button>
          {['PENDING', 'RESCHEDULED'].includes(booking.status) && (
            <Button loading={savingOps === 'confirm'} onClick={() => runOp('confirm', async () => { await confirmBooking(booking.id) }, 'Booking confirmed')}>
              <CheckCircle2 className="h-4 w-4" /> Confirm booking
            </Button>
          )}
          {['PENDING', 'CONFIRMED', 'RESCHEDULED'].includes(booking.status) && (
            <Button variant="outline" onClick={() => setShowReschedule(true)}><CalendarClock className="h-4 w-4" /> Reschedule</Button>
          )}
          {booking.status !== 'CANCELLED' && (
            <Button variant="outline" onClick={() => setShowCancel(true)}><Ban className="h-4 w-4" /> Cancel</Button>
          )}
        </div>
      </div>

      <Card>
        <CardContent className="py-6">
          <BookingStatusPanel stage={booking.orderStage} busy={stageUpdating} disabled={booking.status === 'CANCELLED'} onChange={handleStageChange} />
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <Card>
            <CardHeader><CardTitle>Shoot details</CardTitle></CardHeader>
            <CardContent className="grid grid-cols-2 gap-4 pt-3 text-sm sm:grid-cols-3">
              <Field label="Category"><Badge tone={CATEGORY_TONE[booking.shootCategory] as any}>{booking.shootCategory.replace('_', ' ')}</Badge></Field>
              <Field label="Date">{formatDate(booking.bookingDate)}{booking.startTime ? ` · ${hhmm(booking.startTime)}${booking.endTime ? '–' + hhmm(booking.endTime) : ''}` : ''}</Field>
              <Field label="Location"><Badge tone={booking.locationType === 'OUTDOOR' ? 'info' : 'accent'}>{booking.locationType === 'OUTDOOR' ? 'Outdoor' : 'Indoor'}</Badge></Field>
              {booking.venue && <Field label="Venue">{booking.venue}</Field>}
              {booking.expectedDeliveryDate && <Field label="Expected delivery">{formatDate(booking.expectedDeliveryDate)}</Field>}
              {!!booking.rescheduledCount && <Field label="Rescheduled">{booking.rescheduledCount}×</Field>}
              {booking.cancelledReason && <Field label="Cancel reason">{booking.cancelledReason}</Field>}
              <Field label="Photographer">{booking.photographerName ?? 'Unassigned'}</Field>
              <Field label="Mobile">{booking.clientMobile}</Field>
              <Field label="Marketing opt-in">{booking.marketingOptIn ? 'Yes' : 'No'}</Field>
              <Field label="Created">{formatDate(booking.createdAt)}</Field>
            </CardContent>
          </Card>

          {booking.notes && (
            <Card><CardHeader><CardTitle>Client notes</CardTitle></CardHeader><CardContent className="pt-3 text-sm text-ink-muted">{booking.notes}</CardContent></Card>
          )}

          {questions.length > 0 && (
            <Card>
              <CardHeader><CardTitle>Intake answers</CardTitle></CardHeader>
              <CardContent className="grid grid-cols-1 gap-3 pt-3 text-sm sm:grid-cols-2">
                {questions.map((q) => (
                  <Field key={q.id} label={q.questionText}>{answers[q.questionKey] || <span className="text-ink-faint">Not answered</span>}</Field>
                ))}
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2"><UserCog className="h-4 w-4 text-accent" /> Production</CardTitle></CardHeader>
            <CardContent className="space-y-4 pt-3">
              <div className="flex flex-wrap items-end gap-3">
                <Select label="Editor" value={booking.editorId ?? ''} disabled={!canAssignEditor || savingOps === 'editor'} className="min-w-48"
                  onChange={(e) => e.target.value && runOp('editor', () => assignBookingEditor(booking.id, e.target.value), 'Editor assigned')}>
                  <option value="">{booking.editorName ?? 'Unassigned'}</option>
                  {editors.filter((m) => m.userId !== booking.editorId).map((m) => <option key={m.id} value={m.userId}>{m.firstName} {m.lastName}</option>)}
                </Select>
                {!canAssignEditor && <p className="text-xs text-ink-faint">Only managers and admins assign editors.</p>}
                {canAssignEditor && editors.length === 0 && <p className="text-xs text-ink-faint">No staff have the Editor role yet.</p>}
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
                <Input label="Courier" value={courier.provider} maxLength={60} disabled={!canEditCourier} onChange={(e) => setCourier({ ...courier, provider: e.target.value })} placeholder="e.g. Delhivery" />
                <Input label="Tracking number" value={courier.tracking} maxLength={60} disabled={!canEditCourier} onChange={(e) => setCourier({ ...courier, tracking: e.target.value })} />
                <Button variant="outline" disabled={!canEditCourier || !courier.provider.trim() || !courier.tracking.trim()} loading={savingOps === 'courier'}
                  onClick={() => runOp('courier', () => updateBookingCourier(booking.id, courier.provider.trim(), courier.tracking.trim()), 'Courier details saved')}><Truck className="h-4 w-4" /> Save</Button>
              </div>
              <Textarea label="Note for the next stage change (optional)" rows={2} maxLength={300} value={stageNote} onChange={(e) => setStageNote(e.target.value)} />
            </CardContent>
          </Card>

          {booking.status !== 'CANCELLED' && (
            <Card>
              <CardHeader><CardTitle className="flex items-center gap-2"><PackageCheck className="h-4 w-4 text-accent" /> Pickup</CardTitle></CardHeader>
              <CardContent className="space-y-3 pt-3 text-sm">
                {booking.pickupStatus === 'PICKED_UP' ? (
                  <p className="text-success">Collected by the client on {formatDate(booking.pickedUpAt)}.</p>
                ) : booking.pickupStatus === 'AWAITING_PICKUP' ? (
                  <>
                    <p className="text-warning">
                      Ready since {formatDate(booking.readyForPickupAt)}{typeof booking.daysAwaitingPickup === 'number' ? ` (${booking.daysAwaitingPickup} day${booking.daysAwaitingPickup === 1 ? '' : 's'} waiting)` : ''}.
                    </p>
                    <p className="text-xs text-ink-faint">
                      {booking.pickupReminderCount ? `${booking.pickupReminderCount} reminder(s) sent, last on ${formatDate(booking.lastPickupReminderAt)}.` : 'No reminder sent yet.'}
                    </p>
                    <div className="flex flex-wrap gap-2">
                      <Button variant="outline" loading={savingOps === 'remind'}
                        onClick={() => runOp('remind', async () => { const r = await sendPickupReminder(booking.id); push(`Reminder sent via ${[r.whatsappSent && 'WhatsApp', r.emailSent && 'email'].filter(Boolean).join(' and ')}`) }, 'Client reminded')}>
                        <BellRing className="h-4 w-4" /> Send reminder
                      </Button>
                      <Button variant="outline" loading={savingOps === 'picked'}
                        onClick={() => runOp('picked', async () => { await markBookingPickedUp(booking.id) }, 'Marked as picked up')}>
                        <CheckCircle2 className="h-4 w-4" /> Mark picked up
                      </Button>
                    </div>
                  </>
                ) : (
                  <>
                    <p className="text-ink-muted">Photos/order not yet marked ready for the client.</p>
                    <Button variant="outline" loading={savingOps === 'ready'}
                      onClick={() => runOp('ready', async () => { await markBookingReadyForPickup(booking.id) }, 'Marked ready for pickup')}>
                      <PackageCheck className="h-4 w-4" /> Mark ready for pickup
                    </Button>
                  </>
                )}
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2"><History className="h-4 w-4 text-accent" /> Order history</CardTitle></CardHeader>
            <CardContent className="pt-3">
              {!booking.stageHistory?.length ? <p className="text-sm text-ink-muted">No stage changes recorded yet.</p> : (
                <ol className="space-y-3">
                  {booking.stageHistory.map((h, i) => (
                    <li key={i} className="flex gap-3 text-sm">
                      <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-accent" />
                      <div>
                        <p className="text-ink">{h.stageLabel || h.stage}{h.changedByName ? <span className="text-ink-faint"> · {h.changedByName}</span> : null}</p>
                        <p className="text-xs text-ink-faint">{formatDate(h.createdAt)}{h.note ? ` · ${h.note}` : ''}</p>
                      </div>
                    </li>
                  ))}
                </ol>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>Payment</CardTitle></CardHeader>
            <CardContent className="pt-3">
              <div className="flex items-center justify-between text-sm">
                <span className="text-ink-muted">Total amount</span>
                <span className="font-medium text-ink">{formatCurrency(booking.totalAmount)}</span>
              </div>
              <Divider className="my-3" />
              <div className="flex items-center justify-between text-sm">
                <span className="text-ink-muted">Advance received</span>
                <span className="text-success">{formatCurrency(booking.advanceAmount)}</span>
              </div>
              <div className="mt-2 flex items-center justify-between text-sm">
                <span className="text-ink-muted">Balance due</span>
                <span className={booking.balanceAmount > 0 ? 'text-warning font-medium' : 'text-ink'}>{formatCurrency(booking.balanceAmount)}</span>
              </div>
              <Button className="mt-4 w-full" variant="outline" loading={savingOps === 'invoice'}
                onClick={async () => {
                  setSavingOps('invoice')
                  try { navigate(`/admin/invoices/${await generateBookingInvoice(booking.id)}`) }
                  catch (err) { push(err instanceof ApiError ? err.message : 'Could not open the invoice.') }
                  finally { setSavingOps(null) }
                }}>
                <FileText className="h-4 w-4" /> View / print / send invoice
              </Button>
              <div className="mt-4 h-2 w-full overflow-hidden rounded-full bg-surface-2">
                <div className="h-full rounded-full bg-accent transition-all duration-700" style={{ width: `${booking.totalAmount > 0 ? Math.min(100, (booking.advanceAmount / booking.totalAmount) * 100) : 0}%` }} />
              </div>
            </CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base"><Gift className="h-4 w-4 text-accent" /> Recommended add-ons</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 pt-2">
              {addOns.map((p) => (
                <div key={p.productId} className="flex items-center gap-3 rounded-[var(--radius-md)] border border-border p-2.5">
                  <img src={p.imageUrl} alt="" className="h-12 w-12 rounded-[var(--radius-sm)] object-cover" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-ink">{p.productName}</p>
                    <p className="text-xs text-ink-faint">{formatCurrency(p.price)} · {p.stockQuantity} in stock</p>
                  </div>
                  <Button size="sm" variant="secondary" onClick={() => push(`Suggested ${p.productName} to client`)}>Suggest</Button>
                </div>
              ))}
              {upgrades.map((u) => (
                <div key={u.packageId} className="flex items-center gap-3 rounded-[var(--radius-md)] border border-dashed border-accent/40 bg-accent/5 p-2.5">
                  <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[var(--radius-sm)] bg-accent/15 text-accent"><Package className="h-5 w-5" /></div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-ink">Upgrade to {u.packageName}</p>
                    <p className="text-xs text-ink-faint">+{formatCurrency(u.priceDifference)}</p>
                  </div>
                  <Button size="sm" onClick={() => push('Upgrade proposed to client')}>Propose</Button>
                </div>
              ))}
            </CardContent>
          </Card>

          <Card className="bg-accent/5 border-accent/20">
            <CardContent className="flex items-start gap-3 py-4">
              <Sparkles className="mt-0.5 h-5 w-5 shrink-0 text-accent" />
              <p className="text-sm text-ink-muted">
                {booking.marketingOptIn
                  ? 'This client is opted in for engagement reminders — a follow-up suggestion will be generated automatically based on active rules.'
                  : 'This client has opted out of marketing reminders. No automatic suggestions will be generated for this booking.'}
              </p>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>

    {showReschedule && (
      <RescheduleModal
        open
        title={`Reschedule ${booking.bookingNumber}`}
        currentDate={booking.bookingDate}
        currentStartTime={hhmm(booking.startTime) || '10:00'}
        onClose={() => setShowReschedule(false)}
        onReschedule={async (date, start, end, reason) => {
          await rescheduleBooking(booking.id, date, start, end || (undefined as unknown as string), reason ?? 'Rescheduled by staff')
          reload()
        }}
      />
    )}
    <Modal
      open={showCancel}
      onClose={() => setShowCancel(false)}
      title="Cancel this booking?"
      footer={
        <>
          <Button variant="outline" onClick={() => setShowCancel(false)}>Keep booking</Button>
          <Button variant="danger" onClick={handleCancel} disabled={cancelling}>{cancelling ? 'Cancelling…' : 'Confirm cancellation'}</Button>
        </>
      }
    >
      <p className="text-sm text-ink-muted">
        This marks the booking as cancelled. The client and assigned photographer will be notified where notifications are configured. This can't be undone from here.
      </p>
      <div className="mt-4">
        <Textarea label="Reason (optional)" value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} rows={3} placeholder="e.g. Client requested a different date" />
      </div>
    </Modal>
    </>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs text-ink-faint">{label}</p>
      <div className="mt-1 text-ink">{children}</div>
    </div>
  )
}
