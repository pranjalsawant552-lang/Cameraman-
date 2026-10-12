import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Ban, CalendarOff, ChevronLeft, ChevronRight } from 'lucide-react'
import { Card, Badge, Skeleton } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { blockDates, fetchBlockedDates, fetchCalendarEvents, unblockDate, type CalendarEvent } from '@/lib/api'
import type { BlockedDate } from '@/lib/types'
import { ApiError } from '@/lib/apiClient'
import { useAuthStore } from '@/store/useAuthStore'
import { useToastStore } from '@/store/useToastStore'
import { isAdmin } from '@/lib/access'
import { hhmm, monthCells, toIsoDate } from '@/lib/bookingUtils'
import { BOOKING_STATUS_TONE } from '@/lib/statusTone'
import { cn, formatDate } from '@/lib/utils'

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

export default function Calendar() {
  const push = useToastStore((s) => s.push)
  const roles = useAuthStore((s) => s.user?.roles)
  const canBlock = isAdmin(roles) || (roles ?? []).includes('MANAGER')

  const [month, setMonth] = useState(() => { const n = new Date(); return new Date(n.getFullYear(), n.getMonth(), 1) })
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [blocked, setBlocked] = useState<BlockedDate[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<string | null>(toIsoDate(new Date()))
  const [reason, setReason] = useState('')
  const [rangeTo, setRangeTo] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    const from = toIsoDate(month)
    const to = toIsoDate(new Date(month.getFullYear(), month.getMonth() + 1, 0))
    setLoading(true); setError(null)
    Promise.all([fetchCalendarEvents(from, to), fetchBlockedDates(from, to)])
      .then(([e, b]) => { setEvents(e.filter((x) => x.status !== 'CANCELLED')); setBlocked(b) })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load the calendar.'))
      .finally(() => setLoading(false))
  }, [month])
  useEffect(load, [load])

  const byDay = useMemo(() => {
    const m = new Map<string, CalendarEvent[]>()
    events.forEach((e) => m.set(e.date, [...(m.get(e.date) ?? []), e]))
    m.forEach((list) => list.sort((a, b) => hhmm(a.startTime).localeCompare(hhmm(b.startTime))))
    return m
  }, [events])
  const blockedMap = useMemo(() => new Map(blocked.map((b) => [b.date, b])), [blocked])

  const dayEvents = selected ? byDay.get(selected) ?? [] : []
  const dayBlocked = selected ? blockedMap.get(selected) : undefined

  async function block() {
    if (!selected) return
    const dates: string[] = []
    const end = rangeTo && rangeTo >= selected ? rangeTo : selected
    for (let d = new Date(selected + 'T00:00'); toIsoDate(d) <= end && dates.length < 60; d.setDate(d.getDate() + 1)) dates.push(toIsoDate(d))
    setBusy(true)
    try {
      await blockDates(dates, reason.trim() || undefined)
      push(dates.length === 1 ? 'Date blocked' : `${dates.length} dates blocked`)
      setReason(''); setRangeTo(''); load()
    } catch (e) { push(e instanceof ApiError ? e.message : 'Could not block those dates.') }
    finally { setBusy(false) }
  }

  async function unblock() {
    if (!selected) return
    setBusy(true)
    try { await unblockDate(selected); push('Date reopened'); load() }
    catch (e) { push(e instanceof ApiError ? e.message : 'Could not reopen this date.') }
    finally { setBusy(false) }
  }

  const cells = monthCells(month.getFullYear(), month.getMonth())
  const todayIso = toIsoDate(new Date())

  return (
    <div className="grid gap-5 lg:grid-cols-[1fr_340px]">
      <Card className="p-4">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-display text-xl text-ink">{month.toLocaleString('en-IN', { month: 'long', year: 'numeric' })}</h2>
          <div className="flex gap-1">
            <Button variant="ghost" size="icon" aria-label="Previous month" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}><ChevronLeft className="h-4 w-4" /></Button>
            <Button variant="outline" size="sm" onClick={() => { const n = new Date(); setMonth(new Date(n.getFullYear(), n.getMonth(), 1)); setSelected(todayIso) }}>Today</Button>
            <Button variant="ghost" size="icon" aria-label="Next month" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}><ChevronRight className="h-4 w-4" /></Button>
          </div>
        </div>
        {error && <p role="alert" className="mb-3 text-sm text-danger">{error} <button className="underline" onClick={load}>Retry</button></p>}
        {loading ? <Skeleton className="h-96" /> : (
          <>
            <div className="grid grid-cols-7 gap-px text-center text-xs text-ink-faint">{WEEKDAYS.map((d) => <span key={d} className="py-1">{d}</span>)}</div>
            <div className="grid grid-cols-7 gap-px overflow-hidden rounded-[var(--radius-md)] border border-border bg-border">
              {cells.map((d, i) => {
                if (!d) return <div key={i} className="min-h-20 bg-surface-2/50" />
                const iso = toIsoDate(d)
                const list = byDay.get(iso) ?? []
                const closed = blockedMap.has(iso)
                return (
                  <button key={iso} type="button" onClick={() => setSelected(iso)} aria-label={`${formatDate(d, { day: 'numeric', month: 'long' })}: ${list.length} bookings${closed ? ', closed' : ''}`}
                    className={cn('flex min-h-20 flex-col items-start gap-1 bg-surface p-1.5 text-left text-xs transition-colors hover:bg-surface-2',
                      closed && 'bg-danger-bg/40', selected === iso && 'ring-2 ring-inset ring-accent')}>
                    <span className={cn('flex h-5 w-5 items-center justify-center rounded-full', iso === todayIso && 'bg-accent text-accent-ink')}>{d.getDate()}</span>
                    {closed && <CalendarOff className="h-3 w-3 text-danger" />}
                    {list.slice(0, 2).map((e) => <span key={e.id} className="w-full truncate rounded bg-accent/15 px-1 text-[10px] text-ink">{hhmm(e.startTime)} {e.clientName ?? e.title}</span>)}
                    {list.length > 2 && <span className="text-[10px] text-ink-faint">+{list.length - 2} more</span>}
                  </button>
                )
              })}
            </div>
          </>
        )}
      </Card>

      <Card className="h-fit p-4">
        <h3 className="font-medium text-ink">{selected ? formatDate(selected, { weekday: 'long', day: 'numeric', month: 'long' }) : 'Select a day'}</h3>
        {dayBlocked && <Badge tone="danger" className="mt-2"><Ban className="mr-1 h-3 w-3" /> Closed{dayBlocked.reason ? `: ${dayBlocked.reason}` : ''}</Badge>}
        <ul className="mt-3 space-y-2">
          {dayEvents.length === 0 && <li className="text-sm text-ink-muted">No bookings.</li>}
          {dayEvents.map((e) => (
            <li key={e.id}>
              <Link to={`/admin/bookings/${e.id}`} className="block rounded-[var(--radius-md)] border border-border p-2.5 text-sm hover:border-accent">
                <div className="flex items-center justify-between gap-2"><span className="font-medium text-ink">{e.clientName ?? e.title}</span><Badge tone={BOOKING_STATUS_TONE[e.status as keyof typeof BOOKING_STATUS_TONE] as any}>{e.status}</Badge></div>
                <p className="text-xs text-ink-faint">{hhmm(e.startTime)}{e.endTime ? `–${hhmm(e.endTime)}` : ''} · {e.shootCategory?.replace(/_/g, ' ')}{e.photographerName ? ` · ${e.photographerName}` : ''}</p>
              </Link>
            </li>
          ))}
        </ul>

        {canBlock && selected && (
          <div className="mt-5 space-y-3 border-t border-border pt-4">
            {dayBlocked ? (
              <Button variant="outline" onClick={unblock} loading={busy} className="w-full">Reopen this date</Button>
            ) : (
              <>
                <p className="text-sm font-medium text-ink">Close the studio</p>
                {dayEvents.length > 0 && <p className="text-xs text-warning">{dayEvents.length} booking(s) already exist on this day. Closing won't cancel them.</p>}
                <Input label="Reason (optional)" value={reason} maxLength={100} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Diwali holiday" />
                <Input label="Through (optional, for a range)" type="date" min={selected} value={rangeTo} onChange={(e) => setRangeTo(e.target.value)} />
                <Button onClick={block} loading={busy} className="w-full"><Ban className="h-4 w-4" /> Block {rangeTo && rangeTo > selected ? 'dates' : 'this date'}</Button>
              </>
            )}
          </div>
        )}
      </Card>
    </div>
  )
}
