import { useState } from 'react'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { createPackage } from '@/lib/api'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

const CATEGORIES = ['CASUAL', 'WEDDING', 'PRE_WEDDING', 'BIRTHDAY', 'CORPORATE', 'MATERNITY', 'PORTFOLIO', 'NEWBORN', 'EVENT', 'FAMILY', 'PRODUCT', 'OTHER']

interface Props {
  open: boolean
  onClose: () => void
  onCreated: () => void
}

export function NewPackageModal({ open, onClose, onCreated }: Props) {
  const push = useToastStore((s) => s.push)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({
    name: '', description: '', category: 'CASUAL', basePrice: '', discountedPrice: '',
    durationHours: '', photoCount: '', deliveryDays: '',
  })

  function set<K extends keyof typeof form>(key: K, value: typeof form[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }
  function reset() {
    setForm({ name: '', description: '', category: 'CASUAL', basePrice: '', discountedPrice: '', durationHours: '', photoCount: '', deliveryDays: '' })
    setError(null)
  }

  async function handleSubmit() {
    setError(null)
    if (!form.name.trim() || !form.basePrice) { setError('Name and base price are required.'); return }
    setSubmitting(true)
    try {
      await createPackage({
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        category: form.category,
        basePrice: Number(form.basePrice),
        discountedPrice: form.discountedPrice ? Number(form.discountedPrice) : undefined,
        durationHours: form.durationHours ? Number(form.durationHours) : undefined,
        photoCount: form.photoCount ? Number(form.photoCount) : undefined,
        deliveryDays: form.deliveryDays ? Number(form.deliveryDays) : undefined,
      })
      push('Package created')
      onCreated()
      reset()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the package. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => { onClose(); reset() }}
      title="New package"
      size="lg"
      footer={<>
        <Button variant="outline" onClick={() => { onClose(); reset() }}>Cancel</Button>
        <Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Creating…' : 'Create package'}</Button>
      </>}
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input label="Name" value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="e.g. Signature Portrait" />
        <Select label="Category" value={form.category} onChange={(e) => set('category', e.target.value)}>
          {CATEGORIES.map((c) => <option key={c} value={c}>{c.replace(/_/g, ' ')}</option>)}
        </Select>
        <Input label="Base price (₹)" type="number" min="0" value={form.basePrice} onChange={(e) => set('basePrice', e.target.value)} />
        <Input label="Discounted price (₹, optional)" type="number" min="0" value={form.discountedPrice} onChange={(e) => set('discountedPrice', e.target.value)} />
        <Input label="Duration (hours, optional)" type="number" min="0" value={form.durationHours} onChange={(e) => set('durationHours', e.target.value)} />
        <Input label="Photo count (optional)" type="number" min="0" value={form.photoCount} onChange={(e) => set('photoCount', e.target.value)} />
        <Input label="Delivery days (optional)" type="number" min="0" value={form.deliveryDays} onChange={(e) => set('deliveryDays', e.target.value)} />
      </div>
      <div className="mt-4"><Textarea label="Description (optional)" value={form.description} onChange={(e) => set('description', e.target.value)} rows={3} /></div>
      {error && <p role="alert" className="mt-4 rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
    </Modal>
  )
}
