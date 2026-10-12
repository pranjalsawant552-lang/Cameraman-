import { useState } from 'react'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Aperture, ArrowRight, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { ThemeSwitcher } from '@/components/layout/ThemeSwitcher'
import { useAuthStore } from '@/store/useAuthStore'
import { STUDIO_NAME } from '@/lib/branding'
import { ApiError } from '@/lib/apiClient'
import { safeNext } from '@/lib/nav'

export default function Register() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = params.get('next')
  const { register } = useAuthStore()
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', mobile: '', password: '' })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function set<K extends keyof typeof form>(key: K, value: string) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (!form.firstName.trim() || !form.email.trim() || !form.mobile.trim() || form.password.length < 8) {
      setError('First name, email, a 10-digit mobile number and a password (8+ characters) are required.')
      return
    }
    setSubmitting(true)
    try {
      await register({
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim() || undefined,
        email: form.email.trim(),
        mobile: form.mobile.trim(),
        password: form.password,
      })
      navigate(safeNext(next, '/portal', '/portal'))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center bg-base px-4">
      <div className="absolute right-6 top-6"><ThemeSwitcher /></div>
      <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }} className="w-full max-w-md rounded-[var(--radius-xl)] border border-border bg-surface p-8 shadow-lg">
        <div className="flex flex-col items-center text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-accent/12 text-accent"><Aperture className="h-6 w-6" /></div>
          <h1 className="mt-4 font-display text-2xl text-ink">Create your account</h1>
          <p className="mt-1 text-sm text-ink-muted">Join {STUDIO_NAME} to track your bookings, galleries and invoices.</p>
        </div>

        <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
          <div className="grid grid-cols-2 gap-3">
            <Input label="First name" value={form.firstName} onChange={(e) => set('firstName', e.target.value)} autoComplete="given-name" />
            <Input label="Last name" value={form.lastName} onChange={(e) => set('lastName', e.target.value)} autoComplete="family-name" />
          </div>
          <Input label="Email" type="email" value={form.email} onChange={(e) => set('email', e.target.value)} autoComplete="email" />
          <Input label="Mobile" value={form.mobile} onChange={(e) => set('mobile', e.target.value)} placeholder="9876543210" autoComplete="tel" />
          <Input label="Password" type="password" value={form.password} onChange={(e) => set('password', e.target.value)} autoComplete="new-password" />
          {error && <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">{error}</p>}
          <Button type="submit" size="lg" className="mt-1 w-full" disabled={submitting}>
            {submitting ? <><Loader2 className="h-4 w-4 animate-spin" /> Creating account…</> : <>Create account <ArrowRight className="h-4 w-4" /></>}
          </Button>
        </form>

        <p className="mt-6 text-center text-xs text-ink-faint">
          Already have an account? <Link to="/login" className="font-medium text-accent hover:underline">Sign in</Link>
        </p>
        <p className="mt-2 text-center text-xs text-ink-faint">We'll email you a link to verify your address after signup.</p>
      </motion.div>
    </div>
  )
}
