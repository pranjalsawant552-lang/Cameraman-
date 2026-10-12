import { useState } from 'react'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Aperture, ArrowRight, Briefcase, Loader2, User } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { ThemeSwitcher } from '@/components/layout/ThemeSwitcher'
import { useAuthStore } from '@/store/useAuthStore'
import { STUDIO_NAME } from '@/lib/branding'
import { ApiError } from '@/lib/apiClient'
import { safeNext } from '@/lib/nav'

export default function Login() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = params.get('next')
  const { login } = useAuthStore()
  const [mode, setMode] = useState<'staff' | 'client'>('staff')
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)

    if (!identifier.trim() || !password) {
      setError('Enter your email/mobile and password.')
      return
    }

    setSubmitting(true)
    try {
      await login(identifier, password)
      const portal = useAuthStore.getState().portal
      navigate(portal === 'portal' ? safeNext(next, '/portal', '/portal') : safeNext(next, '/admin', '/admin'))
    } catch (err) {
      setError(err instanceof ApiError ? err.message
        : err instanceof TypeError
          // fetch() rejects with a TypeError when the server is unreachable or the browser blocks it (CORS).
          ? `Can't reach the server (${import.meta.env.VITE_API_BASE_URL ?? 'API URL not set'}). Check that the backend is running and allows this site's address.`
          : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Hero panel */}
      <div className="relative hidden overflow-hidden bg-surface-3 lg:block">
        <img
          src="https://picsum.photos/seed/cameraman-hero/1400/1800"
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-black/40" />
        <div className="relative flex h-full flex-col justify-between p-12">
          <div className="flex items-center gap-2.5 text-white">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-white/15 backdrop-blur-sm">
              <Aperture className="h-5 w-5" />
            </div>
            <span className="font-display text-lg">{STUDIO_NAME}</span>
          </div>
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, ease: [0.16, 1, 0.3, 1] }}
            className="max-w-md"
          >
            <p className="font-display text-4xl italic leading-tight text-white">
              Every frame tells a story worth remembering.
            </p>
            <p className="mt-4 text-sm text-white/70">
              One place to run bookings, galleries, billing and client relationships —
              built for studios that never miss a moment.
            </p>
          </motion.div>
        </div>
      </div>

      {/* Form panel */}
      <div className="relative flex flex-col justify-center px-6 py-12 sm:px-12 lg:px-20">
        <div className="absolute right-6 top-6">
          <ThemeSwitcher />
        </div>

        <div className="mx-auto w-full max-w-sm">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-accent text-accent-ink">
              <Aperture className="h-5 w-5" />
            </div>
            <span className="font-display text-lg text-ink">{STUDIO_NAME}</span>
          </div>

          <h1 className="font-display text-3xl text-ink">Welcome back</h1>
          <p className="mt-2 text-sm text-ink-muted">Sign in to continue to your {mode === 'staff' ? 'studio dashboard' : 'client portal'}.</p>

          <div className="mt-6 grid grid-cols-2 gap-2 rounded-[var(--radius-md)] bg-surface-2 p-1">
            <button
              onClick={() => setMode('staff')}
              className={`flex items-center justify-center gap-1.5 rounded-[calc(var(--radius-md)-4px)] py-2 text-sm font-medium transition-colors ${mode === 'staff' ? 'bg-accent text-accent-ink' : 'text-ink-muted hover:text-ink'}`}
            >
              <Briefcase className="h-3.5 w-3.5" /> Staff
            </button>
            <button
              onClick={() => setMode('client')}
              className={`flex items-center justify-center gap-1.5 rounded-[calc(var(--radius-md)-4px)] py-2 text-sm font-medium transition-colors ${mode === 'client' ? 'bg-accent text-accent-ink' : 'text-ink-muted hover:text-ink'}`}
            >
              <User className="h-3.5 w-3.5" /> Client
            </button>
          </div>

          <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
            <Input
              label="Email or mobile"
              type="text"
              placeholder={mode === 'staff' ? 'meera@cameramanphotostudio.in' : 'you@example.com'}
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              autoComplete="username"
            />
            <Input
              label="Password"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
            <div className="-mt-2 text-right">
              <Link to="/forgot-password" className="text-xs font-medium text-accent hover:underline">Forgot password?</Link>
            </div>
            {params.get('idle') && !error && !params.get('sessionExpired') && (
              <p role="status" className="rounded-[var(--radius-md)] bg-surface-2 px-3 py-2 text-sm text-ink-muted">You were signed out because of inactivity. Please sign in again.</p>
            )}
            {params.get('sessionExpired') && !error && (
              <p role="status" className="rounded-[var(--radius-md)] bg-surface-2 px-3 py-2 text-sm text-ink-muted">Your session expired. Please sign in again.</p>
            )}
            {error && (
              <p role="alert" className="rounded-[var(--radius-md)] bg-red-500/10 px-3 py-2 text-sm text-red-500">
                {error}
              </p>
            )}
            <Button type="submit" size="lg" className="mt-1 w-full" disabled={submitting}>
              {submitting ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" /> Signing in…
                </>
              ) : (
                <>
                  Continue as {mode === 'staff' ? 'staff' : 'client'} <ArrowRight className="h-4 w-4" />
                </>
              )}
            </Button>
          </form>

          <p className="mt-6 text-center text-xs text-ink-faint">
            Sign in with your real studio or client account.
          </p>
          {mode === 'client' && (
            <p className="mt-2 text-center text-xs text-ink-faint">
              New here? <Link to={next ? `/register?next=${encodeURIComponent(next)}` : '/register'} className="font-medium text-accent hover:underline">Create an account</Link>
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
