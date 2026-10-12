import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Card, Badge, Skeleton } from '@/components/ui/primitives'
import { fetchBookings } from '@/lib/api'
import { formatCurrency, formatDate } from '@/lib/utils'
import { BOOKING_STATUS_TONE } from '@/lib/statusTone'
import type { Booking } from '@/lib/types'

/**
 * Bookings made from the Bookings page with the given location type. Indoor/Outdoor modules used to list
 * only their own records, so an INDOOR booking never showed up in the Indoor module.
 */
export function LinkedBookings({ locationType, status, refreshKey = 0 }: { locationType: 'INDOOR' | 'OUTDOOR'; status: string; refreshKey?: number }) {
  const [rows, setRows] = useState<Booking[] | null>(null)
  useEffect(() => {
    setRows(null)
    fetchBookings({ locationType, status: status === 'ALL' ? '' : status, size: 200 })
      .then((res) => setRows(res.content))
      .catch(() => setRows([]))
  }, [locationType, status, refreshKey])

  if (rows && rows.length === 0) return null
  const label = locationType === 'INDOOR' ? 'Indoor' : 'Outdoor'
  return (
    <section className="space-y-2" aria-label={`${label} bookings`}>
      <h2 className="text-sm font-semibold text-ink">{label} bookings <span className="font-normal text-ink-muted">· made from the Bookings page</span></h2>
      {rows === null ? <Skeleton className="h-16 rounded-[var(--radius-lg)]" /> : (
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
                  <th scope="col" className="px-6 py-3">Booking</th>
                  <th scope="col" className="px-4 py-3">Client</th>
                  <th scope="col" className="px-4 py-3">Date</th>
                  <th scope="col" className="px-4 py-3">Amount</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {rows.map((b) => (
                  <tr key={b.id} className="transition-colors hover:bg-surface-2">
                    <td className="px-6 py-3.5 font-medium"><Link className="text-accent hover:underline" to={`/admin/bookings/${b.id}`}>{b.bookingNumber}</Link></td>
                    <td className="px-4 py-3.5 text-ink-muted">{b.clientName}{b.packageName ? ` · ${b.packageName}` : ''}</td>
                    <td className="px-4 py-3.5 text-ink-muted">{formatDate(b.bookingDate)}</td>
                    <td className="px-4 py-3.5 text-ink">{formatCurrency(b.totalAmount)}</td>
                    <td className="px-4 py-3.5"><Badge tone={BOOKING_STATUS_TONE[b.status] as never}>{b.status.replace('_', ' ')}</Badge></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </section>
  )
}
