import { useState } from 'react'
import { Input, Select } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { createStaffMember } from '@/lib/api'
import type { StaffMember } from '@/lib/types'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

// Matches the roles actually seeded on the backend (V2__seed_data.sql) minus
// SUPER_ADMIN/CLIENT, which aren't assigned through this form.
const ROLES = ['ADMIN', 'MANAGER', 'RECEPTIONIST', 'PHOTOGRAPHER', 'EDITOR', 'ACCOUNTANT', 'INVENTORY_MANAGER']
const DEPARTMENTS = ['Management', 'Photography', 'Post-production', 'Front Office', 'Finance']

interface Props {
  open: boolean
  onClose: () => void
  onCreated: (staff: StaffMember) => void
}

export function AddStaffModal({ open, onClose, onCreated }: Props) {
  const push = useToastStore((s) => s.push)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({
    firstName: '', lastName: '', email: '', mobile: '', password: '',
    role: 'PHOTOGRAPHER', department: 'Photography', designation: '',
    employeeId: '', joiningDate: '', maxBookingsPerDay: '5',
  })

  function set<K extends keyof typeof form>(key: K, value: typeof form[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  function reset() {
    setForm({ firstName: '', lastName: '', email: '', mobile: '', password: '', role: 'PHOTOGRAPHER', department: 'Photography', designation: '', employeeId: '', joiningDate: '', maxBookingsPerDay: '5' })
    setError(null)
  }

  async function handleSubmit() {
    setError(null)
    if (!form.firstName.trim() || !form.email.trim() || !form.mobile.trim() || !form.password) {
      setError('First name, email, mobile and a temporary password are required.')
      return
    }
    if (form.password.length < 8) {
      setError('Password must be at least 8 characters.')
      return
    }
    setSubmitting(true)
    try {
      const staff = await createStaffMember({
        email: form.email.trim(),
        password: form.password,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim() || undefined,
        mobile: form.mobile.trim(),
        role: form.role,
        employeeId: form.employeeId.trim() || undefined,
        department: form.department,
        designation: form.designation.trim() || undefined,
        joiningDate: form.joiningDate || undefined,
        maxBookingsPerDay: Number(form.maxBookingsPerDay) || undefined,
      })
      push(`${staff.firstName} added to the team`)
      onCreated(staff)
      reset()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add this staff member. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => { onClose(); reset() }}
      title="Add staff"
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={() => { onClose(); reset() }}>Cancel</Button>
          <Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Adding…' : 'Add staff'}</Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input label="First name" value={form.firstName} onChange={(e) => set('firstName', e.target.value)} />
        <Input label="Last name" value={form.lastName} onChange={(e) => set('lastName', e.target.value)} />
        <Input label="Email" type="email" value={form.email} onChange={(e) => set('email', e.target.value)} />
        <Input label="Mobile" value={form.mobile} onChange={(e) => set('mobile', e.target.value)} placeholder="9876543210" />
        <Input label="Temporary password" type="password" value={form.password} onChange={(e) => set('password', e.target.value)} />
        <Select label="Role" value={form.role} onChange={(e) => set('role', e.target.value)}>
          {ROLES.map((r) => <option key={r} value={r}>{r.replace('_', ' ')}</option>)}
        </Select>
        <Select label="Department" value={form.department} onChange={(e) => set('department', e.target.value)}>
          {DEPARTMENTS.map((d) => <option key={d} value={d}>{d}</option>)}
        </Select>
        <Input label="Designation (optional)" value={form.designation} onChange={(e) => set('designation', e.target.value)} placeholder="e.g. Senior Photographer" />
        <Input label="Employee ID (optional)" value={form.employeeId} onChange={(e) => set('employeeId', e.target.value)} />
        <Input label="Joining date (optional)" type="date" value={form.joiningDate} onChange={(e) => set('joiningDate', e.target.value)} />
        <Input label="Max bookings/day" type="number" min="1" value={form.maxBookingsPerDay} onChange={(e) => set('maxBookingsPerDay', e.target.value)} />
      </div>
      <p className="mt-4 text-xs text-ink-faint">
        This creates a login account and a staff profile. Share the temporary password securely — they should change it on first login.
      </p>
      {error && <p role="alert" className="mt-3 rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
    </Modal>
  )
}
