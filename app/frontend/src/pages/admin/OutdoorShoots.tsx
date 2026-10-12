import { useEffect, useState } from 'react'
import { MapPinned, Plus, CloudRain } from 'lucide-react'
import { Card, Badge, EmptyState, Skeleton } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { Tabs } from '@/components/ui/Tabs'
import { fetchOutdoorShoots, cancelOutdoorShoot, rescheduleOutdoorShoot, type OutdoorShoot } from '@/lib/api'
import { formatCurrency, formatDate } from '@/lib/utils'
import { useToastStore } from '@/store/useToastStore'
import { NewOutdoorShootModal } from '@/components/outdoor/NewOutdoorShootModal'
import { RescheduleModal } from '@/components/shared/RescheduleModal'
import { ApiError } from '@/lib/apiClient'
import { LinkedBookings } from '@/components/shared/LinkedBookings'

const STATUS_TONE: Record<string, string> = { CONFIRMED: 'success', PENDING: 'warning', IN_PROGRESS: 'info', COMPLETED: 'info', CANCELLED: 'danger', RESCHEDULED: 'neutral', NO_SHOW: 'danger' }
const TABS = [{ id: 'ALL', label: 'All' }, { id: 'PENDING', label: 'Pending' }, { id: 'CONFIRMED', label: 'Confirmed' }, { id: 'COMPLETED', label: 'Completed' }, { id: 'CANCELLED', label: 'Cancelled' }]

export default function OutdoorShoots() {
  const [status, setStatus] = useState('ALL')
  const [shoots, setShoots] = useState<OutdoorShoot[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)
  const [reschedulingShoot, setReschedulingShoot] = useState<OutdoorShoot | null>(null)
  const push = useToastStore((s) => s.push)

  function reload() {
    setLoading(true)
    fetchOutdoorShoots({ status: status === 'ALL' ? '' : status }).then((res) => { setShoots(res); setLoading(false) })
  }
  useEffect(reload, [status])

  async function handleCancel(shoot: OutdoorShoot) {
    try {
      await cancelOutdoorShoot(shoot.id, 'Cancelled by staff')
      push('Shoot cancelled')
      reload()
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not cancel the shoot.')
    }
  }

  return (
    <div className="space-y-5">
      <p className="max-w-2xl text-sm text-ink-muted">
        On-location work — weddings, pre-wedding, events and destination shoots, with venue and travel details
        tracked separately from studio bookings.
      </p>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Tabs tabs={TABS} active={status} onChange={setStatus} />
        <Button onClick={() => setShowNew(true)}><Plus className="h-4 w-4" /> New outdoor shoot</Button>
      </div>

      {loading ? (
        <div className="space-y-3">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-20 rounded-[var(--radius-lg)]" />)}</div>
      ) : shoots.length === 0 ? (
        <Card><EmptyState icon={<MapPinned className="h-10 w-10" />} title="No outdoor shoots yet" description="Schedule a wedding, event or destination shoot." /></Card>
      ) : (
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                <th scope="col" className="px-6 py-3">Shoot</th>
                <th scope="col" className="px-4 py-3">Client</th>
                <th scope="col" className="px-4 py-3">Venue</th>
                <th scope="col" className="px-4 py-3">Date</th>
                <th scope="col" className="px-4 py-3">Amount</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {shoots.map((s) => (
                <tr key={s.id} className="transition-colors hover:bg-surface-2">
                  <td className="px-6 py-3.5 font-medium text-ink">{s.shootNumber}</td>
                  <td className="px-4 py-3.5 text-ink-muted">{s.clientName} <span className="text-ink-faint">· {s.category.replace('_', ' ')}</span></td>
                  <td className="px-4 py-3.5 text-ink-muted">
                    {s.venueCity ?? '—'}
                    {s.weatherDependent && <CloudRain className="ml-1.5 inline h-3.5 w-3.5 text-ink-faint" />}
                  </td>
                  <td className="px-4 py-3.5 text-ink-muted">{formatDate(s.shootDate)} {s.startTime}</td>
                  <td className="px-4 py-3.5 text-ink">{formatCurrency(s.totalAmount)}</td>
                  <td className="px-4 py-3.5"><Badge tone={STATUS_TONE[s.status] as any}>{s.status.replace('_', ' ')}</Badge></td>
                  <td className="px-4 py-3.5 text-right">
                    {s.status !== 'CANCELLED' && s.status !== 'COMPLETED' && (
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="ghost" onClick={() => setReschedulingShoot(s)}>Reschedule</Button>
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

      <LinkedBookings locationType="OUTDOOR" status={status} />

      <NewOutdoorShootModal open={showNew} onClose={() => setShowNew(false)} onCreated={reload} />
      {reschedulingShoot && (
        <RescheduleModal
          open={!!reschedulingShoot}
          title={`Reschedule ${reschedulingShoot.shootNumber}`}
          currentDate={reschedulingShoot.shootDate}
          currentStartTime={reschedulingShoot.startTime}
          onClose={() => setReschedulingShoot(null)}
          onReschedule={async (date, startTime, endTime, reason) => {
            await rescheduleOutdoorShoot(reschedulingShoot.id, date, startTime, endTime, reason)
            reload()
          }}
        />
      )}
    </div>
  )
}
