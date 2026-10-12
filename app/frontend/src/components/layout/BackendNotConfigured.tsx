import { AlertTriangle } from 'lucide-react'

/** Shown instead of the app when VITE_API_BASE_URL isn't set. This app has
 *  no demo/mock-data mode — every screen needs a real backend to talk to,
 *  so an unconfigured deployment gets a clear setup message here instead of
 *  a confusing wall of failed API calls (or, worse, fabricated data). */
export function BackendNotConfigured() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-base px-4">
      <div className="w-full max-w-lg rounded-[var(--radius-xl)] border border-border bg-surface p-8 shadow-lg">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-warning/12 text-warning">
          <AlertTriangle className="h-6 w-6" />
        </div>
        <h1 className="mt-4 font-display text-2xl text-ink">Backend not configured</h1>
        <p className="mt-2 text-sm text-ink-muted">
          This build has no <code className="rounded bg-surface-2 px-1.5 py-0.5 text-xs">VITE_API_BASE_URL</code> set,
          so it has no backend to talk to. Copy <code className="rounded bg-surface-2 px-1.5 py-0.5 text-xs">frontend/.env.example</code> to{' '}
          <code className="rounded bg-surface-2 px-1.5 py-0.5 text-xs">.env</code>, set it to your Spring Boot API's base
          URL (e.g. <code className="rounded bg-surface-2 px-1.5 py-0.5 text-xs">https://api.yourstudio.com/api</code>), and rebuild.
        </p>
        <p className="mt-4 text-xs text-ink-faint">
          See DEPLOYMENT.md in the project root for the full backend + frontend setup checklist.
        </p>
      </div>
    </div>
  )
}
