import { useEffect, useState } from 'react'
import { Input, Select } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { createInventoryItem, updateInventoryItem } from '@/lib/api'
import type { InventoryItem } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

const CATEGORY_OPTIONS: { value: string; label: string }[] = [
  { value: 'ALBUM', label: 'Albums' }, { value: 'FRAME', label: 'Frames' }, { value: 'CANVAS_PRINT', label: 'Canvas prints' },
  { value: 'USB_DRIVE', label: 'USB drives' }, { value: 'PHOTO_PAPER', label: 'Photo paper' }, { value: 'INK_CARTRIDGE', label: 'Ink cartridges' },
  { value: 'CAMERA_ACCESSORY', label: 'Camera accessories' }, { value: 'LIGHTING_EQUIPMENT', label: 'Lighting equipment' },
  { value: 'BACKDROP', label: 'Backdrops' }, { value: 'PROPS', label: 'Props' }, { value: 'PACKAGING', label: 'Packaging' }, { value: 'OTHER', label: 'Other' },
]

interface Props {
  open: boolean
  editing: InventoryItem | null
  onClose: () => void
  onSaved: () => void
}

export function InventoryItemModal({ open, editing, onClose, onSaved }: Props) {
  const push = useToastStore((s) => s.push)
  const [form, setForm] = useState({ name: '', sku: '', category: 'OTHER', unit: '', initialQuantity: '0', reorderLevel: '5', sellingPrice: '' })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (editing) {
      setForm({ name: editing.name, sku: editing.sku, category: CATEGORY_OPTIONS.some((o) => o.value === editing.category) ? editing.category : 'OTHER', unit: '', initialQuantity: String(editing.quantityOnHand), reorderLevel: String(editing.reorderLevel), sellingPrice: String(editing.unitPrice) })
    } else {
      setForm({ name: '', sku: '', category: 'OTHER', unit: '', initialQuantity: '0', reorderLevel: '5', sellingPrice: '' })
    }
    setError(null)
  }, [editing, open])

  async function handleSubmit() {
    setError(null)
    if (!form.name.trim() || !form.sellingPrice) { setError('Name and selling price are required.'); return }
    if (!editing && !form.sku.trim()) { setError('SKU is required for a new item.'); return }
    setSubmitting(true)
    try {
      if (editing) {
        await updateInventoryItem(editing.id, { name: form.name.trim(), category: form.category || undefined, reorderLevel: Number(form.reorderLevel), sellingPrice: Number(form.sellingPrice) })
        push('Item updated')
      } else {
        await createInventoryItem({
          name: form.name.trim(), sku: form.sku.trim(), category: form.category || 'OTHER',
          unit: form.unit.trim() || undefined, initialQuantity: Number(form.initialQuantity) || 0,
          reorderLevel: Number(form.reorderLevel) || 0, sellingPrice: Number(form.sellingPrice),
        })
        push('Item added to inventory')
      }
      onSaved()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save this item. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={editing ? `Edit ${editing.name}` : 'New inventory item'}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Saving…' : 'Save'}</Button></>}
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input label="Name" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} className="sm:col-span-2" />
        <Input label="SKU" value={form.sku} onChange={(e) => setForm((f) => ({ ...f, sku: e.target.value }))} disabled={!!editing} />
        <Select label="Category" value={form.category} onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}>
          {CATEGORY_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </Select>
        {!editing && <Input label="Unit (optional)" value={form.unit} onChange={(e) => setForm((f) => ({ ...f, unit: e.target.value }))} placeholder="e.g. sheet, box" />}
        {!editing && <Input label="Starting quantity" type="number" min="0" value={form.initialQuantity} onChange={(e) => setForm((f) => ({ ...f, initialQuantity: e.target.value }))} />}
        <Input label="Reorder level" type="number" min="0" value={form.reorderLevel} onChange={(e) => setForm((f) => ({ ...f, reorderLevel: e.target.value }))} />
        <Input label="Selling price (₹)" type="number" min="0" value={form.sellingPrice} onChange={(e) => setForm((f) => ({ ...f, sellingPrice: e.target.value }))} />
      </div>
      {error && <p role="alert" className="mt-4 rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
    </Modal>
  )
}
