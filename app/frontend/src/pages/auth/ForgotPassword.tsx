import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Aperture, MailCheck } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { requestPasswordReset } from '@/lib/api'
import { ApiError } from '@/lib/apiClient'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (!EMAIL_RE.test(email.trim())) return setError('Enter the email address you registered with.')
    setSubmitting(true)
    try {
      await requestPasswordReset(email.trim())
      setSent(true)
    } catch (err) {
      // Only transport/rate-limit style failures surface; the server answers the same for unknown emails.
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    } finally { setSubmitting(false) }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-base px-4">
      <div className="w-full max-w-md rounded-[var(--radius-xl)] border border-border bg-surface p-8 shadow-lg">
        <div className="flex flex-col items-center text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-accent/12 text-accent">{sent ? <MailCheck className="h-6 w-6" /> : <Aperture className="h-6 w-6" />}</div>
          <h1 className="mt-4 font-display text-2xl text-ink">{sent ? 'Check your email' : 'Forgot your password?'}</h1>
          <p className="mt-2 text-sm text-ink-muted">
            {sent
              ? 'If that address belongs to an account, we have sent a link to reset your password. It is valid for one hour.'
              : 'Enter your email and we will send you a link to choose a new password.'}
          </p>
        </div>
        {!sent && (
          <form onSubmit={submit} className="mt-6 flex flex-col gap-4" noValidate>
            <Input id="fp-email" label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            {error && <p role="alert" className="text-sm text-danger">{error}</p>}
            <Button type="submit" size="lg" loading={submitting}>Send reset link</Button>
          </form>
        )}
        <p className="mt-6 text-center text-sm text-ink-muted"><Link to="/login" className="font-medium text-accent hover:underline">Back to sign in</Link></p>
      </div>
    </div>
  )
}
