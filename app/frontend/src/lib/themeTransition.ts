import { useThemeStore, type ThemeName } from '@/store/useThemeStore'

type ViewTransitionDoc = Document & {
  startViewTransition?: (cb: () => void) => { ready: Promise<void> }
}

/**
 * Switch theme with a circular "lens opening" reveal that expands from where the
 * person clicked. Falls back to an instant switch when the browser has no View
 * Transitions API or the visitor prefers reduced motion.
 */
export function setThemeAnimated(theme: ThemeName, origin?: { x: number; y: number }) {
  const { theme: current, setTheme } = useThemeStore.getState()
  if (current === theme) return

  const doc = document as ViewTransitionDoc
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (!doc.startViewTransition || reduce) {
    setTheme(theme)
    return
  }

  const x = origin?.x ?? window.innerWidth / 2
  const y = origin?.y ?? 0
  const radius = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))

  const transition = doc.startViewTransition(() => setTheme(theme))
  transition.ready
    .then(() => {
      document.documentElement.animate(
        { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] },
        { duration: 750, easing: 'cubic-bezier(0.16, 1, 0.3, 1)', pseudoElement: '::view-transition-new(root)' },
      )
    })
    .catch(() => { /* transition was skipped — theme is already applied */ })
}
