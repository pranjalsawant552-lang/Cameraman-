import { useState } from 'react'
import { Input, Select } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { recordManualPayment } from '@/lib/api'
import type { Invoice } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

interface Props {
  invoice: Invoice | null
  onClose: () => void
  onRecorded: () => void
}

export function RecordPaymentModal({ invoice, onClose, onRecorded }: Props) {
  const push = useToastStore((s) => s.push)
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState('CASH')
  const [transactionId, setTransactionId] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!invoice) return null

  async function handleSubmit() {
    setError(null)
    const value = Number(amount)
    if (!value || value <= 0) { setError('Enter a payment amount greater than 0.'); return }
    if (value > invoice!.balanceDue) { setError(`Amount can't exceed the balance due (${formatCurrency(invoice!.balanceDue)}).`); return }

    setSubmitting(true)
    try {
      // Previously called PUT /billing/invoices/{id}/payment directly, which
      // updates the invoice but never created a row in the Payments module's
      // own ledger — so a payment recorded here would never show up in the
      // admin Payments list or exports, only an online (Razorpay) payment
      // would. Routed through the same /payments/manual endpoint the
      // Payments module uses, which updates the invoice via the identical
      // billingService.recordPayment() call underneath, so nothing about
      // invoice totals changes — this only makes the record visible in one
      // consistent place.
      await recordManualPayment({
        invoiceId: invoice!.id,
        clientName: invoice!.clientName,
        amount: value,
        paymentMethod: method as 'CASH' | 'UPI' | 'CARD' | 'NET_BANKING' | 'BANK_TRANSFER' | 'CHEQUE',
        paymentType: value >= invoice!.balanceDue ? 'FULL' : 'PARTIAL',
        transactionId: transactionId.trim() || undefined,
      })
      push('Payment recorded')
      setAmount(''); setTransactionId('')
      onRecorded()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not record the payment. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={!!invoice}
      onClose={onClose}
      title={`Record payment · ${invoice.invoiceNumber}`}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Recording…' : 'Record payment'}</Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between rounded-[var(--radius-md)] bg-surface-2 px-3.5 py-2.5 text-sm">
          <span className="text-ink-muted">Balance due</span>
          <span className="font-medium text-warning">{formatCurrency(invoice.balanceDue)}</span>
        </div>
        <Input label="Amount received (₹)" type="number" min="0.01" max={invoice.balanceDue} value={amount} onChange={(e) => setAmount(e.target.value)} />
        <Select label="Payment method" value={method} onChange={(e) => setMethod(e.target.value)}>
          <option value="CASH">Cash</option>
          <option value="UPI">UPI</option>
          <option value="CARD">Card</option>
          <option value="BANK_TRANSFER">Bank transfer</option>
        </Select>
        {method !== 'CASH' && (
          <Input label="Transaction / reference ID (optional)" value={transactionId} onChange={(e) => setTransactionId(e.target.value)} />
        )}
        {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
      </div>
    </Modal>
  )
}
