import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarCheck, ChevronLeft, ChevronRight, Clock, Camera, CheckCircle2 } from 'lucide-react'
import { Card, EmptyState, Skeleton, Badge } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { WizardSteps } from '@/components/ui/Stepper'
import {
  createBooking, createPaymentOrder, fetchActivePackages, fetchBlockedDates, fetchIntakeQuestionsForCategory,
  fetchSlotAvailability, submitIntakeAnswers, verifyRazorpayPayment,
} from '@/lib/api'
import type { IntakeQuestion } from '@/lib/api'
import type { BlockedDate, Booking, CatalogPackage, SlotAvailability } from '@/lib/types'
import { ApiError } from '@/lib/apiClient'
import { useAuthStore } from '@/store/useAuthStore'
import { useToastStore } from '@/store/useToastStore'
import { addHours, bookableStarts, categoryLabel, hhmm, monthCells, parseIsoDate, toIsoDate } from '@/lib/bookingUtils'
import { formatCurrency, formatDate } from '@/lib/utils'
import { openRazorpayCheckout } from '@/lib/razorpay'
import { STUDIO_NAME } from '@/lib/branding'
import { cn } from '@/lib/utils'

const STEPS = ['Category', 'Package', 'Date', 'Time', 'Details', 'Review']
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const MAX_NOTES = 1000

