import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Calendar, Check, ChevronLeft, ChevronRight, Clock, Copy, Eye, EyeOff, Link2,
  RefreshCw, Upload, UserCheck, X,
} from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, Badge, Avatar, EmptyState } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Tabs } from '@/components/ui/Tabs'
import { Input, Select } from '@/components/ui/Input'
import {
  fetchDaySummary, fetchMonthlyReport, fetchStaff, fetchWorkHoursConfig, updateWorkHoursConfig,
  fetchWebhookConfig, regenerateWebhookSecret, fetchUnmatchedCodes, linkEmployeeCode,
  importAttendanceCsv, manualAttendanceEntry, fetchLeaveRequests, actOnLeaveRequest,
  fetchAttendanceDevices, updateAttendanceDevice, type AttendanceDeviceInfo,
  fetchCorrections, reviewCorrection, type AttendanceCorrectionInfo,
} from '@/lib/api'
import type {
  DaySummary, DayAttendanceStatus, MonthlyReport, StaffMember, WorkHoursConfig,
  WebhookConfig, UnmatchedCode, StaffLeaveRequest,
} from '@/lib/types'
import { todayLocalDateKey } from '@/lib/dateUtils'
import { cn, formatDate } from '@/lib/utils'
import { DAY_ATTENDANCE_TONE, STAFF_LEAVE_STATUS_TONE } from '@/lib/statusTone'
import { useToastStore } from '@/store/useToastStore'
import { useAuthStore } from '@/store/useAuthStore'
import { hasPermission } from '@/lib/access'
import { ShiftsPanel } from '@/components/attendance/ShiftsPanel'
import { PayrollPanel } from '@/components/attendance/PayrollPanel'

type Tab = 'today' | 'monthly' | 'shifts' | 'payroll' | 'devices' | 'leave' | 'corrections'

/** 95 -> "1h 35m"; nothing worked shows a dash. */
function hm(mins?: number) {
  if (!mins) return '—'
  const h = Math.floor(mins / 60), m = mins % 60
  return h ? `${h}h ${m}m` : `${m}m`
}

function timeOf(iso: string | null) {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })
}

export default function Attendance() {
  const [params] = useSearchParams()
  const [tab, setTab] = useState<Tab>(params.get('staffId') ? 'monthly' : 'today')
  const [staff, setStaff] = useState<StaffMember[]>([])

  useEffect(() => { fetchStaff().then(setStaff) }, [])

  const user = useAuthStore((st) => st.user)
  const canSeePay = !!user && (user.roles.some((r) => ['ADMIN', 'SUPER_ADMIN', 'MANAGER'].includes(r)) || hasPermission(user.permissions, 'ATTENDANCE:EXPORT'))

  return (
    <div className="space-y-5">
      <Tabs
        tabs={[
          { id: 'today', label: 'Today' },
          { id: 'monthly', label: 'Monthly report' },
          { id: 'shifts', label: 'Shifts & holidays' },
          ...(canSeePay ? [{ id: 'payroll' as const, label: 'Payroll' }] : []),
          { id: 'leave', label: 'Leave requests' },
          { id: 'corrections', label: 'Punch corrections' },
          { id: 'devices', label: 'Devices & import' },
        ]}
        active={tab}
        onChange={setTab}
      />
      {tab === 'today' && <TodayTab staff={staff} />}
      {tab === 'monthly' && <MonthlyTab staff={staff} initialStaffId={params.get('staffId')} />}
      {tab === 'shifts' && <ShiftsPanel canSeePay={canSeePay} />}
      {tab === 'payroll' && canSeePay && <PayrollPanel />}
      {tab === 'leave' && <LeaveTab />}
      {tab === 'corrections' && <CorrectionsTab />}
      {tab === 'devices' && <DevicesTab staff={staff} />}
    </div>
  )
}

// ==================== TODAY ====================

