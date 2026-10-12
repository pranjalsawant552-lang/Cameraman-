import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle } from 'lucide-react'

interface State { error: Error | null }

/** Catches render-time crashes so one broken screen shows a recovery message instead of a blank page. */
export class ErrorBoundary extends Component<{ children: ReactNode; resetKey?: string }, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State { return { error } }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('UI crashed:', error, info.componentStack)
  }

  componentDidUpdate(prev: { resetKey?: string }) {
    // Navigating to another page clears the error so the rest of the app keeps working.
    if (this.state.error && prev.resetKey !== this.props.resetKey) this.setState({ error: null })
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div role="alert" className="mx-auto flex max-w-md flex-col items-center gap-3 px-4 py-20 text-center">
        <AlertTriangle className="h-10 w-10 text-warning" />
        <h2 className="font-display text-xl text-ink">Something went wrong on this page</h2>
        <p className="text-sm text-ink-muted">Your data is safe. Try again, or go back to the home screen.</p>
        <div className="mt-2 flex gap-2">
          <button type="button" className="rounded-[var(--radius-md)] bg-accent px-4 py-2 text-sm font-medium text-accent-ink" onClick={() => this.setState({ error: null })}>Try again</button>
          <a href="/" className="rounded-[var(--radius-md)] border border-border px-4 py-2 text-sm text-ink">Go home</a>
        </div>
      </div>
    )
  }
}