export default function PortalBook() {
  const { user } = useAuthStore()
  const push = useToastStore((s) => s.push)

  const [step, setStep] = useState(0)
  const [packages, setPackages] = useState<CatalogPackage[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [category, setCategory] = useState<string | null>(null)
  const [pkg, setPkg] = useState<CatalogPackage | null>(null)
  const [date, setDate] = useState<string | null>(null)
  const [startTime, setStartTime] = useState<string | null>(null)
  const [notes, setNotes] = useState('')
  const [venue, setVenue] = useState('')
  const [marketingOptIn, setMarketingOptIn] = useState(true)
  const [questions, setQuestions] = useState<IntakeQuestion[]>([])
  const [answers, setAnswers] = useState<Record<string, string>>({})

  const [month, setMonth] = useState(() => { const n = new Date(); return new Date(n.getFullYear(), n.getMonth(), 1) })
  const [blocked, setBlocked] = useState<BlockedDate[]>([])
  const [slots, setSlots] = useState<SlotAvailability[] | null>(null)
  const [slotsError, setSlotsError] = useState<string | null>(null)

  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [created, setCreated] = useState<Booking | null>(null)
  const [paying, setPaying] = useState(false)
  const [paid, setPaid] = useState(false)
  const [gatewayOff, setGatewayOff] = useState(false)

  useEffect(() => {
    fetchActivePackages()
      .then(setPackages)
      .catch((e) => setLoadError(e instanceof ApiError ? e.message : 'Could not load packages.'))
      .finally(() => setLoading(false))
  }, [])

  const categories = useMemo(() => Array.from(new Set(packages.map((p) => p.category))), [packages])
  const categoryPackages = useMemo(() => packages.filter((p) => p.category === category), [packages, category])
  const hours = pkg?.durationHours && pkg.durationHours > 0 ? pkg.durationHours : 1

  // Blocked dates for the visible month (disables those days in the calendar).
  useEffect(() => {
    if (step !== 2) return
    const from = toIsoDate(month)
    const to = toIsoDate(new Date(month.getFullYear(), month.getMonth() + 1, 0))
    fetchBlockedDates(from, to).then(setBlocked).catch(() => setBlocked([]))
  }, [step, month])

  // Slots for the chosen day. Re-fetched whenever the day changes so stale availability is never shown.
  useEffect(() => {
    if (!date || step !== 3) return
    let cancelled = false
    setSlots(null); setSlotsError(null); setStartTime(null)
    fetchSlotAvailability(date)
      .then((s) => { if (!cancelled) setSlots(s) })
      .catch((e) => { if (!cancelled) setSlotsError(e instanceof ApiError ? e.message : 'Could not load time slots.') })
    return () => { cancelled = true }
  }, [date, step])

  const blockedSet = useMemo(() => new Set(blocked.map((b) => b.date)), [blocked])
  const starts = useMemo(() => (slots ? bookableStarts(slots, hours) : new Set<string>()), [slots, hours])

  const loadQuestions = useCallback(async (cat: string) => {
    try { setQuestions(await fetchIntakeQuestionsForCategory(cat)) } catch { setQuestions([]) }
  }, [])

  function reset() {
    setStep(0); setCategory(null); setPkg(null); setDate(null); setStartTime(null)
    setNotes(''); setVenue(''); setAnswers({}); setQuestions([]); setCreated(null)
    setSubmitError(null); setPaid(false)
  }

  function missingRequired(): string | null {
    for (const q of questions) {
      if (q.required && !(answers[q.questionKey] ?? '').trim()) return q.questionText
    }
    return null
  }

  function next() {
    if (step === 4) {
      const missing = missingRequired()
      if (missing) { push(`Please answer: ${missing}`); return }
    }
    setStep((s) => Math.min(STEPS.length - 1, s + 1))
  }

  async function confirm() {
    if (!pkg || !date || !startTime) return
    setSubmitting(true); setSubmitError(null)
    try {
      const booking = await createBooking({
        clientId: user?.id, // ignored server-side for clients; identity comes from the JWT
        clientName: `${user?.firstName ?? ''} ${user?.lastName ?? ''}`.trim(),
        clientMobile: user?.mobile ?? '',
        clientEmail: user?.email,
        bookingDate: date,
        startTime,
        endTime: addHours(startTime, hours),
        packageId: pkg.id,
        packageName: pkg.name,
        type: 'ONLINE',
        shootCategory: pkg.category,
        venue: venue.trim() || undefined,
        notes: notes.trim() || undefined,
        totalAmount: pkg.effectivePrice, // server recomputes from the package
        marketingOptIn,
      })
      if (questions.length > 0) {
        try { await submitIntakeAnswers(booking.id, answers) } catch (e) {
          push(e instanceof ApiError ? e.message : 'Booking saved, but your answers could not be stored.')
        }
      }
      setCreated(booking)
      push('Booking confirmed')
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Could not create the booking. Please try again.'
      setSubmitError(msg)
      // The slot may have been taken in the meantime: send the client back to pick another.
      if (err instanceof ApiError && (err.errorCode === 'SLOT_TAKEN' || err.errorCode === 'DATE_BLOCKED')) setStep(3)
    } finally {
      setSubmitting(false)
    }
  }

  async function payAdvance() {
    if (!created) return
    setPaying(true)
    try {
      const amount = Math.max(1, Math.round(created.totalAmount * 0.3))
      const order = await createPaymentOrder({
        bookingId: created.id, amount, paymentType: 'ADVANCE',
        clientName: created.clientName, notes: `Advance for ${created.bookingNumber}`,
      })
      const result = await openRazorpayCheckout({
        keyId: order.keyId, orderId: order.orderId, amountPaise: Math.round(order.amount * 100),
        currency: order.currency, studioName: STUDIO_NAME, description: `Advance for ${created.bookingNumber}`,
        prefillName: user ? `${user.firstName} ${user.lastName}`.trim() : undefined,
        prefillEmail: user?.email, prefillContact: user?.mobile,
      })
      await verifyRazorpayPayment({
        razorpayOrderId: result.razorpay_order_id,
        razorpayPaymentId: result.razorpay_payment_id,
        razorpaySignature: result.razorpay_signature,
      })
      setPaid(true)
      push('Advance received. Thank you!')
    } catch (err) {
      if (err instanceof ApiError && /not configured/i.test(err.message)) {
        setGatewayOff(true) // online payment isn't set up: silently fall back to "pay at studio"
      } else {
        push(err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Payment could not be completed.')
      }
    } finally {
      setPaying(false)
    }
  }

  // ───────────────────────── render ─────────────────────────
  if (loading) {
    return <div className="space-y-4"><Skeleton className="h-10 w-2/3" /><Skeleton className="h-64 rounded-[var(--radius-lg)]" /></div>
  }
  if (loadError) {
    return <Card className="p-6"><EmptyState icon={<Camera className="h-10 w-10" />} title="Booking is unavailable" description={loadError} action={<Button onClick={() => window.location.reload()}>Try again</Button>} /></Card>
  }
  if (packages.length === 0) {
    return <Card className="p-6"><EmptyState icon={<Camera className="h-10 w-10" />} title="No packages open for booking" description="Please contact the studio to arrange a session." action={<Link to="/contact"><Button>Contact us</Button></Link>} /></Card>
  }

  if (created) {
    return (
      <Card className="mx-auto max-w-xl p-8 text-center">
        <CheckCircle2 className="mx-auto h-12 w-12 text-success" />
        <h1 className="mt-4 font-display text-2xl text-ink">You're booked!</h1>
        <p className="mt-2 text-sm text-ink-muted">
          {created.packageName} on {formatDate(created.bookingDate, { weekday: 'long', day: 'numeric', month: 'long' })} at {hhmm(created.startTime)}.
        </p>
        <p className="mt-1 text-xs text-ink-faint">Reference {created.bookingNumber}. A confirmation will reach you by notification, email or WhatsApp where available.</p>

        {!gatewayOff && !paid && (
          <div className="mt-6 rounded-[var(--radius-md)] border border-border bg-surface-2 p-4 text-left">
            <p className="text-sm font-medium text-ink">Secure your slot with an advance</p>
            <p className="mt-1 text-xs text-ink-muted">Pay 30% ({formatCurrency(Math.round(created.totalAmount * 0.3))}) online now, or skip and pay at the studio.</p>
            <Button className="mt-3" onClick={payAdvance} loading={paying}>Pay advance</Button>
          </div>
        )}
        {paid && <Badge tone="success" className="mt-6">Advance paid</Badge>}

        <div className="mt-6 flex flex-wrap justify-center gap-2">
          <Link to="/portal/bookings"><Button>View my bookings</Button></Link>
          <Button variant="outline" onClick={reset}>Book another session</Button>
        </div>
      </Card>
    )
  }

  const cells = monthCells(month.getFullYear(), month.getMonth())
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const horizon = new Date(today); horizon.setFullYear(horizon.getFullYear() + 1)

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="font-display text-2xl text-ink">Book a session</h1>
        <p className="mt-1 text-sm text-ink-muted">Choose what you'd like to shoot, then pick a day and time that suits you.</p>
      </div>
      <WizardSteps steps={STEPS} current={step} />

      <Card className="p-6">
        {step === 0 && (
          <div>
            <h2 className="font-medium text-ink">What kind of shoot?</h2>
            <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
              {categories.map((c) => (
                <button key={c} type="button" onClick={() => { setCategory(c); setPkg(null); loadQuestions(c); setStep(1) }}
                  className={cn('rounded-[var(--radius-md)] border p-4 text-left text-sm transition-colors hover:border-accent',
                    category === c ? 'border-accent bg-accent/10 text-ink' : 'border-border text-ink-muted')}>
                  <Camera className="mb-2 h-5 w-5 text-accent" />{categoryLabel(c)}
                </button>
              ))}
            </div>
          </div>
        )}

        {step === 1 && (
          <div>
            <h2 className="font-medium text-ink">Choose a package</h2>
            <div className="mt-4 space-y-3">
              {categoryPackages.length === 0 && <p className="text-sm text-ink-muted">No packages in this category right now.</p>}
              {categoryPackages.map((p) => (
                <button key={p.id} type="button" onClick={() => { setPkg(p); setDate(null); setStartTime(null) }}
                  className={cn('w-full rounded-[var(--radius-md)] border p-4 text-left transition-colors hover:border-accent',
                    pkg?.id === p.id ? 'border-accent bg-accent/10' : 'border-border')}>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-medium text-ink">{p.name}</p>
                      {p.description && <p className="mt-1 text-sm text-ink-muted">{p.description}</p>}
                      <p className="mt-2 text-xs text-ink-faint">
                        {p.durationHours ? `${p.durationHours}h shoot` : ''}{p.photoCount ? ` · ${p.photoCount} photos` : ''}{p.deliveryDays ? ` · delivered in ${p.deliveryDays} days` : ''}
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="font-medium text-ink">{formatCurrency(p.effectivePrice)}</p>
                      {p.discountedPrice ? <p className="text-xs text-ink-faint line-through">{formatCurrency(p.basePrice)}</p> : null}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </div>
        )}

        {step === 2 && (
          <div>
            <div className="flex items-center justify-between">
              <h2 className="font-medium text-ink">Pick a date</h2>
              <div className="flex items-center gap-1">
                <Button variant="ghost" size="icon" aria-label="Previous month" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}
                  disabled={month <= new Date(today.getFullYear(), today.getMonth(), 1)}><ChevronLeft className="h-4 w-4" /></Button>
                <span className="min-w-32 text-center text-sm text-ink">{month.toLocaleString('en-IN', { month: 'long', year: 'numeric' })}</span>
                <Button variant="ghost" size="icon" aria-label="Next month" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}
                  disabled={new Date(month.getFullYear(), month.getMonth() + 1, 1) > horizon}><ChevronRight className="h-4 w-4" /></Button>
              </div>
            </div>
            <div className="mt-4 grid grid-cols-7 gap-1 text-center text-xs text-ink-faint">
              {WEEKDAYS.map((d) => <span key={d}>{d}</span>)}
            </div>
            <div className="mt-1 grid grid-cols-7 gap-1">
              {cells.map((d, i) => {
                if (!d) return <span key={i} />
                const iso = toIsoDate(d)
                const isBlocked = blockedSet.has(iso)
                const disabled = d < today || d > horizon || isBlocked
                const reason = blocked.find((b) => b.date === iso)?.reason
                return (
                  <button key={iso} type="button" disabled={disabled} onClick={() => setDate(iso)}
                    title={isBlocked ? reason || 'Studio closed' : undefined}
                    aria-label={`${formatDate(d, { weekday: 'long', day: 'numeric', month: 'long' })}${isBlocked ? ', studio closed' : ''}`}
                    className={cn('h-10 rounded-[var(--radius-sm)] text-sm transition-colors',
                      disabled ? 'cursor-not-allowed text-ink-faint/50 line-through' : 'text-ink hover:bg-accent/15',
                      date === iso && 'bg-accent text-accent-ink hover:bg-accent')}>
                    {d.getDate()}
                  </button>
                )
              })}
            </div>
            {blocked.length > 0 && <p className="mt-3 text-xs text-ink-faint">Struck-through days are unavailable (studio closed or in the past).</p>}
          </div>
        )}

        {step === 3 && (
          <div>
            <h2 className="font-medium text-ink">Pick a start time{date ? ` on ${formatDate(date, { day: 'numeric', month: 'long' })}` : ''}</h2>
            {pkg && <p className="mt-1 text-xs text-ink-faint"><Clock className="mr-1 inline h-3 w-3" />Your {pkg.name} session takes about {hours} hour{hours > 1 ? 's' : ''}.</p>}
            {slotsError && <p role="alert" className="mt-4 text-sm text-danger">{slotsError}</p>}
            {!slots && !slotsError && <div className="mt-4 grid grid-cols-3 gap-2 sm:grid-cols-5">{Array.from({ length: 10 }).map((_, i) => <Skeleton key={i} className="h-10" />)}</div>}
            {slots && (
              <>
                <div className="mt-4 grid grid-cols-3 gap-2 sm:grid-cols-5">
                  {slots.map((s) => {
                    const t = hhmm(s.startTime)
                    const ok = starts.has(t)
                    return (
                      <button key={t} type="button" disabled={!ok} onClick={() => setStartTime(t)} title={s.closedReason}
                        className={cn('h-10 rounded-[var(--radius-sm)] border text-sm transition-colors',
                          ok ? 'border-border text-ink hover:border-accent' : 'cursor-not-allowed border-transparent bg-surface-2 text-ink-faint/60 line-through',
                          startTime === t && 'border-accent bg-accent text-accent-ink hover:border-accent')}>
                        {t}
                      </button>
                    )
                  })}
                </div>
                {starts.size === 0 && <p className="mt-4 text-sm text-ink-muted">No time is free for a {hours}-hour session on this day. Please choose another date.</p>}
              </>
            )}
          </div>
        )}

        {step === 4 && (
          <div className="space-y-4">
            <h2 className="font-medium text-ink">A few details</h2>
            <Input label="Venue / location (optional)" value={venue} maxLength={200} onChange={(e) => setVenue(e.target.value)} />
            {questions.map((q) => {
              const id = `q-${q.questionKey}`
              const val = answers[q.questionKey] ?? ''
              const set = (v: string) => setAnswers((a) => ({ ...a, [q.questionKey]: v }))
              const label = `${q.questionText}${q.required ? ' *' : ''}`
              if (q.fieldType === 'SELECT') {
                return (
                  <Select key={q.id} id={id} label={label} value={val} onChange={(e) => set(e.target.value)}>
                    <option value="">Select…</option>
                    {(q.options ?? '').split(',').map((o) => o.trim()).filter(Boolean).map((o) => <option key={o} value={o}>{o}</option>)}
                  </Select>
                )
              }
              return <Input key={q.id} id={id} label={label} type={q.fieldType === 'NUMBER' ? 'number' : q.fieldType === 'DATE' ? 'date' : 'text'} maxLength={500} value={val} onChange={(e) => set(e.target.value)} />
            })}
            <Textarea label="Anything else we should know? (optional)" value={notes} maxLength={MAX_NOTES} onChange={(e) => setNotes(e.target.value)} rows={3} />
            <label className="flex items-start gap-2 text-sm text-ink-muted">
              <input type="checkbox" className="mt-1" checked={marketingOptIn} onChange={(e) => setMarketingOptIn(e.target.checked)} />
              <span>Send me occasional reminders and offers (birthdays, anniversaries). You can change this any time in Settings.</span>
            </label>
          </div>
        )}

        {step === 5 && pkg && date && startTime && (
          <div>
            <h2 className="font-medium text-ink">Review and confirm</h2>
            <dl className="mt-4 divide-y divide-border text-sm">
              <Row k="Package" v={`${pkg.name} (${categoryLabel(pkg.category)})`} />
              <Row k="Date" v={formatDate(date, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })} />
              <Row k="Time" v={`${startTime} – ${addHours(startTime, hours)}`} />
              {venue && <Row k="Venue" v={venue} />}
              {notes && <Row k="Notes" v={notes} />}
              {questions.filter((q) => answers[q.questionKey]).map((q) => <Row key={q.id} k={q.questionText} v={answers[q.questionKey]} />)}
              <Row k="Total" v={formatCurrency(pkg.effectivePrice)} strong />
            </dl>
            <p className="mt-3 text-xs text-ink-faint">The final price is confirmed by the studio. You can cancel or reschedule from My bookings, subject to the studio's policy window.</p>
            {submitError && <p role="alert" className="mt-4 rounded-[var(--radius-md)] bg-danger-bg px-3 py-2 text-sm text-danger">{submitError}</p>}
          </div>
        )}
      </Card>

      <div className="flex justify-between">
        <Button variant="outline" onClick={() => setStep((s) => Math.max(0, s - 1))} disabled={step === 0 || submitting}>Back</Button>
        {step < STEPS.length - 1 ? (
          <Button onClick={next} disabled={(step === 0 && !category) || (step === 1 && !pkg) || (step === 2 && !date) || (step === 3 && !startTime)}>Continue</Button>
        ) : (
          <Button onClick={confirm} loading={submitting}><CalendarCheck className="h-4 w-4" /> Confirm booking</Button>
        )}
      </div>
    </div>
  )
}

function Row({ k, v, strong }: { k: string; v: string; strong?: boolean }) {
  return (
    <div className="flex justify-between gap-4 py-2.5">
      <dt className="text-ink-muted">{k}</dt>
      <dd className={cn('text-right text-ink', strong && 'font-medium')}>{v}</dd>
    </div>
  )
}
