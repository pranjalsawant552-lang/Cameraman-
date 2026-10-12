import { useEffect, useState } from 'react'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { fetchMyInvoices, createPaymentOrder, verifyRazorpayPayment, downloadInvoicePdf } from '@/lib/api'
import type { Invoice } from '@/lib/types'
import { formatCurrency, formatDate } from '@/lib/utils'
import { INVOICE_STATUS_TONE } from '@/lib/statusTone'
import { Download, Receipt } from 'lucide-react'
import { useToastStore } from '@/store/useToastStore'
import { useAuthStore } from '@/store/useAuthStore'
import { STUDIO_NAME } from '@/lib/branding'
import { openRazorpayCheckout } from '@/lib/razorpay'
import { ApiError } from '@/lib/apiClient'

export default function PortalInvoices() {
  const push = useToastStore((s) => s.push)
  const user = useAuthStore((s) => s.user)
  const [myInvoices, setMyInvoices] = useState<Invoice[]>([])
  const [loading, setLoading] = useState(true)
  const [payingId, setPayingId] = useState<string | null>(null)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  async function handleDownload(inv: Invoice) {
    setDownloadingId(inv.id)
    try {
      const blob = await downloadInvoicePdf(inv.id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${inv.invoiceNumber}.pdf`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not download the PDF. Please try again.')
    } finally { setDownloadingId(null) }
  }

  function reload() {
    fetchMyInvoices().then((res) => { setMyInvoices(res); setLoading(false) })
  }
  useEffect(reload, [])

  async function handlePay(inv: Invoice) {
    setPayingId(inv.id)
    try {
      const order = await createPaymentOrder({
        invoiceId: inv.id,
        clientId: user?.id,
        clientName: user ? `${user.firstName} ${user.lastName}`.trim() : inv.clientName,
        amount: inv.balanceDue,
        paymentType: inv.balanceDue >= inv.totalAmount ? 'FULL' : 'PARTIAL',
      })
      const result = await openRazorpayCheckout({
        keyId: order.keyId,
        orderId: order.orderId,
        amountPaise: Math.round(order.amount * 100),
        currency: order.currency,
        studioName: STUDIO_NAME,
        description: `Invoice ${inv.invoiceNumber}`,
        prefillName: user ? `${user.firstName} ${user.lastName}`.trim() : undefined,
        prefillEmail: user?.email,
        prefillContact: user?.mobile,
      })
      await verifyRazorpayPayment({
        razorpayOrderId: result.razorpay_order_id,
        razorpayPaymentId: result.razorpay_payment_id,
        razorpaySignature: result.razorpay_signature,
      })
      push('Payment successful')
      reload()
    } catch (err) {
      // openRazorpayCheckout rejects with a plain Error if the widget
      // failed to load or the user closed the modal — those aren't
      // ApiErrors, so they're shown as-is rather than a generic fallback.
      const message = err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Payment could not be completed.'
      push(message)
    } finally {
      setPayingId(null)
    }
  }

  if (loading) {
    return <div className="space-y-4">{Array.from({ length: 2 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-[var(--radius-lg)]" />)}</div>
  }

  if (myInvoices.length === 0) {
    return <Card><EmptyState icon={<Receipt className="h-10 w-10" />} title="No invoices yet" /></Card>
  }

  return (
    <div className="space-y-4">
      {myInvoices.map((inv) => (
        <Card key={inv.id} className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <p className="font-medium text-ink">{inv.invoiceNumber}</p>
              <Badge tone={INVOICE_STATUS_TONE[inv.status] as any}>{inv.status.replace('_', ' ')}</Badge>
            </div>
            <p className="mt-1 text-sm text-ink-muted">Due {formatDate(inv.dueDate)}</p>
          </div>
          <div className="flex items-center gap-6">
            <div className="text-right">
              <p className="text-xs text-ink-faint">Total</p>
              <p className="font-medium text-ink">{formatCurrency(inv.totalAmount)}</p>
            </div>
            <Button variant="outline" size="sm" onClick={() => handleDownload(inv)} loading={downloadingId === inv.id} aria-label={`Download ${inv.invoiceNumber}`}>
              <Download className="h-3.5 w-3.5" /> {inv.status === 'PAID' ? 'Receipt' : 'Invoice'}
            </Button>
            {inv.status === 'CANCELLED' ? (
              <Badge tone="neutral">Cancelled</Badge>
            ) : inv.balanceDue > 0 ? (
              <Button onClick={() => handlePay(inv)} disabled={payingId === inv.id}>
                {payingId === inv.id ? 'Processing…' : `Pay ${formatCurrency(inv.balanceDue)}`}
              </Button>
            ) : (
              <Badge tone="success">Fully paid</Badge>
            )}
          </div>
        </Card>
      ))}
    </div>
  )
}
