import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarDays, Plane } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Input, Textarea } from '@/components/ui/Input'
import { applyForLeave, cancelLeave, fetchCalendarEvents, fetchMyCorrections, fetchMyLeaves, fetchMyStaffProfile, requestPunchCorrection, type AttendanceCorrectionInfo } from '@/lib/api'
import { Select } from '@/components/ui/Input'
import type { CalendarEvent } from '@/lib/api'
import type { StaffLeaveRequest, StaffMember } from '@/lib/types'
import { ApiError } from '@/lib/apiClient'
import { useToastStore } from '@/store/useToastStore'
import { hhmm, toIsoDate } from '@/lib/bookingUtils'
import { STAFF_LEAVE_STATUS_TONE } from '@/lib/statusTone'
import { formatDate } from '@/lib/utils'

/** The signed-in staff member's own view: upcoming shoots and their leave, with leave application. */
export default function MySchedule() {
  const push = useToastStore((s) => s.push)
  const [me, setMe] = useState<StaffMember | null>(null)
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [leaves, setLeaves] = useState<StaffLeaveRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({ from: '', to: '', reason: '' })
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [corrections, setCorrections] = useState<AttendanceCorrectionInfo[]>([])
  const [corr, setCorr] = useState({ time: '', direction: 'IN', reason: '' })
  const [corrError, setCorrError] = useState<string | null>(null)
  const [corrSaving, setCorrSaving] = useState(false)

  async function submitCorrection() {
    setCorrError(null)
    if (!corr.time) return setCorrError('Choose the date and time you should have punched.')
    if (!corr.reason.trim()) return setCorrError('Tell your manager what happened.')
    setCorrSaving(true)
    try {
      await requestPunchCorrection(corr.time.length === 16 ? corr.time + ':00' : corr.time, corr.direction, corr.reason.trim())
      push('Correction request sent to your manager')
      setCorr({ time: '', direction: 'IN', reason: '' })
      fetchMyCorrections().then(setCorrections).catch(() => undefined)
    } catch (e) { setCorrError(e instanceof ApiError ? e.message : 'Could not send the request.') }
    finally { setCorrSaving(false) }
  }

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const profile = await fetchMyStaffProfile()
      setMe(profile)
      const from = new Date(); const to = new Date(); to.setDate(to.getDate() + 30)
      const [ev, lv] = await Promise.all([fetchCalendarEvents(toIsoDate(from), toIsoDate(to)), fetchMyLeaves().catch(() => [])])
      setEvents(ev); setLeaves(lv)
      fetchMyCorrections().then(setCorrections).catch(() => setCorrections([]))
    } catch (e) {
      setError(e instanceof ApiError && e.status === 404 ? 'You do not have a staff profile yet. Ask an admin to create one.' : e instanceof ApiError ? e.message : 'Could not load your schedule.')
    } finally { setLoading(false) }
  }, [])
  useEffect(() => { load() }, [load])

  const myEvents = useMemo(() => {
    if (!me) return []
    const full = `${me.firstName} ${me.lastName}`.trim().toLowerCase()
    return events
      .filter((e) => e.status !== 'CANCELLED' && (e.photographerName ?? '').toLowerCase() === full)
      .sort((a, b) => (a.date + hhmm(a.startTime)).localeCompare(b.date + hhmm(b.startTime)))
  }, [events, me])

  async function submit() {
    if (!me) return
    setFormError(null)
    if (!form.from || !form.to) return setFormError('Choose the first and last day of your leave.')
    if (form.to < form.from) return setFormError('The last day cannot be before the first day.')
    if (form.from < toIsoDate(new Date())) return setFormError('Leave can only be requested for today or later.')
    setSaving(true)
    try {
      await applyForLeave(me.id, form.from, form.to, form.reason.trim() || undefined)
      push('Leave request sent to your manager')
      setForm({ from: '', to: '', reason: '' }); load()
    } catch (e) { setFormError(e instanceof ApiError ? e.message : 'Could not submit your request.') }
    finally { setSaving(false) }
  }

  async function cancel(id: string) {
    try { await cancelLeave(id); push('Leave request cancelled'); load() }
    catch (e) { push(e instanceof ApiError ? e.message : 'Could not cancel that request.') }
  }

  if (loading) return <div className="grid gap-4 md:grid-cols-2"><Skeleton className="h-64" /><Skeleton className="h-64" /></div>
  if (error) return <Card><EmptyState icon={<CalendarDays className="h-10 w-10" />} title="My schedule is unavailable" description={error} action={<Button onClick={load}>Try again</Button>} /></Card>

  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <Card>
        <CardHeader><CardTitle className="flex items-center gap-2"><CalendarDays className="h-4.5 w-4.5 text-accent" /> My shoots · next 30 days</CardTitle></CardHeader>
        <CardContent className="pt-3">
          {myEvents.length === 0 ? <p className="text-sm text-ink-muted">Nothing assigned to you in the next 30 days.</p> : (
            <ul className="space-y-2">
              {myEvents.map((e) => (
                <li key={e.id}>
                  <Link to={`/admin/bookings/${e.id}`} className="block rounded-[var(--radius-md)] border border-border p-3 text-sm hover:border-accent">
                    <p className="font-medium text-ink">{formatDate(e.date, { weekday: 'short', day: 'numeric', month: 'short' })} · {hhmm(e.startTime)}{e.endTime ? `–${hhmm(e.endTime)}` : ''}</p>
                    <p className="text-xs text-ink-muted">{e.clientName} · {e.shootCategory?.replace(/_/g, ' ')}</p>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="flex items-center gap-2"><Plane className="h-4.5 w-4.5 text-accent" /> Leave</CardTitle></CardHeader>
        <CardContent className="space-y-5 pt-3">
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <Input label="From" type="date" min={toIsoDate(new Date())} value={form.from} onChange={(e) => setForm({ ...form, from: e.target.value, to: form.to && form.to < e.target.value ? e.target.value : form.to })} />
              <Input label="To" type="date" min={form.from || toIsoDate(new Date())} value={form.to} onChange={(e) => setForm({ ...form, to: e.target.value })} />
            </div>
            <Textarea label="Reason (optional)" rows={2} maxLength={500} value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} />
            {formError && <p role="alert" className="text-sm text-danger">{formError}</p>}
            <Button onClick={submit} loading={saving}>Request leave</Button>
          </div>
          <div>
            <p className="mb-2 text-sm font-medium text-ink">My requests</p>
            {leaves.length === 0 ? <p className="text-sm text-ink-muted">No leave requests yet.</p> : (
              <ul className="space-y-2">
                {leaves.map((l) => (
                  <li key={l.id} className="flex items-center justify-between gap-3 rounded-[var(--radius-md)] border border-border px-3 py-2 text-sm">
                    <div>
                      <p className="text-ink">{formatDate(l.fromDate)} – {formatDate(l.toDate)} <span className="text-ink-faint">({l.dayCount}d)</span></p>
                      {l.reviewNotes && <p className="text-xs text-ink-faint">Manager: {l.reviewNotes}</p>}
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge tone={STAFF_LEAVE_STATUS_TONE[l.status] as any}>{l.status}</Badge>
                      {(l.status === 'PENDING' || l.status === 'APPROVED') && <Button size="sm" variant="ghost" onClick={() => cancel(l.id)}>Cancel</Button>}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </CardContent>
      </Card>

      <Card className="lg:col-span-2">
        <CardHeader><CardTitle>Missed a punch?</CardTitle></CardHeader>
        <CardContent className="grid gap-5 pt-3 md:grid-cols-2">
          <div className="space-y-3">
            <Input label="Date and time you should have punched" type="datetime-local" max={new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16)} value={corr.time} onChange={(e) => setCorr({ ...corr, time: e.target.value })} />
            <Select label="Direction" value={corr.direction} onChange={(e) => setCorr({ ...corr, direction: e.target.value })}>
              <option value="IN">Check in</option><option value="OUT">Check out</option><option value="UNKNOWN">Not sure</option>
            </Select>
            <Textarea label="What happened?" rows={2} maxLength={500} value={corr.reason} onChange={(e) => setCorr({ ...corr, reason: e.target.value })} />
            {corrError && <p role="alert" className="text-sm text-danger">{corrError}</p>}
            <Button onClick={submitCorrection} loading={corrSaving}>Send to manager</Button>
          </div>
          <div>
            <p className="mb-2 text-sm font-medium text-ink">My requests</p>
            {corrections.length === 0 ? <p className="text-sm text-ink-muted">No requests yet.</p> : (
              <ul className="space-y-2">
                {corrections.map((c) => (
                  <li key={c.id} className="rounded-[var(--radius-md)] border border-border px-3 py-2 text-sm">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-ink">{new Date(c.proposedTime).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })} · {c.direction.toLowerCase()}</span>
                      <Badge tone={STAFF_LEAVE_STATUS_TONE[c.status] as any}>{c.status}</Badge>
                    </div>
                    {c.reviewNotes && <p className="text-xs text-ink-faint">Manager: {c.reviewNotes}</p>}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
