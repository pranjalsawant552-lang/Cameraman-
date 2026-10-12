import { useState } from 'react'
import { Input, Textarea } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'

interface Props {
  open: boolean
  title: string
  currentDate: string
  currentStartTime: string
  onClose: () => void
  onReschedule: (date: string, startTime: string, endTime: string, reason?: string) => Promise<void>
}

export function RescheduleModal({ open, title, currentDate, currentStartTime, onClose, onReschedule }: Props) {
  const push = useToastStore((s) => s.push)
  const [date, setDate] = useState(currentDate)
  const [startTime, setStartTime] = useState(currentStartTime)
  const [endTime, setEndTime] = useState('')
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit() {
    setError(null)
    if (!date) { setError('Pick a new date.'); return }
    setSubmitting(true)
    try {
      await onReschedule(date, startTime, endTime, reason.trim() || undefined)
      push('Rescheduled')
      onClose()
    } catch (err) {
      // Surfaces the backend's real slot-conflict message if the new time
      // collides with something else, same pattern as new-booking creation.
      setError(err instanceof ApiError ? err.message : 'Could not reschedule. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={handleSubmit} disabled={submitting}>{submitting ? 'Saving…' : 'Confirm new time'}</Button></>}
    >
      <div className="space-y-4">
        <Input label="New date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        <div className="grid grid-cols-2 gap-3">
          <Input label="Start time" type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
          <Input label="End time (optional)" type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
        </div>
        <Textarea label="Reason (optional)" value={reason} onChange={(e) => setReason(e.target.value)} rows={2} />
        {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
      </div>
    </Modal>
  )
}
