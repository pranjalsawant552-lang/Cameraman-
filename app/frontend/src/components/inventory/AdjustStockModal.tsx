import { useState } from 'react'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { adjustStock, type StockAdjustmentType } from '@/lib/api'
import type { InventoryItem } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

interface Props {
  item: InventoryItem | null
  onClose: () => void
  onAdjusted: (updated: InventoryItem) => void
}

export function AdjustStockModal({ item, onClose, onAdjusted }: Props) {
  const push = useToastStore((s) => s.push)
  const [type, setType] = useState<StockAdjustmentType>('PURCHASE')
  const [quantity, setQuantity] = useState('')
  const [notes, setNotes] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!item) return null

  async function handleSubmit() {
    setError(null)
    const qty = Number(quantity)
    if (!qty || qty <= 0) { setError('Enter a quantity greater than 0.'); return }
    if ((type === 'SALE' || type === 'ADJUSTMENT_OUT' || type === 'DAMAGE') && qty > item!.quantityOnHand) {
      setError(`Only ${item!.quantityOnHand} currently in stock.`); return
    }
    setSubmitting(true)
    try {
      const updated = await adjustStock(item!.id, type, qty, notes.trim() || undefined)
      push(`Stock ${type === 'PURCHASE' || type === 'ADJUSTMENT_IN' || type === 'RETURN' ? 'added' : 'updated'} for ${item!.name}`)
      onAdjusted(updated)
      setQuantity(''); setNotes('')
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update stock. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={!!item}
      onClose={onClose}
      title={`Adjust stock · ${item.name}`}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Saving…' : 'Save'}</Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between rounded-[var(--radius-md)] bg-surface-2 px-3.5 py-2.5 text-sm">
          <span className="text-ink-muted">Currently on hand</span>
          <span className="font-medium text-ink">{item.quantityOnHand}</span>
        </div>
        <Select label="Movement type" value={type} onChange={(e) => setType(e.target.value as StockAdjustmentType)}>
          <option value="PURCHASE">Stock in (purchase / restock)</option>
          <option value="SALE">Stock out (sold / used)</option>
          <option value="RETURN">Returned to stock</option>
          <option value="DAMAGE">Damaged / written off</option>
          <option value="ADJUSTMENT_IN">Count correction: add</option>
          <option value="ADJUSTMENT_OUT">Count correction: remove</option>
        </Select>
        <Input label="Quantity" type="number" min="1" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
        <Textarea label="Notes (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} placeholder="e.g. Purchased from XYZ Suppliers" />
        {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
      </div>
    </Modal>
  )
}
