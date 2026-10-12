import { useEffect, useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { Input, Select } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { createInvoice, fetchPackages, fetchProducts, type InvoiceItemInput } from '@/lib/api'
import type { Invoice, CatalogPackage, CatalogProduct } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'
import { formatCurrency } from '@/lib/utils'
import { ApiError } from '@/lib/apiClient'

interface Props {
  open: boolean
  onClose: () => void
  onCreated: (invoice: Invoice) => void
}

interface LineItem extends InvoiceItemInput {
  key: string
}

function emptyLine(): LineItem {
  return { key: crypto.randomUUID(), description: '', quantity: 1, unitPrice: 0, discountPercent: 0 }
}

export function NewInvoiceModal({ open, onClose, onCreated }: Props) {
  const push = useToastStore((s) => s.push)
  const [clientName, setClientName] = useState('')
  const [clientMobile, setClientMobile] = useState('')
  const [invoiceType, setInvoiceType] = useState<'ONLINE' | 'OFFLINE'>('OFFLINE')
  const [items, setItems] = useState<LineItem[]>([emptyLine()])
  const [discountAmount, setDiscountAmount] = useState('')
  const [taxType, setTaxType] = useState<'INTRA_STATE' | 'INTER_STATE' | 'EXEMPT'>('INTRA_STATE')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [packages, setPackages] = useState<CatalogPackage[]>([])
  const [products, setProducts] = useState<CatalogProduct[]>([])
  const [catalogPick, setCatalogPick] = useState('')

  useEffect(() => {
    if (!open) return
    fetchPackages().then(setPackages)
    fetchProducts().then(setProducts)
  }, [open])

  function addFromCatalog(value: string) {
    if (!value) return
    const [kind, id] = value.split(':')
    if (kind === 'pkg') {
      const p = packages.find((x) => x.id === id)
      if (p) setItems((prev) => [...prev, { key: crypto.randomUUID(), description: p.name, quantity: 1, unitPrice: p.effectivePrice, discountPercent: 0 }])
    } else {
      const p = products.find((x) => x.id === id)
      if (p) setItems((prev) => [...prev, { key: crypto.randomUUID(), description: p.name, quantity: 1, unitPrice: p.price, discountPercent: 0 }])
    }
    setCatalogPick('')
  }

  function updateItem(key: string, patch: Partial<LineItem>) {
    setItems((prev) => prev.map((it) => (it.key === key ? { ...it, ...patch } : it)))
  }

  function removeItem(key: string) {
    setItems((prev) => (prev.length > 1 ? prev.filter((it) => it.key !== key) : prev))
  }

  const lineTotal = (it: LineItem) => it.quantity * it.unitPrice * (1 - (it.discountPercent ?? 0) / 100)
  const subtotal = items.reduce((sum, it) => sum + lineTotal(it), 0)
  const taxableAmount = Math.max(0, subtotal - (Number(discountAmount) || 0))
  // Mirrors BillingService#applyTaxType on the backend: INTRA_STATE keeps
  // the entity's 9%+9% CGST/SGST default (18% total), INTER_STATE charges
  // the same 18% as pure IGST, EXEMPT charges none. Shown here so what
  // staff see while building the invoice matches what the client will
  // actually be charged.
  const estimatedTax = taxType === 'EXEMPT' ? 0 : taxableAmount * 0.18
  const total = taxableAmount + estimatedTax

  function reset() {
    setClientName(''); setClientMobile(''); setInvoiceType('OFFLINE')
    setItems([emptyLine()]); setDiscountAmount(''); setTaxType('INTRA_STATE'); setError(null)
  }

  async function handleSubmit() {
    setError(null)
    const validItems = items.filter((it) => it.description.trim() && it.quantity > 0 && it.unitPrice >= 0)
    if (!clientName.trim()) { setError('Client name is required.'); return }
    if (validItems.length === 0) { setError('Add at least one line item with a description, quantity and price.'); return }

    setSubmitting(true)
    try {
      const invoice = await createInvoice({
        invoiceType,
        clientName: clientName.trim(),
        clientMobile: clientMobile.trim() || undefined,
        discountAmount: Number(discountAmount) || undefined,
        taxType,
        items: validItems.map(({ key: _key, ...rest }) => rest),
      })
      push(`Invoice ${invoice.invoiceNumber} created`)
      onCreated(invoice)
      reset()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the invoice. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => { onClose(); reset() }}
      title="New invoice"
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={() => { onClose(); reset() }}>Cancel</Button>
          <Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Creating…' : `Create invoice · ${formatCurrency(total)}`}</Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Input label="Client name" value={clientName} onChange={(e) => setClientName(e.target.value)} />
        <Input label="Client mobile (optional)" value={clientMobile} onChange={(e) => setClientMobile(e.target.value)} />
        <Select label="Type" value={invoiceType} onChange={(e) => setInvoiceType(e.target.value as typeof invoiceType)}>
          <option value="OFFLINE">In-studio / walk-in</option>
          <option value="ONLINE">Online order</option>
        </Select>
      </div>

      <div className="mt-5">
        <p className="mb-2 text-sm font-medium text-ink-muted">Items</p>
        {(packages.length > 0 || products.length > 0) && (
          <Select value={catalogPick} onChange={(e) => addFromCatalog(e.target.value)} className="mb-3">
            <option value="">+ Add from catalog…</option>
            {packages.length > 0 && (
              <optgroup label="Packages">
                {packages.map((p) => <option key={p.id} value={`pkg:${p.id}`}>{p.name} — {formatCurrency(p.effectivePrice)}</option>)}
              </optgroup>
            )}
            {products.length > 0 && (
              <optgroup label="Products">
                {products.map((p) => <option key={p.id} value={`prod:${p.id}`}>{p.name} — {formatCurrency(p.price)}</option>)}
              </optgroup>
            )}
          </Select>
        )}
        <div className="space-y-2">
          {items.map((it) => (
            <div key={it.key} className="grid grid-cols-12 items-end gap-2">
              <div className="col-span-5"><Input placeholder="e.g. 4x6 photo print" value={it.description} onChange={(e) => updateItem(it.key, { description: e.target.value })} /></div>
              <div className="col-span-2"><Input type="number" min="1" placeholder="Qty" value={it.quantity} onChange={(e) => updateItem(it.key, { quantity: Number(e.target.value) })} /></div>
              <div className="col-span-2"><Input type="number" min="0" placeholder="Rate ₹" value={it.unitPrice} onChange={(e) => updateItem(it.key, { unitPrice: Number(e.target.value) })} /></div>
              <div className="col-span-2 flex h-10 items-center justify-end text-sm text-ink">{formatCurrency(lineTotal(it))}</div>
              <div className="col-span-1 flex justify-end">
                <button type="button" onClick={() => removeItem(it.key)} className="rounded-md p-2 text-ink-faint hover:bg-surface-2 hover:text-danger" aria-label="Remove item">
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
        <Button variant="ghost" size="sm" className="mt-2" onClick={() => setItems((prev) => [...prev, emptyLine()])}>
          <Plus className="h-3.5 w-3.5" /> Add line
        </Button>
      </div>

      <div className="mt-4 flex flex-col items-end gap-2 border-t border-border pt-4">
        <div className="flex w-56 items-center justify-between text-sm">
          <span className="text-ink-muted">Subtotal</span>
          <span className="text-ink">{formatCurrency(subtotal)}</span>
        </div>
        <div className="flex w-56 items-center justify-between gap-2 text-sm">
          <span className="text-ink-muted">Discount (₹)</span>
          <Input type="number" min="0" value={discountAmount} onChange={(e) => setDiscountAmount(e.target.value)} className="h-8 w-24 text-right" />
        </div>
        <div className="flex w-56 items-center justify-between gap-2 text-sm">
          <span className="text-ink-muted">Tax type</span>
          <Select value={taxType} onChange={(e) => setTaxType(e.target.value as typeof taxType)} className="h-8 w-32 text-right">
            <option value="INTRA_STATE">Intra-state</option>
            <option value="INTER_STATE">Inter-state</option>
            <option value="EXEMPT">Exempt</option>
          </Select>
        </div>
        <div className="flex w-56 items-center justify-between text-sm">
          <span className="text-ink-muted">{taxType === 'INTER_STATE' ? 'IGST (18%)' : taxType === 'EXEMPT' ? 'Tax' : 'GST (18%)'}</span>
          <span className="text-ink">{formatCurrency(estimatedTax)}</span>
        </div>
        <div className="flex w-56 items-center justify-between text-base font-medium">
          <span className="text-ink">Total</span>
          <span className="text-ink">{formatCurrency(total)}</span>
        </div>
      </div>
      <p className="mt-2 text-right text-xs text-ink-faint">GST is applied automatically and can't be changed per invoice yet.</p>

      {error && <p role="alert" className="mt-4 rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
    </Modal>
  )
}
