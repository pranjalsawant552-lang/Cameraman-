import { useEffect, useMemo, useState } from 'react'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ClientCombobox } from '@/components/shared/ClientCombobox'
import { createBooking, fetchActivePackages, fetchBlockedDates, fetchSlotAvailability } from '@/lib/api'
import type { BlockedDate, Booking, CatalogPackage, ShootCategory, SlotAvailability } from '@/lib/types'
import { addHours, bookableStarts, hhmm } from '@/lib/bookingUtils'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

const CATEGORIES: ShootCategory[] = ['CASUAL', 'WEDDING', 'PRE_WEDDING', 'MATERNITY', 'NEWBORN', 'BIRTHDAY', 'FAMILY', 'CORPORATE', 'EVENT', 'PRODUCT', 'PORTFOLIO', 'OTHER']

interface Props {
  open: boolean
  onClose: () => void
  onCreated: (booking: Booking) => void
}

export function NewBookingModal({ open, onClose, onCreated }: Props) {
  const push = useToastStore((s) => s.push)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({
    clientName: '', clientMobile: '', clientEmail: '',
    bookingDate: '', startTime: '10:00', endTime: '11:00',
    packageName: '', shootCategory: 'FAMILY' as ShootCategory,
    type: 'OFFLINE' as 'ONLINE' | 'OFFLINE' | 'WALK_IN',
    venue: '', locationType: 'INDOOR' as 'INDOOR' | 'OUTDOOR', notes: '', totalAmount: '', advanceAmount: '',
  })
  // Set when staff pick an existing client from the suggestions, so the booking is linked to that client.
  const [clientId, setClientId] = useState<string | undefined>(undefined)

  const [packages, setPackages] = useState<CatalogPackage[]>([])
  const [slots, setSlots] = useState<SlotAvailability[] | null>(null)
  const [slotsError, setSlotsError] = useState<string | null>(null)
  const [blocked, setBlocked] = useState<BlockedDate[]>([])
  const [packageId, setPackageId] = useState('')

  useEffect(() => {
    if (!open) return
    fetchActivePackages().then(setPackages).catch(() => setPackages([]))
    fetchBlockedDates().then(setBlocked).catch(() => setBlocked([]))
  }, [open])

  const chosenPackage = packages.find((p) => p.id === packageId)
  const hours = chosenPackage?.durationHours && chosenPackage.durationHours > 0 ? chosenPackage.durationHours : 1
  const closedReason = blocked.find((b) => b.date === form.bookingDate)?.reason

  // Live availability for the chosen day, so staff can't double-book a taken slot.
  useEffect(() => {
    if (!open || !form.bookingDate) { setSlots(null); return }
    let cancelled = false
    setSlots(null); setSlotsError(null)
    fetchSlotAvailability(form.bookingDate)
      .then((s) => { if (!cancelled) setSlots(s) })
      .catch((e) => { if (!cancelled) setSlotsError(e instanceof ApiError ? e.message : 'Could not check availability.') })
    return () => { cancelled = true }
  }, [open, form.bookingDate])

  const starts = useMemo(() => (slots ? bookableStarts(slots, hours) : new Set<string>()), [slots, hours])
  const dayClosed = slots !== null && slots.length > 0 && slots.every((s) => !s.available && s.closedReason)
  const chosenSlotTaken = slots !== null && !dayClosed && !starts.has(form.startTime)

  function pickPackage(id: string) {
    setPackageId(id)
    const p = packages.find((x) => x.id === id)
    if (!p) return
    setForm((f) => ({
      ...f,
      packageName: p.name,
      shootCategory: (CATEGORIES.includes(p.category as ShootCategory) ? p.category : 'OTHER') as ShootCategory,
      totalAmount: String(p.effectivePrice),
      endTime: addHours(f.startTime, p.durationHours && p.durationHours > 0 ? p.durationHours : 1),
    }))
  }

  function pickStart(t: string) {
    setForm((f) => ({ ...f, startTime: t, endTime: addHours(t, hours) }))
  }

  function set<K extends keyof typeof form>(key: K, value: typeof form[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  function reset() {
    setForm({ clientName: '', clientMobile: '', clientEmail: '', bookingDate: '', startTime: '10:00', endTime: '11:00', packageName: '', shootCategory: 'FAMILY', type: 'OFFLINE', venue: '', locationType: 'INDOOR', notes: '', totalAmount: '', advanceAmount: '' })
    setClientId(undefined)
    setError(null)
    setPackageId('')
    setSlots(null)
  }

  async function handleSubmit() {
    setError(null)
    if (dayClosed) { setError(`The studio is closed on this date${closedReason ? ` (${closedReason})` : ''}.`); return }
    if (chosenSlotTaken) { setError('That time overlaps an existing booking. Pick one of the free times shown.'); return }
    if (!form.clientName.trim() || !form.clientMobile.trim() || !form.bookingDate || !form.packageName.trim() || !form.totalAmount) {
      setError('Client name, mobile, date, package and total amount are required.')
      return
    }
    if (form.locationType === 'OUTDOOR' && !form.venue.trim()) { setError('Enter the venue / location for an outdoor booking.'); return }
    setSubmitting(true)
    try {
      const booking = await createBooking({
        clientId,
        locationType: form.locationType,
        clientName: form.clientName.trim(),
        clientMobile: form.clientMobile.trim(),
        clientEmail: form.clientEmail.trim() || undefined,
        bookingDate: form.bookingDate,
        startTime: form.startTime,
        endTime: form.endTime,
        packageId: packageId || undefined,
        packageName: form.packageName.trim(),
        shootCategory: form.shootCategory,
        type: form.type,
        venue: form.venue.trim() || undefined,
        notes: form.notes.trim() || undefined,
        totalAmount: Number(form.totalAmount),
        advanceAmount: form.advanceAmount ? Number(form.advanceAmount) : undefined,
      })
      push('Booking created')
      onCreated(booking)
      reset()
      onClose()
    } catch (err) {
      // A 409/slot-conflict from the backend's overlap check (see
      // BookingService) surfaces here with its real message rather than a
      // generic failure — this is the double-booking prevention from the
      // audit actually reaching the UI.
      setError(err instanceof ApiError ? err.message : 'Could not create the booking. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => { onClose(); reset() }}
      title="New booking"
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={() => { onClose(); reset() }}>Cancel</Button>
          <Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Creating…' : 'Create booking'}</Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <ClientCombobox
          value={form.clientName}
          onChange={(t) => { set('clientName', t); setClientId(undefined) }}
          onSelect={(c) => { setClientId(c.id); setForm((f) => ({ ...f, clientMobile: c.mobile || f.clientMobile, clientEmail: c.email || f.clientEmail })) }}
        />
        <Input label="Client mobile" value={form.clientMobile} onChange={(e) => set('clientMobile', e.target.value)} placeholder="9876543210" />
        <Input label="Client email (optional)" type="email" value={form.clientEmail} onChange={(e) => set('clientEmail', e.target.value)} />
        <Select label="Shoot location" value={form.locationType} onChange={(e) => set('locationType', e.target.value as 'INDOOR' | 'OUTDOOR')}>
          <option value="INDOOR">Indoor (in the studio)</option>
          <option value="OUTDOOR">Outdoor (at a venue)</option>
        </Select>
        <Select label="Shoot category" value={form.shootCategory} onChange={(e) => set('shootCategory', e.target.value as ShootCategory)}>
          {CATEGORIES.map((c) => <option key={c} value={c}>{c.replace(/_/g, ' ')}</option>)}
        </Select>
        <Select label="Package (fills price & duration)" value={packageId} onChange={(e) => pickPackage(e.target.value)}>
          <option value="">Custom / not listed</option>
          {packages.map((p) => <option key={p.id} value={p.id}>{p.name} — ₹{p.effectivePrice}</option>)}
        </Select>
        <Input label="Package / service name" value={form.packageName} onChange={(e) => set('packageName', e.target.value)} placeholder="e.g. Signature Portrait" />
        <Select label="Booking source" value={form.type} onChange={(e) => set('type', e.target.value as typeof form.type)}>
          <option value="OFFLINE">Staff-entered</option>
          <option value="WALK_IN">Walk-in</option>
          <option value="ONLINE">Online request</option>
        </Select>
        <Input label="Date" type="date" value={form.bookingDate} onChange={(e) => set('bookingDate', e.target.value)} />
        <div className="grid grid-cols-2 gap-2">
          <Input label="Start time" type="time" value={form.startTime} onChange={(e) => set('startTime', e.target.value)} />
          <Input label="End time" type="time" value={form.endTime} onChange={(e) => set('endTime', e.target.value)} />
        </div>
        <div className="sm:col-span-2" aria-live="polite">
          {form.bookingDate && slotsError && <p className="text-sm text-danger">{slotsError}</p>}
          {form.bookingDate && !slots && !slotsError && <p className="text-xs text-ink-faint">Checking availability…</p>}
          {dayClosed && <p role="alert" className="rounded-[var(--radius-md)] bg-danger-bg px-3 py-2 text-sm text-danger">Studio closed on this date{closedReason ? `: ${closedReason}` : ''}.</p>}
          {slots && !dayClosed && (
            <div>
              <p className="mb-1.5 text-xs text-ink-muted">Free start times for a {hours}h session (tap to use):</p>
              <div className="flex flex-wrap gap-1.5">
                {slots.map((sl) => {
                  const t = hhmm(sl.startTime)
                  const ok = starts.has(t)
                  return (
                    <button key={t} type="button" disabled={!ok} onClick={() => pickStart(t)}
                      className={`rounded-[var(--radius-sm)] border px-2.5 py-1 text-xs transition-colors ${ok ? 'border-border text-ink hover:border-accent' : 'cursor-not-allowed border-transparent bg-surface-2 text-ink-faint/60 line-through'} ${form.startTime === t ? 'border-accent bg-accent text-accent-ink' : ''}`}>
                      {t}
                    </button>
                  )
                })}
              </div>
              {chosenSlotTaken && <p className="mt-2 text-xs text-warning">The selected start time ({form.startTime}) is not free.</p>}
            </div>
          )}
        </div>
        <Input label={form.locationType === 'OUTDOOR' ? 'Venue / location (required)' : 'Venue / location (optional)'} value={form.venue} onChange={(e) => set('venue', e.target.value)} />
        <Input label="Total amount (₹)" type="number" min="0" value={form.totalAmount} onChange={(e) => set('totalAmount', e.target.value)} />
        <Input label="Advance received (₹, optional)" type="number" min="0" value={form.advanceAmount} onChange={(e) => set('advanceAmount', e.target.value)} />
      </div>
      <div className="mt-4">
        <Textarea label="Notes (optional)" value={form.notes} onChange={(e) => set('notes', e.target.value)} rows={3} />
      </div>
      {error && <p role="alert" className="mt-4 rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
    </Modal>
  )
}