function TodayTab({ staff }: { staff: StaffMember[] }) {
  const [date, setDate] = useState(todayLocalDateKey())
  const [rows, setRows] = useState<(DaySummary & { status: DayAttendanceStatus })[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => { setLoading(true); fetchDaySummary(date).then((r) => { setRows(r); setLoading(false) }) }, [date])

  const counts = useMemo(() => {
    const c: Record<DayAttendanceStatus, number> = { PRESENT: 0, LATE: 0, HALF_DAY: 0, ABSENT: 0, ON_LEAVE: 0, WEEKLY_OFF: 0, HOLIDAY: 0, FUTURE: 0 }
    rows.forEach((r) => { c[r.status]++ })
    return c
  }, [rows])

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2">
          <Calendar className="h-4 w-4 text-ink-faint" />
          <input type="date" aria-label="Attendance date" value={date} onChange={(e) => setDate(e.target.value)} className="h-10 rounded-[var(--radius-sm)] border border-border bg-surface-2 px-3 text-sm text-ink outline-none focus:border-accent" />
        </div>
        <p className="text-sm text-ink-muted">{formatDate(date, { weekday: 'long', day: 'numeric', month: 'long' })}</p>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <CountTile label="Present" value={counts.PRESENT} tone="success" />
        <CountTile label="Late" value={counts.LATE} tone="warning" />
        <CountTile label="Half day" value={counts.HALF_DAY} tone="warning" />
        <CountTile label="Absent" value={counts.ABSENT} tone="danger" />
        <CountTile label="On leave" value={counts.ON_LEAVE} tone="info" />
        <CountTile label="Off / holiday" value={counts.WEEKLY_OFF + counts.HOLIDAY} tone="neutral" />
      </div>

      <Card className="overflow-hidden p-0">
        {loading ? (
          <div className="p-6 text-sm text-ink-muted">Loading…</div>
        ) : (
          <div className="overflow-x-auto">
          <table className="w-full min-w-[560px] text-sm">
            <caption className="sr-only">Attendance for each staff member on the selected day</caption>
            <thead>
              <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                <th scope="col" className="px-6 py-3">Staff</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="px-4 py-3">In time</th>
                <th scope="col" className="px-4 py-3">Out time</th>
                <th scope="col" className="px-4 py-3">Hours</th>
                <th scope="col" className="px-4 py-3">Overtime</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {rows.map((r) => {
                const s = staff.find((x) => x.id === r.staffId)
                return (
                  <tr key={r.staffId} className="transition-colors hover:bg-surface-2">
                    <td className="px-6 py-3">
                      <div className="flex items-center gap-3">
                        <Avatar src={s?.profilePhotoUrl} name={r.staffName} size={32} />
                        <span className="font-medium text-ink">{r.staffName}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3"><Badge tone={DAY_ATTENDANCE_TONE[r.status] as any}>{r.status.replace('_', ' ')}</Badge></td>
                    <td className="px-4 py-3 text-ink-muted">{timeOf(r.firstPunch)}</td>
                    <td className="px-4 py-3 text-ink-muted">{timeOf(r.lastPunch)}</td>
                    <td className="px-4 py-3 text-ink-muted">{hm(r.workedMinutes)}{r.punchCount === 1 && <span className="ml-1 text-xs text-warning">no out punch</span>}</td>
                    <td className="px-4 py-3 text-ink-muted">{r.overtimeMinutes ? hm(r.overtimeMinutes) : '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          </div>
        )}
      </Card>
    </div>
  )
}

function CountTile({ label, value, tone }: { label: string; value: number; tone: string }) {
  const toneClass: Record<string, string> = { success: 'text-success bg-success-bg', warning: 'text-warning bg-warning-bg', danger: 'text-danger bg-danger-bg', info: 'text-info bg-info-bg', neutral: 'text-ink-muted bg-surface-2' }
  return (
    <Card className={cn('p-4', toneClass[tone])}>
      <p className="font-display text-2xl">{value}</p>
      <p className="text-xs opacity-80">{label}</p>
    </Card>
  )
}

// ==================== MONTHLY ====================

const WEEKDAYS = ['S', 'M', 'T', 'W', 'T', 'F', 'S']

function MonthlyTab({ staff, initialStaffId }: { staff: StaffMember[]; initialStaffId: string | null }) {
  const anchor = new Date()
  const [staffId, setStaffId] = useState(initialStaffId ?? '')
  const [year, setYear] = useState(anchor.getFullYear())
  const [month, setMonth] = useState(anchor.getMonth() + 1)
  const [report, setReport] = useState<MonthlyReport | null>(null)

  useEffect(() => {
    if (staff.length && !staffId) setStaffId(staff[0].id)
  }, [staff, staffId])

  useEffect(() => {
    if (!staffId) return
    fetchMonthlyReport(staffId, year, month).then(setReport)
  }, [staffId, year, month])

  function shiftMonth(delta: number) {
    let m = month + delta, y = year
    if (m < 1) { m = 12; y -= 1 }
    if (m > 12) { m = 1; y += 1 }
    setMonth(m); setYear(y)
  }

  if (!report) return <div className="text-sm text-ink-muted">Loading…</div>

  const firstDay = new Date(year, month - 1, 1).getDay()
  const leadingBlanks = Array.from({ length: firstDay })

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Select value={staffId} onChange={(e) => setStaffId(e.target.value)} className="max-w-xs">
          {staff.map((s) => <option key={s.id} value={s.id}>{s.firstName} {s.lastName} — {s.employeeId}</option>)}
        </Select>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" aria-label="Previous month" onClick={() => shiftMonth(-1)}><ChevronLeft className="h-4 w-4" /></Button>
          <p className="w-36 text-center text-sm font-medium text-ink">{new Date(year, month - 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })}</p>
          <Button variant="outline" size="icon" aria-label="Next month" onClick={() => shiftMonth(1)}><ChevronRight className="h-4 w-4" /></Button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
        <CountTile label="Present" value={report.presentDays} tone="success" />
        <CountTile label="Late" value={report.lateDays} tone="warning" />
        <CountTile label="Half day" value={report.halfDays ?? 0} tone="warning" />
        <CountTile label="Absent" value={report.absentDays} tone="danger" />
        <CountTile label="On leave" value={report.leaveDays} tone="info" />
        <CountTile label="Weekly off" value={report.weeklyOffDays} tone="neutral" />
        <CountTile label="Holidays" value={report.holidayDays ?? 0} tone="info" />
      </div>
      <p className="text-sm text-ink-muted">
        {report.shiftName ? `${report.shiftName} shift · ` : ''}Worked {hm(report.workedMinutes)} of {report.workingDays ?? 0} working days · overtime {hm(report.overtimeMinutes)} · late by {hm(report.lateMinutes)} in total
      </p>

      <Card className="p-5">
        <div className="mb-3 grid grid-cols-7 gap-1.5 text-center text-xs font-medium text-ink-faint">
          {WEEKDAYS.map((w, i) => <div key={i}>{w}</div>)}
        </div>
        <div className="grid grid-cols-7 gap-1.5">
          {leadingBlanks.map((_, i) => <div key={`b${i}`} />)}
          {report.days.map((d) => {
            const tone = DAY_ATTENDANCE_TONE[d.status]
            const toneClass: Record<string, string> = {
              success: 'bg-success-bg text-success border-success/25', warning: 'bg-warning-bg text-warning border-warning/25',
              danger: 'bg-danger-bg text-danger border-danger/25', info: 'bg-info-bg text-info border-info/25',
              neutral: 'bg-surface-2 text-ink-faint border-border',
            }
            return (
              <div key={d.date}
                title={`${d.date} · ${d.status.replace('_', ' ').toLowerCase()}${d.holidayName ? ` (${d.holidayName})` : ''}${d.missingOut ? ' · no out punch' : ''}`}
                className={cn('flex aspect-square flex-col items-center justify-center rounded-[var(--radius-sm)] border text-center', toneClass[tone])}>
                <span className="text-xs font-semibold">{new Date(d.date).getDate()}</span>
                <span className="sr-only">{d.status.replace('_', ' ').toLowerCase()}</span>
                {(d.status === 'PRESENT' || d.status === 'LATE' || d.status === 'HALF_DAY') && (
                  <span className="text-[9px] leading-tight opacity-80">{timeOf(d.firstPunch)}</span>
                )}
              </div>
            )
          })}
        </div>
        <div className="mt-4 flex flex-wrap gap-3 border-t border-border pt-3 text-xs text-ink-faint">
          {(['PRESENT', 'LATE', 'HALF_DAY', 'ABSENT', 'ON_LEAVE', 'WEEKLY_OFF', 'HOLIDAY'] as DayAttendanceStatus[]).map((s) => (
            <span key={s} className="flex items-center gap-1.5">
              <span className={cn('h-2.5 w-2.5 rounded-full', {
                success: 'bg-success', warning: 'bg-warning', danger: 'bg-danger', info: 'bg-info', neutral: 'bg-ink-faint',
              }[DAY_ATTENDANCE_TONE[s]])} />
              {s.replace('_', ' ')}
            </span>
          ))}
        </div>
      </Card>
    </div>
  )
}

// ==================== LEAVE ====================

function LeaveTab() {
  const [leaves, setLeaves] = useState<StaffLeaveRequest[]>([])
  const push = useToastStore((s) => s.push)

  function reload() { fetchLeaveRequests().then(setLeaves) }
  useEffect(reload, [])

  const pending = leaves.filter((l) => l.status === 'PENDING')
  const history = leaves.filter((l) => l.status !== 'PENDING')

  async function act(id: string, action: 'approve' | 'reject') {
    await actOnLeaveRequest(id, action)
    push(action === 'approve' ? 'Leave approved' : 'Leave rejected')
    reload()
  }

  return (
    <div className="space-y-5">
      {pending.length === 0 ? (
        <Card><EmptyState icon={<UserCheck className="h-10 w-10" />} title="No pending leave requests" /></Card>
      ) : (
        <div className="space-y-3">
          {pending.map((l) => (
            <Card key={l.id} className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="font-medium text-ink">{l.staffName}</p>
                <p className="text-sm text-ink-muted">{formatDate(l.fromDate)} – {formatDate(l.toDate)} · {l.dayCount} day{l.dayCount > 1 ? 's' : ''}</p>
                <p className="mt-1 text-sm text-ink-faint">"{l.reason}"</p>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={() => act(l.id, 'reject')}><X className="h-3.5 w-3.5" /> Reject</Button>
                <Button size="sm" onClick={() => act(l.id, 'approve')}><Check className="h-3.5 w-3.5" /> Approve</Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {history.length > 0 && (
        <div>
          <p className="mb-2 text-xs font-medium uppercase tracking-wide text-ink-faint">History</p>
          <Card className="divide-y divide-border p-0">
            {history.map((l) => (
              <div key={l.id} className="flex items-center justify-between gap-4 px-5 py-3">
                <div>
                  <p className="text-sm text-ink">{l.staffName} <span className="text-ink-faint">· {formatDate(l.fromDate)} – {formatDate(l.toDate)}</span></p>
                  <p className="text-xs text-ink-faint">{l.reviewedByName ? `Reviewed by ${l.reviewedByName}` : ''}</p>
                </div>
                <Badge tone={STAFF_LEAVE_STATUS_TONE[l.status] as any}>{l.status}</Badge>
              </div>
            ))}
          </Card>
        </div>
      )}
    </div>
  )
}

// ==================== DEVICES & IMPORT ====================

function DevicesTab({ staff }: { staff: StaffMember[] }) {
  const push = useToastStore((s) => s.push)
  const [config, setConfig] = useState<WebhookConfig | null>(null)
  const [showSecret, setShowSecret] = useState(false)
  const [unmatched, setUnmatched] = useState<UnmatchedCode[]>([])
  const [linkTarget, setLinkTarget] = useState<Record<string, string>>({})
  const [workHours, setWorkHours] = useState<WorkHoursConfig | null>(null)
  const [importResult, setImportResult] = useState<{ totalRows: number; imported: number; duplicates?: number; skipped: number; errors: string[] } | null>(null)
  const [manual, setManual] = useState({ staffId: '', punchTime: '', direction: 'IN' as const })
  const [devices, setDevices] = useState<AttendanceDeviceInfo[] | null>(null)
  const [devicesError, setDevicesError] = useState<string | null>(null)
  const pushToast = useToastStore((st) => st.push)

  const loadDevices = () => fetchAttendanceDevices().then(setDevices).catch(() => setDevicesError('Could not load devices (admins only).'))
  useEffect(() => { loadDevices() }, [])

  async function setDeviceAllowed(serial: string, allowed: boolean) {
    try { await updateAttendanceDevice(serial, { allowed }); pushToast(allowed ? 'Device approved' : 'Device blocked'); loadDevices() }
    catch { pushToast('Could not update the device.') }
  }

  useEffect(() => {
    fetchWebhookConfig().then(setConfig)
    fetchUnmatchedCodes().then(setUnmatched)
    fetchWorkHoursConfig().then(setWorkHours)
  }, [])

  function copy(text: string) {
    navigator.clipboard?.writeText(text)
    push('Copied to clipboard')
  }

  async function handleLink(code: string) {
    const staffId = linkTarget[code]
    if (!staffId) return
    await linkEmployeeCode(code, staffId)
    push('Employee code linked — past and future punches from this device now map to that staff member')
    fetchUnmatchedCodes().then(setUnmatched)
  }

  async function handleImport(file: File) {
    const result = await importAttendanceCsv(file)
    setImportResult(result)
  }

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader><CardTitle>Known machines</CardTitle></CardHeader>
        <CardContent className="pt-3">
          <p className="mb-3 text-sm text-ink-muted">A machine appears here the first time it contacts the server. New machines are blocked until you approve them; machines that send the shared key are always accepted.</p>
          {devicesError && <p className="text-sm text-ink-faint">{devicesError}</p>}
          {devices && devices.length === 0 && <p className="text-sm text-ink-muted">No machine has contacted the server yet.</p>}
          {devices && devices.length > 0 && (
            <ul className="divide-y divide-border">
              {devices.map((d) => (
                <li key={d.serial} className="flex flex-wrap items-center justify-between gap-3 py-2.5 text-sm">
                  <div>
                    <p className="font-medium text-ink">{d.label || d.serial} {!d.allowed && <Badge tone="warning">Waiting for approval</Badge>}</p>
                    <p className="text-xs text-ink-faint">Serial {d.serial} · {d.punchCount} punches · last seen {new Date(d.lastSeenAt).toLocaleString('en-IN')}</p>
                  </div>
                  <Button size="sm" variant={d.allowed ? 'outline' : 'primary'} onClick={() => setDeviceAllowed(d.serial, !d.allowed)}>{d.allowed ? 'Block' : 'Approve'}</Button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>Connect your attendance machine</CardTitle></CardHeader>
        <CardContent className="space-y-4 pt-3">
          <p className="text-sm text-ink-muted">
            Works with ZKTeco, eSSL, Realtime, Mantra and most budget biometric/RFID machines that speak the
            ZKTeco push (ADMS) protocol — point the device at the URL below. Anything else that can make an
            outbound HTTP request can use the generic webhook instead.
          </p>
          {config && (
            <div className="space-y-3">
              <ConfigRow label="Device push URL (ZKTeco / ADMS protocol)" value={config.iclockBaseUrl} onCopy={copy} />
              <ConfigRow label="Generic webhook URL" value={config.genericWebhookUrl} onCopy={copy} />
              <div>
                <p className="mb-1.5 text-xs font-medium text-ink-muted">Secret key</p>
                <div className="flex items-center gap-2">
                  <code className="flex-1 truncate rounded-[var(--radius-sm)] border border-border bg-surface-2 px-3 py-2 text-xs text-ink">
                    {showSecret ? config.secret : '•'.repeat(28)}
                  </code>
                  <Button variant="ghost" size="icon" aria-label={showSecret ? 'Hide secret key' : 'Show secret key'} onClick={() => setShowSecret((v) => !v)}>{showSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</Button>
                  <Button variant="ghost" size="icon" aria-label="Copy secret key" onClick={() => copy(config.secret)}><Copy className="h-4 w-4" /></Button>
                  <Button variant="outline" size="sm" onClick={async () => { const c = await regenerateWebhookSecret(); setConfig(c); push('Secret regenerated — update it on your device') }}>
                    <RefreshCw className="h-3.5 w-3.5" /> Regenerate
                  </Button>
                </div>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {workHours && (
        <Card>
          <CardHeader><CardTitle>Studio default shift &amp; attendance rules</CardTitle></CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 pt-3 sm:grid-cols-3">
            <Input label="Shift start" type="time" value={workHours.shiftStart} onChange={(e) => setWorkHours({ ...workHours, shiftStart: e.target.value })} />
            <Input label="Grace period (minutes)" type="number" value={workHours.graceMinutes} onChange={(e) => setWorkHours({ ...workHours, graceMinutes: Number(e.target.value) })} />
            <Select label="Weekly off day" value={workHours.weeklyOffDay} onChange={(e) => setWorkHours({ ...workHours, weeklyOffDay: e.target.value })}>
              {['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'].map((d) => <option key={d} value={d}>{d}</option>)}
            </Select>
            <Input label="Shift end" type="time" value={workHours.shiftEnd ?? ''} onChange={(e) => setWorkHours({ ...workHours, shiftEnd: e.target.value })} />
            <Input label="Half day if worked under (minutes)" type="number" min={0} value={workHours.halfDayMinutes ?? 0} onChange={(e) => setWorkHours({ ...workHours, halfDayMinutes: Number(e.target.value) })} />
            <Input label="Smallest overtime counted (minutes)" type="number" min={0} value={workHours.minOvertimeMinutes ?? 0} onChange={(e) => setWorkHours({ ...workHours, minOvertimeMinutes: Number(e.target.value) })} />
            <Input label="Late arrivals that cost half a day (0 = off)" type="number" min={0} value={workHours.latesPerHalfDay ?? 0} onChange={(e) => setWorkHours({ ...workHours, latesPerHalfDay: Number(e.target.value) })} />
          </CardContent>
          <div className="flex justify-end px-6 pb-6">
            <Button onClick={async () => { const c = await updateWorkHoursConfig(workHours); setWorkHours(c); push('Attendance rules saved') }}>Save</Button>
          </div>
        </Card>
      )}

      <Card>
        <CardHeader><CardTitle>Import from CSV</CardTitle></CardHeader>
        <CardContent className="space-y-3 pt-3">
          <p className="text-sm text-ink-muted">Almost every attendance machine's bundled software can export a CSV/Excel report — upload it here as a fallback or bulk-import path.</p>
          <label className="flex cursor-pointer items-center justify-center gap-2 rounded-[var(--radius-md)] border-2 border-dashed border-border py-8 text-sm text-ink-muted transition-colors hover:border-accent hover:text-accent">
            <Upload className="h-4 w-4" /> Click to choose a CSV file
            <input type="file" accept=".csv" className="hidden" onChange={(e) => e.target.files?.[0] && handleImport(e.target.files[0])} />
          </label>
          {importResult && (
            <div className="rounded-[var(--radius-md)] border border-border bg-surface-2 p-4 text-sm">
              <p className="text-ink">Imported <strong>{importResult.imported}</strong> of {importResult.totalRows} rows. {importResult.duplicates ? `${importResult.duplicates} were already stored (duplicates), ` : ''}{importResult.skipped} could not be read.</p>
              {importResult.errors.length > 0 && (
                <ul className="mt-2 list-inside list-disc space-y-0.5 text-xs text-danger">
                  {importResult.errors.map((e, i) => <li key={i}>{e}</li>)}
                </ul>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>Unmatched device codes</CardTitle></CardHeader>
        <CardContent className="space-y-3 pt-3">
          <p className="text-sm text-ink-muted">These codes have punched in from the device but aren't linked to a staff member yet.</p>
          {unmatched.length === 0 ? (
            <p className="py-4 text-center text-sm text-ink-faint">All device codes are linked.</p>
          ) : (
            unmatched.map((u) => (
              <div key={u.employeeCode} className="flex flex-col gap-2 rounded-[var(--radius-md)] border border-border p-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="font-mono text-sm text-ink">{u.employeeCode}</p>
                  <p className="text-xs text-ink-faint">{u.punchCount} punches · last seen {formatDate(u.lastSeen, { hour: 'numeric', minute: '2-digit' })}</p>
                </div>
                <div className="flex items-center gap-2">
                  <Select value={linkTarget[u.employeeCode] ?? ''} onChange={(e) => setLinkTarget((t) => ({ ...t, [u.employeeCode]: e.target.value }))} className="h-9 max-w-[180px]">
                    <option value="">Select staff…</option>
                    {staff.map((s) => <option key={s.id} value={s.id}>{s.firstName} {s.lastName}</option>)}
                  </Select>
                  <Button size="sm" variant="secondary" onClick={() => handleLink(u.employeeCode)}><Link2 className="h-3.5 w-3.5" /> Link</Button>
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>Manual punch entry</CardTitle></CardHeader>
        <CardContent className="pt-3">
          <p className="mb-3 text-sm text-ink-muted">Works regardless of hardware — a fallback for a forgotten card or a machine that's temporarily down.</p>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
            <Select label="Staff" value={manual.staffId} onChange={(e) => setManual({ ...manual, staffId: e.target.value })}>
              <option value="">Select…</option>
              {staff.map((s) => <option key={s.id} value={s.id}>{s.firstName} {s.lastName}</option>)}
            </Select>
            <Input label="Date & time" type="datetime-local" value={manual.punchTime} onChange={(e) => setManual({ ...manual, punchTime: e.target.value })} />
            <Select label="Direction" value={manual.direction} onChange={(e) => setManual({ ...manual, direction: e.target.value as any })}>
              <option value="IN">In</option>
              <option value="OUT">Out</option>
            </Select>
            <div className="flex items-end">
              <Button
                className="w-full"
                disabled={!manual.staffId || !manual.punchTime}
                onClick={async () => {
                  await manualAttendanceEntry(manual.staffId, new Date(manual.punchTime).toISOString(), manual.direction)
                  push('Punch recorded')
                  setManual({ staffId: '', punchTime: '', direction: 'IN' })
                }}
              >
                <Clock className="h-3.5 w-3.5" /> Record
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

function ConfigRow({ label, value, onCopy }: { label: string; value: string; onCopy: (v: string) => void }) {
  return (
    <div>
      <p className="mb-1.5 text-xs font-medium text-ink-muted">{label}</p>
      <div className="flex items-center gap-2">
        <code className="flex-1 truncate rounded-[var(--radius-sm)] border border-border bg-surface-2 px-3 py-2 text-xs text-ink">{value}</code>
        <Button variant="ghost" size="icon" aria-label={`Copy ${label}`} onClick={() => onCopy(value)}><Copy className="h-4 w-4" /></Button>
      </div>
    </div>
  )
}

function CorrectionsTab() {
  const push = useToastStore((st) => st.push)
  const [rows, setRows] = useState<AttendanceCorrectionInfo[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  const load = () => fetchCorrections('PENDING').then(setRows).catch(() => setError('Only managers and admins can review corrections.'))
  useEffect(() => { load() }, [])

  async function act(id: string, action: 'approve' | 'reject') {
    setBusy(id)
    try { await reviewCorrection(id, action); push(action === 'approve' ? 'Approved. The punch was added' : 'Request rejected'); load() }
    catch { push('That action failed.') }
    finally { setBusy(null) }
  }

  if (error) return <Card><EmptyState icon={<Clock className="h-10 w-10" />} title="Not available" description={error} /></Card>
  if (!rows) return <p className="text-sm text-ink-muted">Loading…</p>
  if (rows.length === 0) return <Card><EmptyState icon={<Check className="h-10 w-10" />} title="Nothing waiting" description="Staff requests to add a missed punch appear here." /></Card>
  return (
    <Card className="divide-y divide-border p-0">
      {rows.map((c) => (
        <div key={c.id} className="flex flex-wrap items-center justify-between gap-3 p-4 text-sm">
          <div>
            <p className="font-medium text-ink">{c.staffName ?? 'Staff'} · {new Date(c.proposedTime).toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' })} ({c.direction.toLowerCase()})</p>
            <p className="text-ink-muted">{c.reason}</p>
          </div>
          <div className="flex gap-2">
            <Button size="sm" variant="outline" loading={busy === c.id} onClick={() => act(c.id, 'reject')}><X className="h-3.5 w-3.5" /> Reject</Button>
            <Button size="sm" loading={busy === c.id} onClick={() => act(c.id, 'approve')}><Check className="h-3.5 w-3.5" /> Approve</Button>
          </div>
        </div>
      ))}
    </Card>
  )
}
