import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus, Receipt, RefreshCw } from 'lucide-react'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { SearchInput } from '@/components/ui/Input'
import { Tabs } from '@/components/ui/Tabs'
import { Pagination } from '@/components/ui/Pagination'
import { Button } from '@/components/ui/Button'
import { fetchInvoices, backfillInvoices, type PagedResponse } from '@/lib/api'
import { useToastStore } from '@/store/useToastStore'
import type { Invoice, InvoiceStatus } from '@/lib/types'
import { formatCurrency, formatDate } from '@/lib/utils'
import { INVOICE_STATUS_TONE } from '@/lib/statusTone'
import { NewInvoiceModal } from '@/components/billing/NewInvoiceModal'
import { RecordPaymentModal } from '@/components/billing/RecordPaymentModal'

const TABS: { id: InvoiceStatus | 'ALL'; label: string }[] = [
  { id: 'ALL', label: 'All' }, { id: 'SENT', label: 'Sent' }, { id: 'PARTIALLY_PAID', label: 'Partial' },
  { id: 'PAID', label: 'Paid' }, { id: 'OVERDUE', label: 'Overdue' },
]

export default function Invoices() {
  const [backfilling, setBackfilling] = useState(false)
  const pushToast = useToastStore((st) => st.push)
  async function generateMissing() {
    setBackfilling(true)
    try {
      const r = await backfillInvoices()
      const why = r.failed && r.errors?.length ? ` First error: ${r.errors[0]}` : ' (see server log)'
      pushToast(r.created ? `Created ${r.created} invoice${r.created > 1 ? 's' : ''}${r.failed ? `, ${r.failed} failed.${why}` : '.'}` : r.failed ? `${r.failed} could not be created.${why}` : 'Every booking already has an invoice.')
      if (r.created) setRefreshKey((k) => k + 1)
    } catch { pushToast('Could not generate invoices. Please try again.') }
    finally { setBackfilling(false) }
  }
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<InvoiceStatus | 'ALL'>('ALL')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PagedResponse<Invoice> | null>(null)
  const [loading, setLoading] = useState(true)
  const [showNewInvoice, setShowNewInvoice] = useState(false)
  const [paymentInvoice, setPaymentInvoice] = useState<Invoice | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    setLoading(true)
    fetchInvoices({ search, status: status === 'ALL' ? '' : status, page, size: 8 }).then((res) => { setData(res); setLoading(false) })
  }, [search, status, page, refreshKey])

  useEffect(() => { setPage(0) }, [search, status])

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="w-full max-w-xs"><SearchInput value={search} onChange={setSearch} placeholder="Search invoices…" /></div>
        <div className="flex items-center gap-3">
          <Tabs tabs={TABS} active={status} onChange={setStatus} />
          <Button size="md" variant="outline" loading={backfilling} onClick={generateMissing}
            title="Create invoices for every booking, studio session and outdoor shoot that does not have one yet">
            <RefreshCw className="h-4 w-4" /> Generate missing invoices
          </Button>
          <Button size="md" onClick={() => setShowNewInvoice(true)}><Plus className="h-4 w-4" /> New invoice</Button>
        </div>
      </div>

      <Card className="overflow-hidden p-0">
        {loading ? (
          <div className="divide-y divide-border">{Array.from({ length: 5 }).map((_, i) => <div key={i} className="p-4"><Skeleton className="h-6 w-full" /></div>)}</div>
        ) : !data || data.content.length === 0 ? (
          <EmptyState icon={<Receipt className="h-10 w-10" />} title="No invoices found" description="Try a different search term or filter." />
        ) : (
          <>
            <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                  <th scope="col" className="px-6 py-3">Invoice</th>
                  <th scope="col" className="px-4 py-3">Client</th>
                  <th scope="col" className="px-4 py-3">Total</th>
                  <th scope="col" className="px-4 py-3">Balance due</th>
                  <th scope="col" className="px-4 py-3">Due date</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.content.map((inv) => (
                  <tr key={inv.id} className="transition-colors hover:bg-surface-2">
                    <td className="px-6 py-3.5 font-medium text-ink">
                      <Link to={`/admin/invoices/${inv.id}`} className="hover:text-accent hover:underline">{inv.invoiceNumber}</Link>
                    </td>
                    <td className="px-4 py-3.5 text-ink-muted">{inv.clientName}</td>
                    <td className="px-4 py-3.5 text-ink">{formatCurrency(inv.totalAmount)}</td>
                    <td className="px-4 py-3.5">
                      {inv.balanceDue > 0 ? (
                        <button
                          onClick={() => setPaymentInvoice(inv)}
                          className="font-medium text-warning underline decoration-dotted underline-offset-2 hover:text-accent"
                        >
                          {formatCurrency(inv.balanceDue)}
                        </button>
                      ) : <span className="text-ink-faint">—</span>}
                    </td>
                    <td className="px-4 py-3.5 text-ink-muted">{formatDate(inv.dueDate)}</td>
                    <td className="px-4 py-3.5"><Badge tone={INVOICE_STATUS_TONE[inv.status] as any}>{inv.status.replace('_', ' ')}</Badge></td>
                  </tr>
                ))}
              </tbody>
            </table>
</div>
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} pageSize={data.size} onChange={setPage} />
          </>
        )}
      </Card>

      <NewInvoiceModal
        open={showNewInvoice}
        onClose={() => setShowNewInvoice(false)}
        onCreated={() => { setPage(0); setRefreshKey((k) => k + 1) }}
      />
      <RecordPaymentModal
        invoice={paymentInvoice}
        onClose={() => setPaymentInvoice(null)}
        onRecorded={() => setRefreshKey((k) => k + 1)}
      />
    </div>
  )
}
