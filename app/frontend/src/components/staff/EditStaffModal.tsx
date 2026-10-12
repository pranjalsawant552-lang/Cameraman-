import { useEffect, useState } from 'react'
import { Input, Select, Textarea } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { updateStaffProfile, updateUserAccount } from '@/lib/api'
import type { StaffMember } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'
import { useAuthStore } from '@/store/useAuthStore'
import { isAdmin } from '@/lib/access'

const ROLES = ['ADMIN', 'MANAGER', 'RECEPTIONIST', 'PHOTOGRAPHER', 'EDITOR', 'ACCOUNTANT', 'INVENTORY_MANAGER']
const DEPARTMENTS = ['Management', 'Photography', 'Post-production', 'Front Office', 'Finance']
const MOBILE_RE = /^[6-9]\d{9}$/

export function EditStaffModal({ staff, onClose, onSaved }: { staff: StaffMember | null; onClose: () => void; onSaved: () => void }) {
  const push = useToastStore((s) => s.push)
  const me = useAuthStore((s) => s.user)
  const canChangeRole = isAdmin(me?.roles) && staff?.userId !== me?.id
  const [form, setForm] = useState({ firstName: '', lastName: '', mobile: '', role: 'PHOTOGRAPHER', department: '', designation: '', employeeId: '', maxBookingsPerDay: '2', bio: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!staff) return
    setError(null)
    setForm({
      firstName: staff.firstName, lastName: staff.lastName, mobile: staff.mobile,
      role: staff.roles.find((r) => ROLES.includes(r)) ?? 'PHOTOGRAPHER',
      department: staff.department === 'Unassigned' ? '' : staff.department, designation: staff.designation,
      employeeId: staff.employeeId === '—' ? '' : staff.employeeId, maxBookingsPerDay: String(staff.maxBookingsPerDay), bio: '',
    })
  }, [staff])

  const set = (k: keyof typeof form, v: string) => setForm((f) => ({ ...f, [k]: v }))

  async function save() {
    if (!staff) return
    setError(null)
    if (!form.firstName.trim()) return setError('First name is required.')
    if (form.mobile && !MOBILE_RE.test(form.mobile.trim())) return setError('Enter a valid 10-digit Indian mobile number.')
    const max = Number(form.maxBookingsPerDay)
    if (!Number.isInteger(max) || max < 1 || max > 50) return setError('Max bookings per day must be between 1 and 50.')
    setSaving(true)
    try {
      const roleChanged = canChangeRole && !staff.roles.includes(form.role as never)
      await updateUserAccount(staff.userId, {
        firstName: form.firstName.trim(), lastName: form.lastName.trim(), mobile: form.mobile.trim() || undefined,
        ...(roleChanged ? { roleNames: [form.role] } : {}),
      })
      await updateStaffProfile(staff.id, {
        employeeId: form.employeeId.trim(), department: form.department || undefined,
        designation: form.designation.trim() || undefined, maxBookingsPerDay: max,
      })
      push('Staff member updated')
      onSaved(); onClose()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save the changes.')
    } finally { setSaving(false) }
  }

  return (
    <Modal open={!!staff} onClose={onClose} title={staff ? `Edit ${staff.firstName}` : ''}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={save} loading={saving}>Save changes</Button></>}>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input label="First name" value={form.firstName} maxLength={60} onChange={(e) => set('firstName', e.target.value)} />
        <Input label="Last name" value={form.lastName} maxLength={60} onChange={(e) => set('lastName', e.target.value)} />
        <Input label="Mobile" value={form.mobile} inputMode="numeric" maxLength={10} onChange={(e) => set('mobile', e.target.value.replace(/\D/g, ''))} />
        <Input label="Employee ID (used by the attendance machine)" value={form.employeeId} maxLength={20} onChange={(e) => set('employeeId', e.target.value)} />
        <Select label="Role" value={form.role} disabled={!canChangeRole} onChange={(e) => set('role', e.target.value)}>
          {ROLES.map((r) => <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>)}
        </Select>
        <Select label="Department" value={form.department} onChange={(e) => set('department', e.target.value)}>
          <option value="">Unassigned</option>
          {DEPARTMENTS.map((d) => <option key={d} value={d}>{d}</option>)}
        </Select>
        <Input label="Designation" value={form.designation} maxLength={100} onChange={(e) => set('designation', e.target.value)} />
        <Input label="Max bookings per day" type="number" min={1} max={50} value={form.maxBookingsPerDay} onChange={(e) => set('maxBookingsPerDay', e.target.value)} />
      </div>
      {!canChangeRole && <p className="mt-3 text-xs text-ink-faint">Roles can be changed by an admin, and never on your own account.</p>}
      {error && <p role="alert" className="mt-4 text-sm text-danger">{error}</p>}
    </Modal>
  )
}
