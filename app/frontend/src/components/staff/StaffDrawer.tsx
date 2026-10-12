import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarCheck2, Mail, Phone } from 'lucide-react'
import { Drawer } from '@/components/ui/Drawer'
import { Avatar, Badge, Skeleton } from '@/components/ui/primitives'
import { fetchStaffOverview, type StaffOverview, type StaffShoot } from '@/lib/api'
import { formatDate } from '@/lib/utils'

const monthLabel = (y: number, m: number) => new Date(y, m - 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })

/** Admin staff detail: contact, shoots, this month's attendance and recent leave. */
export function StaffDrawer({ staffId, onClose }: { staffId: string | null; onClose: () => void }) {
  const [data, setData] = useState<StaffOverview | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    if (!staffId) return
    let live = true
    setData(null); setError(false)
    fetchStaffOverview(staffId).then((d) => { if (live) setData(d) }).catch(() => { if (live) setError(true) })
    return () => { live = false }
  }, [staffId])

  const p = data?.profile
  const name = p ? `${p.firstName} ${p.lastName}`.trim() : 'Staff member'
  const ot = data ? Math.round((data.overtimeMinutes / 60) * 10) / 10 : 0

  return (
    <Drawer open={!!staffId} onClose={onClose} title={name} subtitle={p ? `${p.designation || 'Staff'} · ${p.employeeId}` : undefined}
      footer={staffId ? <Link to={`/admin/attendance?staffId=${staffId}`} className="inline-flex items-center gap-1.5 text-sm font-medium text-accent hover:underline"><CalendarCheck2 className="h-4 w-4" /> Open full attendance</Link> : undefined}>
      {error && <p className="text-sm text-danger">Could not load this staff member.</p>}
      {!data && !error && <div className="space-y-3"><Skeleton className="h-16" /><Skeleton className="h-32" /><Skeleton className="h-32" /></div>}
      {data && p && (
        <div className="space-y-6">
          <div className="flex items-center gap-3">
            <Avatar src={p.profilePhotoUrl} name={name} size={48} />
            <div className="flex flex-wrap gap-1.5">
              <Badge tone="neutral">{p.department}</Badge>
              <Badge tone={p.availableForBooking ? 'success' : 'neutral'}>{p.availableForBooking ? 'Taking bookings' : 'Not taking bookings'}</Badge>
              {p.userStatus && p.userStatus !== 'ACTIVE' && <Badge tone="danger">Deactivated</Badge>}
            </div>
          </div>
          <ul className="space-y-1.5 text-sm text-ink-muted">
            <li className="flex items-center gap-2"><Mail className="h-4 w-4 text-ink-faint" /> <a className="hover:text-accent" href={`mailto:${p.email}`}>{p.email}</a></li>
            {p.mobile && <li className="flex items-center gap-2"><Phone className="h-4 w-4 text-ink-faint" /> <a className="hover:text-accent" href={`tel:${p.mobile}`}>{p.mobile}</a></li>}
            <li className="text-xs text-ink-faint">Joined {formatDate(p.joiningDate)} · up to {p.maxBookingsPerDay} shoots a day</li>
          </ul>

          <dl className="grid grid-cols-2 gap-3 text-sm">
            <Tile label="Shoots" value={`${data.totalShoots}`} hint={`${data.completedShoots} completed`} />
            <Tile label="Tasks" value={`${p.completedTasksCount}/${p.assignedTasksCount}`} hint="completed" />
          </dl>

          <section>
            <h3 className="mb-1 text-xs font-medium uppercase tracking-wide text-ink-faint">Attendance · {monthLabel(data.attendanceYear, data.attendanceMonth)}</h3>
            <dl className="grid grid-cols-3 gap-2 text-center text-sm">
              <Tile label="Present" value={`${data.presentDays}`} />
              <Tile label="Late" value={`${data.lateDays}`} />
              <Tile label="Half days" value={`${data.halfDays}`} />
              <Tile label="Absent" value={`${data.absentDays}`} />
              <Tile label="On leave" value={`${data.leaveDays}`} />
              <Tile label="Overtime" value={`${ot}h`} />
            </dl>
            <p className="mt-1 text-xs text-ink-faint">{data.workingDays} working days this month{data.shiftName ? ` · ${data.shiftName} shift` : ''}</p>
          </section>

          <Shoots title="Upcoming shoots" empty="Nothing scheduled." rows={data.upcomingShoots} />
          <Shoots title="Recent shoots" empty="No past shoots." rows={data.recentShoots} />

          <section>
            <h3 className="mb-1 text-xs font-medium uppercase tracking-wide text-ink-faint">Recent leave</h3>
            {data.recentLeaves.length === 0 ? <p className="text-sm text-ink-faint">No leave requests.</p> : (
              <ul className="divide-y divide-border">
                {data.recentLeaves.map((l) => (
                  <li key={l.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                    <span className="text-ink">{formatDate(l.fromDate)} – {formatDate(l.toDate)} <span className="text-ink-faint">· {l.dayCount} day{l.dayCount > 1 ? 's' : ''}</span></span>
                    <Badge tone={l.status === 'APPROVED' ? 'success' : l.status === 'PENDING' ? 'warning' : 'neutral'}>{l.status}</Badge>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </Drawer>
  )
}

function Tile({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-[var(--radius-md)] bg-surface-2 p-3">
      <dt className="text-xs text-ink-faint">{label}</dt>
      <dd className="font-medium text-ink">{value}</dd>
      {hint && <dd className="text-xs text-ink-faint">{hint}</dd>}
    </div>
  )
}

function Shoots({ title, rows, empty }: { title: string; rows: StaffShoot[]; empty: string }) {
  return (
    <section>
      <h3 className="mb-1 text-xs font-medium uppercase tracking-wide text-ink-faint">{title}</h3>
      {rows.length === 0 ? <p className="text-sm text-ink-faint">{empty}</p> : (
        <div className="divide-y divide-border">
          {rows.map((b) => (
            <Link key={b.id} to={`/admin/bookings/${b.id}`} className="flex items-center justify-between gap-3 py-2 text-sm hover:text-accent">
              <span><span className="font-medium text-ink">{b.bookingNumber}</span> <span className="text-ink-faint">· {formatDate(b.bookingDate)}{b.clientName ? ` · ${b.clientName}` : ''}</span></span>
              <Badge tone="neutral">{b.status.replace('_', ' ')}</Badge>
            </Link>
          ))}
        </div>
      )}
    </section>
  )
}
