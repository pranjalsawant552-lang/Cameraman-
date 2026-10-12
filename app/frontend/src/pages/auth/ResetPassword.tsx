import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Aperture } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { resetPassword } from '@/lib/api'
import { ApiError } from '@/lib/apiClient'
import { useToastStore } from '@/store/useToastStore'

export default function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const navigate = useNavigate()
  const push = useToastStore((s) => s.push)
  const [pw, setPw] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (pw.length < 8) return setError('Your new password must be at least 8 characters.')
    if (pw.length > 100) return setError('That password is too long (100 characters max).')
    if (pw !== confirm) return setError('The passwords do not match.')
    setSubmitting(true)
    try {
      await resetPassword(token, pw, confirm)
      push('Password updated. Please sign in.')
      navigate('/login', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reset your password. Please request a new link.')
    } finally { setSubmitting(false) }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-base px-4">
      <div className="w-full max-w-md rounded-[var(--radius-xl)] border border-border bg-surface p-8 shadow-lg">
        <div className="flex flex-col items-center text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-accent/12 text-accent"><Aperture className="h-6 w-6" /></div>
          <h1 className="mt-4 font-display text-2xl text-ink">Choose a new password</h1>
        </div>
        {!token ? (
          <div className="mt-6 text-center">
            <p role="alert" className="text-sm text-danger">This reset link is incomplete or has expired.</p>
            <Link to="/forgot-password" className="mt-4 inline-block text-sm font-medium text-accent hover:underline">Request a new link</Link>
          </div>
        ) : (
          <form onSubmit={submit} className="mt-6 flex flex-col gap-4" noValidate>
            <Input id="rp-new" label="New password (8+ characters)" type="password" autoComplete="new-password" maxLength={100} value={pw} onChange={(e) => setPw(e.target.value)} />
            <Input id="rp-confirm" label="Confirm new password" type="password" autoComplete="new-password" maxLength={100} value={confirm} onChange={(e) => setConfirm(e.target.value)} />
            {error && <p role="alert" className="text-sm text-danger">{error} {error.toLowerCase().includes('expired') || error.toLowerCase().includes('invalid') ? <Link to="/forgot-password" className="font-medium underline">Request a new link</Link> : null}</p>}
            <Button type="submit" size="lg" loading={submitting}>Update password</Button>
          </form>
        )}
      </div>
    </div>
  )
}
