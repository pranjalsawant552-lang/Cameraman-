import { useState } from 'react'
import { Input, Textarea } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Switch } from '@/components/ui/primitives'
import { createGallery } from '@/lib/api'
import type { Gallery } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

interface Props {
  open: boolean
  onClose: () => void
  onCreated: (gallery: Gallery) => void
}

export function NewGalleryModal({ open, onClose, onCreated }: Props) {
  const push = useToastStore((s) => s.push)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({
    name: '', clientName: '', bookingId: '', description: '',
    selectionLimit: '100', downloadAllowed: false, watermarkEnabled: true,
  })

  function set<K extends keyof typeof form>(key: K, value: typeof form[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }
  function reset() {
    setForm({ name: '', clientName: '', bookingId: '', description: '', selectionLimit: '100', downloadAllowed: false, watermarkEnabled: true })
    setError(null)
  }

  async function handleSubmit() {
    setError(null)
    if (!form.name.trim() || !form.clientName.trim()) { setError('Gallery name and client name are required.'); return }
    setSubmitting(true)
    try {
      const gallery = await createGallery({
        name: form.name.trim(),
        clientName: form.clientName.trim(),
        bookingId: form.bookingId.trim() || undefined,
        description: form.description.trim() || undefined,
        selectionLimit: form.selectionLimit ? Number(form.selectionLimit) : undefined,
        downloadAllowed: form.downloadAllowed,
        watermarkEnabled: form.watermarkEnabled,
      })
      push('Gallery created as a draft')
      onCreated(gallery)
      reset()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the gallery. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => { onClose(); reset() }}
      title="New gallery"
      footer={<>
        <Button variant="outline" onClick={() => { onClose(); reset() }}>Cancel</Button>
        <Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Creating…' : 'Create draft gallery'}</Button>
      </>}
    >
      <div className="space-y-4">
        <Input label="Gallery name" value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="e.g. Ananya & Rohit — Wedding" />
        <Input label="Client name" value={form.clientName} onChange={(e) => set('clientName', e.target.value)} />
        <Input label="Booking ID (optional)" value={form.bookingId} onChange={(e) => set('bookingId', e.target.value)} placeholder="Link this gallery to a booking" />
        <Input label="Selection limit (photos the client can pick)" type="number" min="0" value={form.selectionLimit} onChange={(e) => set('selectionLimit', e.target.value)} />
        <div className="flex items-center justify-between rounded-[var(--radius-md)] border border-border px-3.5 py-2.5">
          <div><p className="text-sm text-ink">Watermark previews</p><p className="text-xs text-ink-faint">Protects originals until final delivery</p></div>
          <Switch checked={form.watermarkEnabled} onChange={(v) => set('watermarkEnabled', v)} />
        </div>
        <div className="flex items-center justify-between rounded-[var(--radius-md)] border border-border px-3.5 py-2.5">
          <div><p className="text-sm text-ink">Allow client downloads</p><p className="text-xs text-ink-faint">Enable once final photos are ready</p></div>
          <Switch checked={form.downloadAllowed} onChange={(v) => set('downloadAllowed', v)} />
        </div>
        <Textarea label="Notes (optional)" value={form.description} onChange={(e) => set('description', e.target.value)} rows={2} />
        {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
      </div>
    </Modal>
  )
}
