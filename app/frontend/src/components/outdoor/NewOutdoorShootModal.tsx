import { useState } from 'react'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ClientCombobox } from '@/components/shared/ClientCombobox'
import { createOutdoorShoot, type OutdoorShoot } from '@/lib/api'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

const CATEGORIES = ['WEDDING', 'PRE_WEDDING', 'CORPORATE_ONSITE', 'EVENT', 'DESTINATION', 'OTHER']

interface Props {
  open: boolean
  onClose: () => void
  onCreated: (shoot: OutdoorShoot) => void
}

export function NewOutdoorShootModal({ open, onClose, onCreated }: Props) {
  const push = useToastStore((s) => s.push)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({
    clientName: '', clientMobile: '', category: 'WEDDING', packageName: '',
    shootDate: '', startTime: '10:00', endTime: '',
    venueName: '', venueAddress: '', venueCity: '',
    travelDistanceKm: '', travelCost: '', weatherDependent: true, backupPlanNotes: '',
    totalAmount: '', advanceAmount: '',
  })

  function set<K extends keyof typeof form>(key: K, value: typeof form[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }
  function reset() {
    setForm({ clientName: '', clientMobile: '', category: 'WEDDING', packageName: '', shootDate: '', startTime: '10:00', endTime: '', venueName: '', venueAddress: '', venueCity: '', travelDistanceKm: '', travelCost: '', weatherDependent: true, backupPlanNotes: '', totalAmount: '', advanceAmount: '' })
    setError(null)
  }

  async function handleSubmit() {
    setError(null)
    if (!form.clientName.trim() || !form.shootDate || !form.venueCity.trim()) { setError('Client name, date and venue city are required.'); return }
    setSubmitting(true)
    try {
      const shoot = await createOutdoorShoot({
        clientName: form.clientName.trim(), clientMobile: form.clientMobile.trim() || undefined,
        category: form.category, packageName: form.packageName.trim() || undefined,
        shootDate: form.shootDate, startTime: form.startTime, endTime: form.endTime || undefined,
        venueName: form.venueName.trim() || undefined, venueAddress: form.venueAddress.trim() || undefined,
        venueCity: form.venueCity.trim(), travelDistanceKm: form.travelDistanceKm ? Number(form.travelDistanceKm) : undefined,
        travelCost: form.travelCost ? Number(form.travelCost) : undefined, weatherDependent: form.weatherDependent,
        backupPlanNotes: form.backupPlanNotes.trim() || undefined,
        totalAmount: form.totalAmount ? Number(form.totalAmount) : undefined,
        advanceAmount: form.advanceAmount ? Number(form.advanceAmount) : undefined,
      })
      push('Outdoor shoot scheduled')
      onCreated(shoot)
      reset()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not schedule the shoot. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => { onClose(); reset() }}
      title="New outdoor shoot"
      size="lg"
      footer={<>
        <Button variant="outline" onClick={() => { onClose(); reset() }}>Cancel</Button>
        <Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Scheduling…' : 'Schedule shoot'}</Button>
      </>}
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <ClientCombobox value={form.clientName} onChange={(t) => set('clientName', t)} onSelect={(c) => setForm((f) => ({ ...f, clientMobile: c.mobile || f.clientMobile }))} />
        <Input label="Client mobile (optional)" value={form.clientMobile} onChange={(e) => set('clientMobile', e.target.value)} />
        <Select label="Category" value={form.category} onChange={(e) => set('category', e.target.value)}>
          {CATEGORIES.map((c) => <option key={c} value={c}>{c.replace('_', ' ')}</option>)}
        </Select>
        <Input label="Package / service (optional)" value={form.packageName} onChange={(e) => set('packageName', e.target.value)} />
        <Input label="Date" type="date" value={form.shootDate} onChange={(e) => set('shootDate', e.target.value)} />
        <div className="grid grid-cols-2 gap-2">
          <Input label="Start" type="time" value={form.startTime} onChange={(e) => set('startTime', e.target.value)} />
          <Input label="End (optional)" type="time" value={form.endTime} onChange={(e) => set('endTime', e.target.value)} />
        </div>
        <Input label="Venue name (optional)" value={form.venueName} onChange={(e) => set('venueName', e.target.value)} />
        <Input label="Venue city" value={form.venueCity} onChange={(e) => set('venueCity', e.target.value)} />
        <Input label="Venue address (optional)" value={form.venueAddress} onChange={(e) => set('venueAddress', e.target.value)} className="sm:col-span-2" />
        <Input label="Travel distance (km, optional)" type="number" min="0" value={form.travelDistanceKm} onChange={(e) => set('travelDistanceKm', e.target.value)} />
        <Input label="Travel cost (₹, optional)" type="number" min="0" value={form.travelCost} onChange={(e) => set('travelCost', e.target.value)} />
        <Input label="Total amount (₹, optional)" type="number" min="0" value={form.totalAmount} onChange={(e) => set('totalAmount', e.target.value)} />
        <Input label="Advance received (₹, optional)" type="number" min="0" value={form.advanceAmount} onChange={(e) => set('advanceAmount', e.target.value)} />
      </div>
      <div className="mt-4">
        <label className="flex items-center gap-2 text-sm text-ink-muted">
          <input type="checkbox" checked={form.weatherDependent} onChange={(e) => set('weatherDependent', e.target.checked)} className="h-4 w-4 rounded border-border" />
          Outcome depends on weather (a backup plan is recommended)
        </label>
      </div>
      {form.weatherDependent && (
        <div className="mt-3">
          <Textarea label="Backup plan (optional)" value={form.backupPlanNotes} onChange={(e) => set('backupPlanNotes', e.target.value)} rows={2} placeholder="e.g. Move indoors to the studio's Room A if it rains" />
        </div>
      )}
      {error && <p role="alert" className="mt-4 rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
    </Modal>
  )
}
