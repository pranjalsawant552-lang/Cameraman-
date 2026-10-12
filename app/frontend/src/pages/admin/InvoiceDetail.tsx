import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Ban, CheckCheck, Download, FileWarning, Mail, MessageCircle, Printer, Wallet } from 'lucide-react'
import { Card, Badge } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { fetchInvoiceById, fetchStudioSettings, finalizeInvoice, cancelInvoice, sendInvoiceWhatsApp, sendInvoiceEmail } from '@/lib/api'
import { RecordPaymentModal } from '@/components/billing/RecordPaymentModal'
import { Modal } from '@/components/ui/Modal'
import { Textarea } from '@/components/ui/Input'
import type { Invoice } from '@/lib/types'
import { formatCurrency, formatDate } from '@/lib/utils'
import { INVOICE_STATUS_TONE } from '@/lib/statusTone'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'
import type { StudioSettings } from '@/lib/api'
import { LAYOUTS, type InvoiceLayout } from '@/lib/invoiceHtml'
import { downloadInvoiceFile, printInvoice } from '@/lib/invoiceOutput'

export default function InvoiceDetail() {
  const { id } = useParams()
  const [invoice, setInvoice] = useState<Invoice | null | undefined>(undefined)
  const [downloading, setDownloading] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [payOpen, setPayOpen] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const push = useToastStore((s) => s.push)

  const reload = () => { if (id) fetchInvoiceById(id).then((res) => setInvoice(res ?? null)).catch(() => setInvoice(null)) }
  useEffect(reload, [id]) // eslint-disable-line react-hooks/exhaustive-deps

  async function run(key: string, fn: () => Promise<void>, ok: string) {
    setBusy(key)
    try { await fn(); push(ok); reload() }
    catch (err) { push(err instanceof ApiError ? err.message : 'That action failed. Please try again.') }
    finally { setBusy(null) }
  }

  const [studio, setStudio] = useState<StudioSettings | null>(null)
  const [layout, setLayout] = useState<InvoiceLayout>('A4')
  useEffect(() => { fetchStudioSettings().then(setStudio).catch(() => setStudio(null)) }, [])
  // Default to the layout the invoice was created with (bill-book invoices print on the A5 slip).
  useEffect(() => { if (invoice?.sizeFormat === 'BILLBOOK' || invoice?.sizeFormat === 'A6') setLayout('BILLBOOK') }, [invoice?.sizeFormat])

  // Print and download are drawn in the browser (not the server PDF) so Marathi text renders correctly and the
  // buttons work even when the PDF service or file storage is unavailable.
  async function handleDownload() {
    if (!invoice) return
    setDownloading(true)
    try { await downloadInvoiceFile(invoice, studio, layout) }
    catch { push('Could not create the PDF. Use Print and choose "Save as PDF" instead.') }
    finally { setDownloading(false) }
  }

  function handlePrint() {
    if (!invoice) return
    try { printInvoice(invoice, studio, layout) }
    catch { push('Could not open the print dialog. Please allow pop-ups / printing for this site.') }
  }

  // The server-side WhatsApp API needs credentials. Without them, fall back to opening
  // WhatsApp with a ready-typed message so staff can still attach the downloaded PDF and send.
  async function handleWhatsApp() {
    if (!invoice) return
    setBusy('wa')
    try {
      await sendInvoiceWhatsApp(invoice.id)
      push('Sent on WhatsApp')
      reload()
    } catch (err) {
      if (err instanceof ApiError && err.errorCode === 'WHATSAPP_NOT_CONFIGURED' && invoice.clientMobile) {
        const digits = invoice.clientMobile.replace(/\D/g, '')
        const number = digits.length === 10 ? `91${digits}` : digits
        const text = `Hello ${invoice.clientName}, your invoice ${invoice.invoiceNumber} for ${formatCurrency(invoice.totalAmount)} is ready. Please find the PDF attached.`
        window.open(`https://wa.me/${number}?text=${encodeURIComponent(text)}`, '_blank', 'noopener')
        push('WhatsApp API is not set up, so WhatsApp was opened with the message. Download the PDF and attach it.')
      } else {
        push(err instanceof ApiError ? err.message : 'That action failed. Please try again.')
      }
    } finally {
      setBusy(null)
    }
  }

  if (invoice === undefined) return <div className="text-sm text-ink-muted">Loading…</div>

  if (invoice === null) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 rounded-[var(--radius-lg)] border border-border bg-surface p-12 text-center">
        <FileWarning className="h-8 w-8 text-ink-faint" />
        <p className="text-sm text-ink-muted">This invoice couldn't be found.</p>
        <Link to="/admin/invoices" className="text-sm font-medium text-accent hover:underline">Back to invoices</Link>
      </div>
    )
  }

  const hasTaxBreakdown = invoice.subtotal != null

  return (
    <div className="space-y-5">
      <Link to="/admin/invoices" className="flex items-center gap-1.5 text-sm font-medium text-ink-muted hover:text-ink">
        <ArrowLeft className="h-4 w-4" /> Back to invoices
      </Link>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="font-display text-2xl text-ink">{invoice.invoiceNumber}</h2>
            <Badge tone={INVOICE_STATUS_TONE[invoice.status] as any}>{invoice.status.replace('_', ' ')}</Badge>
          </div>
          <p className="mt-1 text-sm text-ink-muted">{invoice.clientName}{invoice.clientMobile ? ` · ${invoice.clientMobile}` : ''}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {invoice.status === 'DRAFT' && <Button onClick={() => run('final', () => finalizeInvoice(invoice.id), 'Invoice finalized and queued for sending')} loading={busy === 'final'}><CheckCheck className="h-4 w-4" /> Finalize</Button>}
          {invoice.status !== 'DRAFT' && invoice.status !== 'CANCELLED' && invoice.balanceDue > 0 && <Button onClick={() => setPayOpen(true)}><Wallet className="h-4 w-4" /> Record payment</Button>}
          {invoice.status !== 'DRAFT' && invoice.status !== 'CANCELLED' && (
            <>
              <Button variant="outline" onClick={handleWhatsApp} loading={busy === 'wa'} disabled={!invoice.clientMobile}><MessageCircle className="h-4 w-4" /> WhatsApp</Button>
              <Button variant="outline" onClick={() => run('mail', () => sendInvoiceEmail(invoice.id), 'Emailed to the client')} loading={busy === 'mail'} disabled={!invoice.clientEmail}><Mail className="h-4 w-4" /> Email</Button>
            </>
          )}
          <select aria-label="Invoice size" value={layout} onChange={(e) => setLayout(e.target.value as InvoiceLayout)}
            className="h-9 rounded-[var(--radius-sm)] border border-border bg-surface px-2 text-sm">
            {(Object.keys(LAYOUTS) as InvoiceLayout[]).map((k) => <option key={k} value={k}>{LAYOUTS[k].label}</option>)}
          </select>
          <Button variant="outline" onClick={handleDownload} disabled={downloading}>
            <Download className="h-4 w-4" /> {downloading ? 'Preparing…' : 'Download PDF'}
          </Button>
          <Button variant="outline" onClick={handlePrint}><Printer className="h-4 w-4" /> Print</Button>
          {invoice.status !== 'CANCELLED' && invoice.amountPaid === 0 && (
            <Button variant="danger" onClick={() => setCancelOpen(true)}><Ban className="h-4 w-4" /> Cancel</Button>
          )}
        </div>
      </div>
      {invoice.status === 'DRAFT' && <p className="text-xs text-ink-faint">Draft invoices are not visible to the client and cannot take payments. Finalize to send it.</p>}
      {invoice.amountPaid > 0 && invoice.status !== 'CANCELLED' && <p className="text-xs text-ink-faint">An invoice with payments can't be cancelled until those payments are refunded (Payments page).</p>}

      <Card className="overflow-hidden p-0">
        <div className="overflow-x-auto">
        <table className="w-full min-w-[640px] text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
              <th scope="col" className="px-6 py-3">Description</th>
              <th scope="col" className="px-4 py-3">Qty</th>
              <th scope="col" className="px-4 py-3">Rate</th>
              <th scope="col" className="px-4 py-3 text-right">Amount</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {invoice.items && invoice.items.length > 0 ? invoice.items.map((it) => (
              <tr key={it.id}>
                <td className="px-6 py-3.5 text-ink">{it.description}</td>
                <td className="px-4 py-3.5 text-ink-muted">{it.quantity}</td>
                <td className="px-4 py-3.5 text-ink-muted">{formatCurrency(it.unitPrice)}</td>
                <td className="px-4 py-3.5 text-right text-ink">{formatCurrency(it.lineTotal)}</td>
              </tr>
            )) : (
              <tr><td colSpan={4} className="px-6 py-6 text-center text-ink-faint">Line items aren't available for this invoice.</td></tr>
            )}
          </tbody>
        </table>
</div>

        <div className="flex flex-col items-end gap-1.5 border-t border-border p-6 text-sm">
          {hasTaxBreakdown ? (
            <>
              <div className="flex w-56 justify-between"><span className="text-ink-muted">Subtotal</span><span className="text-ink">{formatCurrency(invoice.subtotal!)}</span></div>
              {!!invoice.discountAmount && <div className="flex w-56 justify-between"><span className="text-ink-muted">Discount</span><span className="text-ink">−{formatCurrency(invoice.discountAmount)}</span></div>}
              <div className="flex w-56 justify-between"><span className="text-ink-muted">CGST</span><span className="text-ink">{formatCurrency(invoice.cgstAmount ?? 0)}</span></div>
              <div className="flex w-56 justify-between"><span className="text-ink-muted">SGST</span><span className="text-ink">{formatCurrency(invoice.sgstAmount ?? 0)}</span></div>
              {!!invoice.igstAmount && <div className="flex w-56 justify-between"><span className="text-ink-muted">IGST</span><span className="text-ink">{formatCurrency(invoice.igstAmount)}</span></div>}
            </>
          ) : (
            <p className="mb-1 text-xs text-ink-faint">Tax breakdown isn't available for this invoice.</p>
          )}
          <div className="flex w-56 justify-between border-t border-border pt-1.5 text-base font-medium"><span className="text-ink">Total</span><span className="text-ink">{formatCurrency(invoice.totalAmount)}</span></div>
          <div className="flex w-56 justify-between text-success"><span>Paid</span><span>{formatCurrency(invoice.amountPaid)}</span></div>
          {invoice.balanceDue > 0 && <div className="flex w-56 justify-between font-medium text-warning"><span>Balance due</span><span>{formatCurrency(invoice.balanceDue)}</span></div>}
        </div>
      </Card>

      {invoice.notes && (
        <Card className="p-5"><p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Notes</p><p className="mt-1.5 text-sm text-ink">{invoice.notes}</p></Card>
      )}

      <RecordPaymentModal invoice={payOpen ? invoice : null} onClose={() => setPayOpen(false)} onRecorded={() => { setPayOpen(false); reload() }} />
      <Modal open={cancelOpen} onClose={() => setCancelOpen(false)} title="Cancel this invoice?"
        footer={<><Button variant="outline" onClick={() => setCancelOpen(false)}>Keep invoice</Button>
          <Button variant="danger" loading={busy === 'cancel'} disabled={!cancelReason.trim()}
            onClick={() => run('cancel', async () => { await cancelInvoice(invoice.id, cancelReason.trim()); setCancelOpen(false) }, 'Invoice cancelled')}>Cancel invoice</Button></>}>
        <Textarea label="Reason (required)" value={cancelReason} maxLength={300} rows={3} onChange={(e) => setCancelReason(e.target.value)} />
      </Modal>

      <p className="text-xs text-ink-faint">
        {invoice.invoiceDate && <>Issued {formatDate(invoice.invoiceDate)}{invoice.generatedByName ? ` by ${invoice.generatedByName}` : ''}</>}
      </p>
    </div>
  )
}
