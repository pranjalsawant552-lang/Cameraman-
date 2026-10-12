import { useCallback, useEffect, useState } from 'react'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, Badge, EmptyState } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import {
  addHoliday, createShift, deleteHoliday, deleteShift, fetchHolidays, fetchSchedules, fetchShifts,
  updateSchedule, updateShift, type ShiftInput,
} from '@/lib/api'
import type { AttendanceHolidayRow, StaffScheduleRow, WorkShift } from '@/lib/types'
import { ApiError } from '@/lib/apiClient'
import { formatCurrency, formatDate } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'

export const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
const cap = (d: string) => d.charAt(0) + d.slice(1, 3).toLowerCase()

function errMsg(e: unknown, fallback: string) {
  return e instanceof ApiError && e.message ? e.message : fallback
}

/** Shifts, per-staff schedules (shift, weekly off, pay) and studio holidays. `canSeePay` hides salary for non-payroll roles. */
export function ShiftsPanel({ canSeePay }: { canSeePay: boolean }) {
  const push = useToastStore((s) => s.push)
  const [shifts, setShifts] = useState<WorkShift[] | null>(null)
  const [schedules, setSchedules] = useState<StaffScheduleRow[] | null>(null)
  const [holidays, setHolidays] = useState<AttendanceHolidayRow[]>([])
  const [year, setYear] = useState(new Date().getFullYear())
  const [shiftForm, setShiftForm] = useState<{ id?: string; data: ShiftInput } | null>(null)
  const [editing, setEditing] = useState<StaffScheduleRow | null>(null)
  const [newHoliday, setNewHoliday] = useState({ date: '', name: '' })
  const [busy, setBusy] = useState(false)

  const loadShifts = useCallback(() => fetchShifts().then(setShifts).catch(() => setShifts([])), [])
  const loadSchedules = useCallback(() => {
    if (!canSeePay) { setSchedules([]); return }
    fetchSchedules().then(setSchedules).catch(() => setSchedules([]))
  }, [canSeePay])
  const loadHolidays = useCallback(() => fetchHolidays(year).then(setHolidays).catch(() => setHolidays([])), [year])

  useEffect(() => { loadShifts(); loadSchedules() }, [loadShifts, loadSchedules])
  useEffect(() => { loadHolidays() }, [loadHolidays])

  async function saveShift() {
    if (!shiftForm) return
    setBusy(true)
    try {
      if (shiftForm.id) await updateShift(shiftForm.id, shiftForm.data)
      else await createShift(shiftForm.data)
      push('Shift saved')
      setShiftForm(null)
      loadShifts(); loadSchedules()
    } catch (e) { push(errMsg(e, 'Could not save the shift.')) } finally { setBusy(false) }
  }

  async function removeShift(s: WorkShift) {
    if (!window.confirm(`Delete the "${s.name}" shift?`)) return
    try { await deleteShift(s.id); push('Shift deleted'); loadShifts() } catch (e) { push(errMsg(e, 'Could not delete the shift.')) }
  }

  async function saveHoliday() {
    if (!newHoliday.date || !newHoliday.name.trim()) return
    try {
      await addHoliday(newHoliday.date, newHoliday.name.trim())
      setNewHoliday({ date: '', name: '' })
      push('Holiday added')
      loadHolidays()
    } catch (e) { push(errMsg(e, 'Could not add the holiday.')) }
  }

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Shifts</CardTitle>
          <Button size="sm" onClick={() => setShiftForm({ data: { name: '', startTime: '09:30', endTime: '18:30', graceMinutes: 15, active: true } })}>
            <Plus className="h-3.5 w-3.5" /> New shift
          </Button>
        </CardHeader>
        <CardContent className="pt-3">
          <p className="mb-3 text-sm text-ink-muted">Staff without a shift follow the studio default set under Devices &amp; import.</p>
          {shifts === null ? <p className="text-sm text-ink-muted">Loading…</p> : shifts.length === 0 ? (
            <EmptyState title="No shifts yet" description="Add a shift, then assign it to staff below." />
          ) : (
            <ul className="divide-y divide-border">
              {shifts.map((s) => (
                <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 py-2.5 text-sm">
                  <div>
                    <p className="font-medium text-ink">{s.name} {!s.active && <Badge tone="neutral">Inactive</Badge>}</p>
                    <p className="text-xs text-ink-faint">{s.startTime}–{s.endTime} · {s.graceMinutes} min grace · {s.staffCount} staff</p>
                  </div>
                  <div className="flex gap-1">
                    <Button size="icon" variant="ghost" aria-label={`Edit ${s.name}`}
                      onClick={() => setShiftForm({ id: s.id, data: { name: s.name, startTime: s.startTime, endTime: s.endTime, graceMinutes: s.graceMinutes, active: s.active } })}>
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button size="icon" variant="ghost" aria-label={`Delete ${s.name}`} onClick={() => removeShift(s)}><Trash2 className="h-4 w-4" /></Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {canSeePay && (
        <Card className="overflow-hidden p-0">
          <CardHeader><CardTitle>Staff schedules &amp; pay</CardTitle></CardHeader>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <caption className="sr-only">Shift, weekly off days and pay for each staff member</caption>
              <thead>
                <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                  <th scope="col" className="px-6 py-3">Staff</th>
                  <th scope="col" className="px-4 py-3">Shift</th>
                  <th scope="col" className="px-4 py-3">Weekly off</th>
                  <th scope="col" className="px-4 py-3">Salary / month</th>
                  <th scope="col" className="px-4 py-3">OT / hour</th>
                  <th scope="col" className="px-4 py-3"><span className="sr-only">Edit</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {(schedules ?? []).map((r) => (
                  <tr key={r.staffId}>
                    <td className="px-6 py-3"><p className="font-medium text-ink">{r.staffName}</p><p className="text-xs text-ink-faint">{r.employeeId ?? '—'}</p></td>
                    <td className="px-4 py-3 text-ink-muted">{r.shiftName ?? 'Studio default'} <span className="text-xs text-ink-faint">({r.effectiveStart}–{r.effectiveEnd})</span></td>
                    <td className="px-4 py-3 text-ink-muted">{r.weeklyOffDays.map(cap).join(', ')}</td>
                    <td className="px-4 py-3 text-ink-muted">{r.monthlySalary != null ? formatCurrency(r.monthlySalary) : <span className="text-ink-faint">Not set</span>}</td>
                    <td className="px-4 py-3 text-ink-muted">{r.overtimeRatePerHour != null ? formatCurrency(r.overtimeRatePerHour) : '—'}</td>
                    <td className="px-4 py-3 text-right"><Button size="sm" variant="outline" onClick={() => setEditing(r)}>Edit</Button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Holidays</CardTitle>
          <Input aria-label="Holiday year" type="number" className="h-9 w-24" value={year} onChange={(e) => setYear(Number(e.target.value) || year)} />
        </CardHeader>
        <CardContent className="space-y-3 pt-3">
          <p className="text-sm text-ink-muted">Holidays are paid, never marked absent, and work done on them counts as overtime.</p>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
            <Input label="Date" type="date" value={newHoliday.date} onChange={(e) => setNewHoliday({ ...newHoliday, date: e.target.value })} />
            <Input label="Name" value={newHoliday.name} maxLength={100} onChange={(e) => setNewHoliday({ ...newHoliday, name: e.target.value })} />
            <Button onClick={saveHoliday} disabled={!newHoliday.date || !newHoliday.name.trim()}><Plus className="h-3.5 w-3.5" /> Add</Button>
          </div>
          {holidays.length === 0 ? <p className="py-3 text-center text-sm text-ink-faint">No holidays set for {year}.</p> : (
            <ul className="divide-y divide-border">
              {holidays.map((h) => (
                <li key={h.id} className="flex items-center justify-between py-2 text-sm">
                  <span className="text-ink">{formatDate(h.date, { weekday: 'short', day: 'numeric', month: 'short' })} · {h.name}</span>
                  <Button size="icon" variant="ghost" aria-label={`Remove ${h.name}`}
                    onClick={async () => { try { await deleteHoliday(h.id); loadHolidays() } catch (e) { push(errMsg(e, 'Could not remove it.')) } }}>
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Modal open={!!shiftForm} onClose={() => setShiftForm(null)} title={shiftForm?.id ? 'Edit shift' : 'New shift'}
        footer={<><Button variant="outline" onClick={() => setShiftForm(null)}>Cancel</Button><Button loading={busy} disabled={!shiftForm?.data.name.trim()} onClick={saveShift}>Save</Button></>}>
        {shiftForm && (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="sm:col-span-2"><Input label="Name" value={shiftForm.data.name} maxLength={80} onChange={(e) => setShiftForm({ ...shiftForm, data: { ...shiftForm.data, name: e.target.value } })} /></div>
            <Input label="Starts" type="time" value={shiftForm.data.startTime} onChange={(e) => setShiftForm({ ...shiftForm, data: { ...shiftForm.data, startTime: e.target.value } })} />
            <Input label="Ends" type="time" value={shiftForm.data.endTime} onChange={(e) => setShiftForm({ ...shiftForm, data: { ...shiftForm.data, endTime: e.target.value } })} />
            <Input label="Grace (minutes)" type="number" min={0} max={240} value={shiftForm.data.graceMinutes ?? 0} onChange={(e) => setShiftForm({ ...shiftForm, data: { ...shiftForm.data, graceMinutes: Number(e.target.value) } })} />
            <label className="flex items-center gap-2 self-end pb-2 text-sm text-ink">
              <input type="checkbox" checked={shiftForm.data.active ?? true} onChange={(e) => setShiftForm({ ...shiftForm, data: { ...shiftForm.data, active: e.target.checked } })} /> Active
            </label>
          </div>
        )}
      </Modal>

      {editing && (
        <ScheduleModal row={editing} shifts={(shifts ?? []).filter((s) => s.active || s.id === editing.shiftId)}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); push('Schedule saved'); loadSchedules(); loadShifts() }} />
      )}
    </div>
  )
}

function ScheduleModal({ row, shifts, onClose, onSaved }: { row: StaffScheduleRow; shifts: WorkShift[]; onClose: () => void; onSaved: () => void }) {
  const push = useToastStore((s) => s.push)
  const [shiftId, setShiftId] = useState(row.shiftId ?? '')
  const [off, setOff] = useState<string[]>(row.weeklyOffDays)
  const [salary, setSalary] = useState(row.monthlySalary?.toString() ?? '')
  const [ot, setOt] = useState(row.overtimeRatePerHour?.toString() ?? '')
  const [busy, setBusy] = useState(false)

  const toggle = (d: string) => setOff((cur) => (cur.includes(d) ? cur.filter((x) => x !== d) : [...cur, d]))
  const num = (v: string) => (v.trim() === '' ? null : Math.max(0, Number(v)))

  async function save() {
    setBusy(true)
    try {
      await updateSchedule(row.staffId, { shiftId: shiftId || null, weeklyOffDays: off, monthlySalary: num(salary), overtimeRatePerHour: num(ot) })
      onSaved()
    } catch (e) { push(errMsg(e, 'Could not save the schedule.')) } finally { setBusy(false) }
  }

  return (
    <Modal open onClose={onClose} title={`Schedule · ${row.staffName}`}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button loading={busy} onClick={save}>Save</Button></>}>
      <div className="space-y-4">
        <Select label="Shift" value={shiftId} onChange={(e) => setShiftId(e.target.value)}>
          <option value="">Studio default</option>
          {shifts.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.startTime}–{s.endTime})</option>)}
        </Select>
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium text-ink-muted">Weekly off days</legend>
          <div className="flex flex-wrap gap-2">
            {DAYS.map((d) => (
              <label key={d} className="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-sm)] border border-border px-2.5 py-1.5 text-sm text-ink has-[:checked]:border-accent has-[:checked]:bg-accent/10">
                <input type="checkbox" checked={off.includes(d)} onChange={() => toggle(d)} /> {cap(d)}
              </label>
            ))}
          </div>
          <p className="mt-1 text-xs text-ink-faint">Clear every box to follow the studio default.</p>
        </fieldset>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="Monthly salary" type="number" min={0} inputMode="decimal" value={salary} onChange={(e) => setSalary(e.target.value)} />
          <Input label="Overtime rate per hour" type="number" min={0} inputMode="decimal" value={ot} onChange={(e) => setOt(e.target.value)} />
        </div>
      </div>
    </Modal>
  )
}
