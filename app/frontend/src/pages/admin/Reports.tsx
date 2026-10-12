import { useState } from 'react'
import { Boxes, CalendarRange, Download, FileSpreadsheet, Images, Receipt, Users } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/primitives'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { downloadReport, reportNeedsDateRange, type ReportKind } from '@/lib/api'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'
import { localDateKey, todayLocalDateKey } from '@/lib/dateUtils'

const REPORTS: { kind: ReportKind; title: string; desc: string; icon: typeof FileSpreadsheet }[] = [
  { kind: 'bookings', title: 'Bookings', desc: 'Every booking in the date range — client, package, dates, status, amounts.', icon: CalendarRange },
  { kind: 'revenue', title: 'Revenue', desc: 'Revenue breakdown for the date range, for accounting and GST filing.', icon: Receipt },
  { kind: 'invoices', title: 'Invoices', desc: 'All invoices raised in the date range, with tax and payment status.', icon: FileSpreadsheet },
  { kind: 'galleries', title: 'Galleries', desc: 'Galleries published or updated in the date range, with view/download stats.', icon: Images },
  { kind: 'inventory', title: 'Inventory', desc: 'Current stock levels across every item — a snapshot, not a date range.', icon: Boxes },
  { kind: 'clients', title: 'Clients', desc: 'Full client directory snapshot — contact details for every registered client.', icon: Users },
]

function oneMonthAgoLocalDateKey(): string {
  const d = new Date()
  d.setMonth(d.getMonth() - 1)
  return localDateKey(d)
}

export default function Reports() {
  const push = useToastStore((s) => s.push)
  const [from, setFrom] = useState(oneMonthAgoLocalDateKey())
  const [to, setTo] = useState(todayLocalDateKey())
  const [downloading, setDownloading] = useState<ReportKind | null>(null)

  async function handleDownload(kind: ReportKind) {
    if (reportNeedsDateRange(kind) && (!from || !to)) {
      push('Pick a from and to date first.')
      return
    }
    setDownloading(kind)
    try {
      await downloadReport(kind, from, to)
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not generate the report. Please try again.')
    } finally {
      setDownloading(null)
    }
  }

  return (
    <div className="max-w-3xl space-y-5">
      <Card>
        <CardHeader><CardTitle>Date range</CardTitle></CardHeader>
        <CardContent className="flex flex-wrap items-end gap-4 pt-3">
          <Input label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          <Input label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          <p className="pb-2 text-xs text-ink-faint">Used by every report below except Inventory and Clients, which are always a current-moment snapshot.</p>
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {REPORTS.map((r) => (
          <Card key={r.kind} className="flex flex-col p-5">
            <r.icon className="h-5 w-5 text-accent" />
            <p className="mt-3 font-medium text-ink">{r.title}</p>
            <p className="mt-1 flex-1 text-sm text-ink-muted">{r.desc}</p>
            <Button variant="outline" className="mt-4 self-start" onClick={() => handleDownload(r.kind)} disabled={downloading === r.kind}>
              <Download className="h-3.5 w-3.5" /> {downloading === r.kind ? 'Preparing…' : 'Export .xlsx'}
            </Button>
          </Card>
        ))}
      </div>
    </div>
  )
}
