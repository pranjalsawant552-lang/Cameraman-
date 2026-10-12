import { useEffect, useState } from 'react'
import { Aperture, Plus } from 'lucide-react'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Tabs } from '@/components/ui/Tabs'
import { fetchStudioSessions, cancelStudioSession, rescheduleStudioSession, type StudioSession } from '@/lib/api'
import { formatCurrency, formatDate } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'
import { NewStudioSessionModal } from '@/components/studio/NewStudioSessionModal'
import { RescheduleModal } from '@/components/shared/RescheduleModal'
import { ApiError } from '@/lib/apiClient'
import { LinkedBookings } from '@/components/shared/LinkedBookings'

const STATUS_TONE: Record<string, string> = { CONFIRMED: 'success', PENDING: 'warning', COMPLETED: 'info', CANCELLED: 'danger', RESCHEDULED: 'neutral', NO_SHOW: 'danger' }
const TABS = [{ id: 'ALL', label: 'All' }, { id: 'CONFIRMED', label: 'Confirmed' }, { id: 'COMPLETED', label: 'Completed' }, { id: 'CANCELLED', label: 'Cancelled' }]

export default function StudioSessions() {
  const [status, setStatus] = useState('ALL')
  const [sessions, setSessions] = useState<StudioSession[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)
  const [reschedulingSession, setReschedulingSession] = useState<StudioSession | null>(null)
  const push = useToastStore((s) => s.push)

  function reload() {
    setLoading(true)
    fetchStudioSessions({ status: status === 'ALL' ? '' : status }).then((res) => { setSessions(res); setLoading(false) })
  }
  useEffect(reload, [status])

  async function handleCancel(session: StudioSession) {
    try {
      await cancelStudioSession(session.id, 'Cancelled by staff')
      push('Session cancelled')
      reload()
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not cancel the session.')
    }
  }

  return (
    <div className="space-y-5">
      <p className="max-w-2xl text-sm text-ink-muted">
        Studio-room bookings — portraits, passport photos, product shoots and other in-studio work.
        For weddings, events and other on-location shoots, use Outdoor Shoots instead.
      </p>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Tabs tabs={TABS} active={status} onChange={setStatus} />
        <Button onClick={() => setShowNew(true)}><Plus className="h-4 w-4" /> New studio session</Button>
      </div>

      {loading ? (
        <div className="space-y-3">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-20 rounded-[var(--radius-lg)]" />)}</div>
      ) : sessions.length === 0 ? (
        <Card><EmptyState icon={<Aperture className="h-10 w-10" />} title="No studio sessions yet" description="Book a room for a portrait, passport photo or product shoot." /></Card>
      ) : (
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                <th scope="col" className="px-6 py-3">Session</th>
                <th scope="col" className="px-4 py-3">Client</th>
                <th scope="col" className="px-4 py-3">Room</th>
                <th scope="col" className="px-4 py-3">Date</th>
                <th scope="col" className="px-4 py-3">Amount</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {sessions.map((s) => (
                <tr key={s.id} className="transition-colors hover:bg-surface-2">
                  <td className="px-6 py-3.5 font-medium text-ink">{s.sessionNumber}</td>
                  <td className="px-4 py-3.5 text-ink-muted">{s.clientName}{s.packageName ? ` · ${s.packageName}` : ''}</td>
                  <td className="px-4 py-3.5 text-ink-muted">{s.studioRoom ?? '—'}</td>
                  <td className="px-4 py-3.5 text-ink-muted">{formatDate(s.sessionDate)} {s.startTime}</td>
                  <td className="px-4 py-3.5 text-ink">{formatCurrency(s.totalAmount)}</td>
                  <td className="px-4 py-3.5"><Badge tone={STATUS_TONE[s.status] as any}>{s.status.replace('_', ' ')}</Badge></td>
                  <td className="px-4 py-3.5 text-right">
                    {s.status !== 'CANCELLED' && s.status !== 'COMPLETED' && (
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="ghost" onClick={() => setReschedulingSession(s)}>Reschedule</Button>
                        <Button size="sm" variant="ghost" onClick={() => handleCancel(s)}>Cancel</Button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
</div>
        </Card>
      )}

      <LinkedBookings locationType="INDOOR" status={status} />

      <NewStudioSessionModal open={showNew} onClose={() => setShowNew(false)} onCreated={reload} />
      {reschedulingSession && (
        <RescheduleModal
          open={!!reschedulingSession}
          title={`Reschedule ${reschedulingSession.sessionNumber}`}
          currentDate={reschedulingSession.sessionDate}
          currentStartTime={reschedulingSession.startTime}
          onClose={() => setReschedulingSession(null)}
          onReschedule={async (date, startTime, endTime, reason) => {
            await rescheduleStudioSession(reschedulingSession.id, date, startTime, endTime, reason)
            reload()
          }}
        />
      )}
    </div>
  )
}
