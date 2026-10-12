import { useEffect, useState } from 'react'
import { AlertTriangle, ChevronLeft, ChevronRight, Download } from 'lucide-react'
import { Card } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { downloadPayrollCsv, fetchPayroll } from '@/lib/api'
import type { PayrollReport } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'

const money = (v: number | null) => (v == null ? '—' : formatCurrency(v))

export function PayrollPanel() {
  const push = useToastStore((s) => s.push)
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [report, setReport] = useState<PayrollReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [downloading, setDownloading] = useState(false)

  useEffect(() => {
    let live = true
    setReport(null); setError(null)
    fetchPayroll(year, month).then((r) => { if (live) setReport(r) }).catch(() => { if (live) setError('Payroll is available to managers, admins and accountants.') })
    return () => { live = false }
  }, [year, month])

  function shift(delta: number) {
    let m = month + delta, y = year
    if (m < 1) { m = 12; y -= 1 }
    if (m > 12) { m = 1; y += 1 }
    setMonth(m); setYear(y)
  }

  async function download() {
    setDownloading(true)
    try { await downloadPayrollCsv(year, month) } catch { push('Could not download the payroll file.') } finally { setDownloading(false) }
  }

  const label = new Date(year, month - 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" aria-label="Previous month" onClick={() => shift(-1)}><ChevronLeft className="h-4 w-4" /></Button>
          <p className="w-36 text-center text-sm font-medium text-ink" aria-live="polite">{label}</p>
          <Button variant="outline" size="icon" aria-label="Next month" onClick={() => shift(1)}><ChevronRight className="h-4 w-4" /></Button>
        </div>
        <Button variant="outline" loading={downloading} disabled={!report} onClick={download}><Download className="h-4 w-4" /> Download CSV</Button>
      </div>

      {error && <Card className="p-6 text-sm text-ink-muted">{error}</Card>}
      {!error && !report && <p className="text-sm text-ink-muted">Loading…</p>}

      {report && (
        <>
          {report.monthInProgress && (
            <div role="status" className="flex items-start gap-2 rounded-[var(--radius-md)] border border-warning/30 bg-warning-bg p-3 text-sm text-warning">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>This month isn't over. Only absences and late arrivals so far are counted, so these figures will change. Don't pay from this view until the month closes.</span>
            </div>
          )}
          <Card className="overflow-hidden p-0">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] text-sm">
                <caption className="sr-only">Payroll for {label}</caption>
                <thead>
                  <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                    <th scope="col" className="px-6 py-3">Staff</th>
                    <th scope="col" className="px-3 py-3 text-right">Working</th>
                    <th scope="col" className="px-3 py-3 text-right">Present</th>
                    <th scope="col" className="px-3 py-3 text-right">Late</th>
                    <th scope="col" className="px-3 py-3 text-right">Half</th>
                    <th scope="col" className="px-3 py-3 text-right">Absent</th>
                    <th scope="col" className="px-3 py-3 text-right">OT hrs</th>
                    <th scope="col" className="px-3 py-3 text-right">Unpaid days</th>
                    <th scope="col" className="px-3 py-3 text-right">Salary</th>
                    <th scope="col" className="px-3 py-3 text-right">Deduction</th>
                    <th scope="col" className="px-3 py-3 text-right">OT pay</th>
                    <th scope="col" className="px-4 py-3 text-right">Net pay</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {report.rows.map((r) => (
                    <tr key={r.staffId} className="hover:bg-surface-2">
                      <th scope="row" className="px-6 py-3 text-left font-medium text-ink">{r.staffName}<span className="block text-xs font-normal text-ink-faint">{r.employeeId ?? ''}</span></th>
                      <td className="px-3 py-3 text-right text-ink-muted">{r.workingDays}</td>
                      <td className="px-3 py-3 text-right text-ink-muted">{r.presentDays}</td>
                      <td className="px-3 py-3 text-right text-ink-muted">{r.lateDays}</td>
                      <td className="px-3 py-3 text-right text-ink-muted">{r.halfDays}</td>
                      <td className="px-3 py-3 text-right text-ink-muted">{r.absentDays}</td>
                      <td className="px-3 py-3 text-right text-ink-muted">{r.overtimeHours}</td>
                      <td className="px-3 py-3 text-right text-ink-muted">{r.unpaidDays}</td>
                      <td className="px-3 py-3 text-right text-ink-muted">{money(r.monthlySalary)}</td>
                      <td className="px-3 py-3 text-right text-ink-muted">{money(r.deduction)}</td>
                      <td className="px-3 py-3 text-right text-ink-muted">{money(r.overtimePay)}</td>
                      <td className="px-4 py-3 text-right font-medium text-ink">{r.netPay == null ? <span className="text-xs font-normal text-ink-faint">No salary set</span> : money(r.netPay)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-border bg-surface-2">
                    <th scope="row" colSpan={11} className="px-6 py-3 text-left text-sm font-medium text-ink">Total net pay</th>
                    <td className="px-4 py-3 text-right font-semibold text-ink">{formatCurrency(report.totalNetPay)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </Card>
          <p className="text-xs text-ink-faint">Unpaid days = absences + ½ per half day + ½ per set of late arrivals (see the rules under Devices &amp; import). Approved leave, weekly offs and holidays are paid.</p>
        </>
      )}
    </div>
  )
}
