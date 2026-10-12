import { useEffect, useState } from 'react'
import { CreditCard, IndianRupee } from 'lucide-react'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { SearchInput } from '@/components/ui/Input'
import { Tabs } from '@/components/ui/Tabs'
import { Pagination } from '@/components/ui/Pagination'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Input, Textarea } from '@/components/ui/Input'
import { fetchPayments, refundPayment, type PaymentRecord } from '@/lib/api'
import type { PagedResponse } from '@/lib/api'
import { formatCurrency, formatDate } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

const TABS = [
  { id: 'ALL', label: 'All' }, { id: 'SUCCESS', label: 'Successful' },
  { id: 'PENDING', label: 'Pending' }, { id: 'FAILED', label: 'Failed' }, { id: 'REFUNDED', label: 'Refunded' },
]

const STATUS_TONE: Record<string, string> = {
  SUCCESS: 'success', PENDING: 'warning', FAILED: 'danger', REFUNDED: 'neutral', PARTIALLY_REFUNDED: 'neutral',
}

export default function Payments() {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('ALL')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PagedResponse<PaymentRecord> | null>(null)
  const [loading, setLoading] = useState(true)
  const [refundTarget, setRefundTarget] = useState<PaymentRecord | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    setLoading(true)
    fetchPayments({ search, status: status === 'ALL' ? '' : status, page, size: 10 }).then((res) => { setData(res); setLoading(false) })
  }, [search, status, page, refreshKey])

  useEffect(() => { setPage(0) }, [search, status])

  return (
    <div className="space-y-5">
      <div className="w-full max-w-xs"><SearchInput value={search} onChange={setSearch} placeholder="Search payments…" /></div>
      <Tabs tabs={TABS} active={status} onChange={setStatus} />

      <Card className="overflow-hidden p-0">
        {loading ? (
          <div className="divide-y divide-border">{Array.from({ length: 5 }).map((_, i) => <div key={i} className="p-4"><Skeleton className="h-6 w-full" /></div>)}</div>
        ) : !data || data.content.length === 0 ? (
          <EmptyState icon={<CreditCard className="h-10 w-10" />} title="No payments found" description="Payments made online or recorded manually will show up here." />
        ) : (
          <>
            <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                  <th scope="col" className="px-6 py-3">Client</th>
                  <th scope="col" className="px-4 py-3">Amount</th>
                  <th scope="col" className="px-4 py-3">Method</th>
                  <th scope="col" className="px-4 py-3">Type</th>
                  <th scope="col" className="px-4 py-3">Date</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  <th scope="col" className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.content.map((p) => (
                  <tr key={p.id} className="transition-colors hover:bg-surface-2">
                    <td className="px-6 py-3.5 font-medium text-ink">{p.clientName ?? '—'}</td>
                    <td className="px-4 py-3.5 text-ink">
                      {formatCurrency(p.amount)}
                      {p.refundedAmount ? <span className="ml-1.5 text-xs text-ink-faint">({formatCurrency(p.refundedAmount)} refunded)</span> : null}
                    </td>
                    <td className="px-4 py-3.5 text-ink-muted">{p.paymentMethod ?? (p.razorpayPaymentId ? 'RAZORPAY' : '—')}</td>
                    <td className="px-4 py-3.5 text-ink-muted">{p.paymentType}</td>
                    <td className="px-4 py-3.5 text-ink-muted">{p.paymentDate ? formatDate(p.paymentDate) : formatDate(p.createdAt)}</td>
                    <td className="px-4 py-3.5"><Badge tone={(STATUS_TONE[p.status] ?? 'neutral') as any}>{p.status.replace('_', ' ')}</Badge></td>
                    <td className="px-4 py-3.5 text-right">
                      {p.status === 'SUCCESS' && (
                        <button onClick={() => setRefundTarget(p)} className="text-xs font-medium text-accent hover:underline">Refund</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
</div>
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} pageSize={data.size} onChange={setPage} />
          </>
        )}
      </Card>

      <RefundModal payment={refundTarget} onClose={() => setRefundTarget(null)} onRefunded={() => setRefreshKey((k) => k + 1)} />
    </div>
  )
}

function RefundModal({ payment, onClose, onRefunded }: { payment: PaymentRecord | null; onClose: () => void; onRefunded: () => void }) {
  const push = useToastStore((s) => s.push)
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => { if (payment) { setAmount(String(payment.amount - (payment.refundedAmount ?? 0))); setReason('') } }, [payment])

  if (!payment) return null
  const maxRefundable = payment.amount - (payment.refundedAmount ?? 0)

  async function handleSubmit() {
    setError(null)
    const value = Number(amount)
    if (!value || value <= 0) { setError('Enter a refund amount greater than 0.'); return }
    if (value > maxRefundable) { setError(`Amount can't exceed ${formatCurrency(maxRefundable)}.`); return }
    if (!reason.trim()) { setError('A reason is required.'); return }
    setSubmitting(true)
    try {
      await refundPayment(payment!.id, value, reason.trim())
      push('Refund processed')
      onRefunded()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not process the refund. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={!!payment}
      onClose={onClose}
      title="Process refund"
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Processing…' : 'Refund'}</Button></>}
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between rounded-[var(--radius-md)] bg-surface-2 px-3.5 py-2.5 text-sm">
          <span className="text-ink-muted flex items-center gap-1.5"><IndianRupee className="h-3.5 w-3.5" /> Refundable</span>
          <span className="font-medium text-ink">{formatCurrency(maxRefundable)}</span>
        </div>
        <Input label="Refund amount (₹)" type="number" min="0.01" max={maxRefundable} value={amount} onChange={(e) => setAmount(e.target.value)} />
        <Textarea label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} rows={2} />
        {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
      </div>
    </Modal>
  )
}
