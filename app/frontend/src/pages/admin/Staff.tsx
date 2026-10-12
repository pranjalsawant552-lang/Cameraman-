import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarCheck2, Pencil, Plus, UserCheck, UserX, Users } from 'lucide-react'
import { Card, CardContent, Avatar, Badge, Switch, EmptyState, Skeleton } from '@/components/ui/primitives'
import { SearchInput } from '@/components/ui/Input'
import { Select } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { fetchStaff, setUserActive, toggleStaffAvailability } from '@/lib/api'
import { EditStaffModal } from '@/components/staff/EditStaffModal'
import { Modal } from '@/components/ui/Modal'
import { useAuthStore } from '@/store/useAuthStore'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'
import { isAdmin } from '@/lib/access'
import type { StaffMember } from '@/lib/types'
import { formatDate } from '@/lib/utils'
import { AddStaffModal } from '@/components/staff/AddStaffModal'
import { StaffDrawer } from '@/components/staff/StaffDrawer'

const DEPARTMENTS = ['Management', 'Photography', 'Post-production', 'Front Office', 'Finance']

export default function Staff() {
  const [search, setSearch] = useState('')
  const [department, setDepartment] = useState('')
  const [items, setItems] = useState<StaffMember[]>([])
  const [loading, setLoading] = useState(true)
  const [showAddStaff, setShowAddStaff] = useState(false)
  const [editing, setEditing] = useState<StaffMember | null>(null)
  const [viewingId, setViewingId] = useState<string | null>(null)
  const [confirmToggle, setConfirmToggle] = useState<StaffMember | null>(null)
  const [toggling, setToggling] = useState(false)
  const me = useAuthStore((s) => s.user)
  const push = useToastStore((s) => s.push)
  const admin = isAdmin(me?.roles)

  function reload() {
    setLoading(true)
    fetchStaff({ search, department })
      .then((res) => setItems(res))
      .catch((e) => { setItems([]); push(e instanceof ApiError ? e.message : 'Could not load staff.') })
      .finally(() => setLoading(false))
  }
  useEffect(reload, [search, department])

  async function applyToggle() {
    if (!confirmToggle) return
    const makeActive = confirmToggle.userStatus !== 'ACTIVE'
    setToggling(true)
    try {
      await setUserActive(confirmToggle.userId, makeActive)
      push(makeActive ? 'Account reactivated' : 'Account deactivated. They are signed out and can no longer log in.')
      setConfirmToggle(null); reload()
    } catch (e) {
      push(e instanceof ApiError ? e.message : 'That change could not be made.')
    } finally { setToggling(false) }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 gap-3">
          <div className="w-full max-w-xs"><SearchInput value={search} onChange={setSearch} placeholder="Search staff…" /></div>
          <Select value={department} onChange={(e) => setDepartment(e.target.value)} className="max-w-[200px]">
            <option value="">All departments</option>
            {DEPARTMENTS.map((d) => <option key={d} value={d}>{d}</option>)}
          </Select>
        </div>
        <Button onClick={() => setShowAddStaff(true)}><Plus className="h-4 w-4" /> Add staff</Button>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-40 rounded-[var(--radius-lg)]" />)}
        </div>
      ) : items.length === 0 ? (
        <Card><EmptyState icon={<Users className="h-10 w-10" />} title="No staff found" description="Try a different search term or department filter." /></Card>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {items.map((s) => (
            <Card key={s.id} className="p-5">
              <div className="flex items-start gap-3">
                <Avatar src={s.profilePhotoUrl} name={`${s.firstName} ${s.lastName}`} size={48} />
                <div className="min-w-0 flex-1">
                  <button type="button" onClick={() => setViewingId(s.id)} className="block max-w-full truncate text-left font-medium text-ink hover:text-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent">{s.firstName} {s.lastName}</button>
                  <p className="truncate text-xs text-ink-faint">{s.designation} · {s.employeeId}</p>
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    <Badge tone="neutral">{s.department}</Badge>
                    {s.userStatus && s.userStatus !== 'ACTIVE' && <Badge tone="danger">{s.userStatus === 'INACTIVE' ? 'Deactivated' : s.userStatus}</Badge>}
                  </div>
                </div>
                {admin && (
                  <div className="flex shrink-0 gap-1">
                    <Button variant="ghost" size="icon" aria-label={`Edit ${s.firstName}`} onClick={() => setEditing(s)}><Pencil className="h-4 w-4" /></Button>
                    {s.userId !== me?.id && (
                      <Button variant="ghost" size="icon" aria-label={s.userStatus === 'ACTIVE' ? `Deactivate ${s.firstName}` : `Reactivate ${s.firstName}`} onClick={() => setConfirmToggle(s)}>
                        {s.userStatus === 'ACTIVE' ? <UserX className="h-4 w-4" /> : <UserCheck className="h-4 w-4" />}
                      </Button>
                    )}
                  </div>
                )}
              </div>

              <CardContent className="mt-2 grid grid-cols-2 gap-3 border-t border-border px-0 pb-0 pt-3 text-sm">
                <div>
                  <p className="text-xs text-ink-faint">Joined</p>
                  <p className="text-ink">{formatDate(s.joiningDate)}</p>
                </div>
                <div>
                  <p className="text-xs text-ink-faint">Tasks completed</p>
                  <p className="text-ink">{s.completedTasksCount}/{s.assignedTasksCount}</p>
                </div>
              </CardContent>

              <div className="mt-4 flex items-center justify-between border-t border-border pt-3.5">
                <div className="flex items-center gap-2">
                  <Switch checked={s.availableForBooking} onChange={async () => { await toggleStaffAvailability(s.id); reload() }} />
                  <span className="text-xs text-ink-muted">Available for booking</span>
                </div>
                <Link to={`/admin/attendance?staffId=${s.id}`} className="flex items-center gap-1 text-xs font-medium text-accent hover:underline">
                  <CalendarCheck2 className="h-3.5 w-3.5" /> Attendance
                </Link>
              </div>
            </Card>
          ))}
        </div>
      )}

      <StaffDrawer staffId={viewingId} onClose={() => setViewingId(null)} />
      <EditStaffModal staff={editing} onClose={() => setEditing(null)} onSaved={reload} />
      <Modal open={!!confirmToggle} onClose={() => setConfirmToggle(null)}
        title={confirmToggle?.userStatus === 'ACTIVE' ? 'Deactivate this account?' : 'Reactivate this account?'}
        footer={<><Button variant="outline" onClick={() => setConfirmToggle(null)}>Cancel</Button><Button variant={confirmToggle?.userStatus === 'ACTIVE' ? 'danger' : 'primary'} onClick={applyToggle} loading={toggling}>{confirmToggle?.userStatus === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}</Button></>}>
        <p className="text-sm text-ink-muted">
          {confirmToggle?.userStatus === 'ACTIVE'
            ? `${confirmToggle?.firstName} will be signed out everywhere and won't be able to log in until reactivated. Their history is kept.`
            : `${confirmToggle?.firstName} will be able to log in again.`}
        </p>
      </Modal>

      <AddStaffModal
        open={showAddStaff}
        onClose={() => setShowAddStaff(false)}
        onCreated={() => reload()}
      />
    </div>
  )
}
