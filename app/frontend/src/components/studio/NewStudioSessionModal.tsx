import { useState } from 'react'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ClientCombobox } from '@/components/shared/ClientCombobox'
import { createStudioSession, type StudioSession } from '@/lib/api'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

const ROOMS = ['Room A — main backdrop', 'Room B — natural light', 'Room C — product/tabletop']

interface Props {
  open: boolean
  onClose: () => void
  onCreated: (session: StudioSession) => void
}

export function NewStudioSessionModal({ open, onClose, onCreated }: Props) {
  const push = useToastStore((s) => s.push)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({
    clientName: '', clientMobile: '', packageName: '', sessionDate: '', startTime: '10:00', endTime: '11:00',
    studioRoom: ROOMS[0], backdropNotes: '', propsRequired: '', totalAmount: '', advanceAmount: '',
  })

  function set<K extends keyof typeof form>(key: K, value: typeof form[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }
  function reset() {
    setForm({ clientName: '', clientMobile: '', packageName: '', sessionDate: '', startTime: '10:00', endTime: '11:00', studioRoom: ROOMS[0], backdropNotes: '', propsRequired: '', totalAmount: '', advanceAmount: '' })
    setError(null)
  }

  async function handleSubmit() {
    setError(null)
    if (!form.clientName.trim() || !form.sessionDate) { setError('Client name and session date are required.'); return }
    setSubmitting(true)
    try {
      const session = await createStudioSession({
        clientName: form.clientName.trim(), clientMobile: form.clientMobile.trim() || undefined,
        packageName: form.packageName.trim() || undefined, sessionDate: form.sessionDate,
        startTime: form.startTime, endTime: form.endTime, studioRoom: form.studioRoom,
        backdropNotes: form.backdropNotes.trim() || undefined, propsRequired: form.propsRequired.trim() || undefined,
        totalAmount: form.totalAmount ? Number(form.totalAmount) : undefined,
        advanceAmount: form.advanceAmount ? Number(form.advanceAmount) : undefined,
      })
      push('Studio session booked')
      onCreated(session)
      reset()
      onClose()
    } catch (err) {
      // A room/slot conflict from the backend's availability check surfaces
      // here with its real message — the same double-booking protection
      // pattern used for the generic booking calendar.
      setError(err instanceof ApiError ? err.message : 'Could not book the session. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => { onClose(); reset() }}
      title="New studio session"
      size="lg"
      footer={<>
        <Button variant="outline" onClick={() => { onClose(); reset() }}>Cancel</Button>
        <Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Booking…' : 'Book session'}</Button>
      </>}
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <ClientCombobox value={form.clientName} onChange={(t) => set('clientName', t)} onSelect={(c) => setForm((f) => ({ ...f, clientMobile: c.mobile || f.clientMobile }))} />
        <Input label="Client mobile (optional)" value={form.clientMobile} onChange={(e) => set('clientMobile', e.target.value)} />
        <Input label="Package / service (optional)" value={form.packageName} onChange={(e) => set('packageName', e.target.value)} placeholder="e.g. Passport photo, Product shoot" />
        <Select label="Studio room" value={form.studioRoom} onChange={(e) => set('studioRoom', e.target.value)}>
          {ROOMS.map((r) => <option key={r} value={r}>{r}</option>)}
        </Select>
        <Input label="Date" type="date" value={form.sessionDate} onChange={(e) => set('sessionDate', e.target.value)} />
        <div className="grid grid-cols-2 gap-2">
          <Input label="Start" type="time" value={form.startTime} onChange={(e) => set('startTime', e.target.value)} />
          <Input label="End" type="time" value={form.endTime} onChange={(e) => set('endTime', e.target.value)} />
        </div>
        <Input label="Total amount (₹, optional)" type="number" min="0" value={form.totalAmount} onChange={(e) => set('totalAmount', e.target.value)} />
        <Input label="Advance received (₹, optional)" type="number" min="0" value={form.advanceAmount} onChange={(e) => set('advanceAmount', e.target.value)} />
      </div>
      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Textarea label="Backdrop / lighting notes (optional)" value={form.backdropNotes} onChange={(e) => set('backdropNotes', e.target.value)} rows={2} />
        <Textarea label="Props required (optional)" value={form.propsRequired} onChange={(e) => set('propsRequired', e.target.value)} rows={2} />
      </div>
      {error && <p role="alert" className="mt-4 rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
    </Modal>
  )
}
