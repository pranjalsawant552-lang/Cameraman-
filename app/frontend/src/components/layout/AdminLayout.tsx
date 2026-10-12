import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'
import { useIdleLogout } from '@/lib/useIdleLogout'

const TITLES: Record<string, string> = {
  '/admin': 'Dashboard',
  '/admin/bookings': 'Bookings',
  '/admin/studio-sessions': 'Indoor / Studio Sessions',
  '/admin/outdoor-shoots': 'Outdoor Shoots',
  '/admin/clients': 'Clients',
  '/admin/galleries': 'Galleries',
  '/admin/invoices': 'Invoices',
  '/admin/engagement': 'Suggestions & reminders',
  '/admin/broadcast': 'WhatsApp broadcast',
  '/admin/recommendations': 'Upsells & recommendations',
  '/admin/inventory': 'Inventory',
  '/admin/catalog': 'Packages & Products',
  '/admin/staff': 'Staff',
  '/admin/attendance': 'Attendance',
  '/admin/cms': 'Website content',
  '/admin/audit-logs': 'Audit log',
  '/admin/selections': 'Photo selections',
  '/admin/roles': 'Roles & permissions',
  '/admin/intake-questions': 'Booking questions',
  '/admin/calendar': 'Calendar & closed days',
  '/admin/my-schedule': 'My schedule',
  '/admin/settings': 'Settings',
}

function titleFor(pathname: string) {
  if (TITLES[pathname]) return TITLES[pathname]
  const base = '/' + pathname.split('/').slice(0, 3).join('/').replace(/^\/+/, '')
  return TITLES[base] ?? 'Cameraman Photo Studio'
}

export function AdminLayout() {
  useIdleLogout()
  const location = useLocation()
  const title = titleFor(location.pathname)
  // Screen readers and the browser tab should know which page they are on after a client-side navigation.
  useEffect(() => { document.title = `${title} · Cameraman Photo Studio` }, [title])
  return (
    <div className="min-h-screen bg-bg">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-[60] focus:rounded-[var(--radius-md)] focus:bg-accent focus:px-4 focus:py-2 focus:text-accent-ink">Skip to main content</a>
      <Sidebar />
      <div className="lg:pl-64">
        <Topbar title={title} />
        <main id="main-content" tabIndex={-1} className="px-4 py-5 focus:outline-none sm:px-6 sm:py-6 lg:px-8">
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
    </div>
  )
}
