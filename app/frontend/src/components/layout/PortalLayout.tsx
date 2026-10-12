import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Aperture, LogOut } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/store/useAuthStore'
import { STUDIO_NAME } from '@/lib/branding'
import { NotificationBell } from './NotificationBell'
import { ThemeSwitcher } from './ThemeSwitcher'
import { Avatar } from '@/components/ui/primitives'

const NAV = [
  { to: '/portal', end: true, label: 'Home' },
  { to: '/portal/book', label: 'Book a session' },
  { to: '/portal/bookings', label: 'My bookings' },
  { to: '/portal/gallery', label: 'My gallery' },
  { to: '/portal/invoices', label: 'Invoices' },
  { to: '/portal/recommended', label: 'Recommended' },
  { to: '/portal/verify', label: 'Verify account' },
]

export function PortalLayout() {
  const { user, logout } = useAuthStore()
  const location = useLocation()

  return (
    <div className="min-h-screen bg-bg">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-[60] focus:rounded-[var(--radius-md)] focus:bg-accent focus:px-4 focus:py-2 focus:text-accent-ink">Skip to main content</a>
      <header className="sticky top-0 z-30 border-b border-border bg-surface/85 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-accent text-accent-ink">
              <Aperture className="h-4.5 w-4.5" />
            </div>
            <span className="font-display text-lg text-ink">{STUDIO_NAME}</span>
          </div>

          <nav aria-label="Main" className="hidden items-center gap-1 md:flex">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn('rounded-full px-3.5 py-2 text-sm font-medium transition-colors', isActive ? 'bg-accent/15 text-accent' : 'text-ink-muted hover:text-ink')
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-1">
            <ThemeSwitcher />
            <NotificationBell />
            <NavLink to="/portal/settings" className="ml-1">
              <Avatar src={user?.profilePhotoUrl} name={`${user?.firstName} ${user?.lastName}`} size={34} />
            </NavLink>
            <button onClick={logout} className="ml-1 flex h-10 w-10 items-center justify-center rounded-full text-ink-muted transition-colors hover:bg-surface-2 hover:text-danger">
              <LogOut className="h-4.5 w-4.5" />
            </button>
          </div>
        </div>
        <nav aria-label="Main (mobile)" className="flex items-center gap-1 overflow-x-auto px-4 pb-2 md:hidden">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => cn('shrink-0 rounded-full px-3 py-1.5 text-sm font-medium', isActive ? 'bg-accent/15 text-accent' : 'text-ink-muted')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main id="main-content" tabIndex={-1} className="mx-auto max-w-6xl px-4 py-6 focus:outline-none sm:px-6 sm:py-8">
        <AnimatePresence mode="wait">
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
          >
            <Outlet />
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  )
}
