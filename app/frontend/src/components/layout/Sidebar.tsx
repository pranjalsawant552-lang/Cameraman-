import { NavLink } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  Aperture, LayoutGrid, CalendarDays, Users, Images, Receipt, CreditCard, Mail, FileSpreadsheet, BookOpen, Sparkles,
  Boxes, Gift, Settings, LogOut, UserSquare2, CalendarCheck2, Tags, MessageCircleMore, MapPinned,
  FileEdit, History, Heart, ShieldCheck, CalendarRange, ClipboardList, UserRound,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/store/useAuthStore'
import { canSee } from '@/lib/access'
import { STUDIO_NAME } from '@/lib/branding'

const NAV = [
  { to: '/admin', end: true, icon: LayoutGrid, label: 'Dashboard' },
  { to: '/admin/my-schedule', icon: UserRound, label: 'My schedule' },
  { to: '/admin/calendar', icon: CalendarRange, label: 'Calendar' },
  { to: '/admin/bookings', icon: CalendarDays, label: 'Bookings' },
  { to: '/admin/studio-sessions', icon: Aperture, label: 'Indoor / Studio' },
  { to: '/admin/outdoor-shoots', icon: MapPinned, label: 'Outdoor Shoots' },
  { to: '/admin/clients', icon: Users, label: 'Clients' },
  { to: '/admin/galleries', icon: Images, label: 'Galleries' },
  { to: '/admin/selections', icon: Heart, label: 'Photo selections' },
  { to: '/admin/invoices', icon: Receipt, label: 'Invoices' },
  { to: '/admin/payments', icon: CreditCard, label: 'Payments' },
  { to: '/admin/contact-inquiries', icon: Mail, label: 'Contact inquiries' },
  { to: '/admin/reports', icon: FileSpreadsheet, label: 'Reports' },
  { to: '/admin/albums', icon: BookOpen, label: 'Albums' },
  { to: '/admin/engagement', icon: Sparkles, label: 'Suggestions' },
  { to: '/admin/broadcast', icon: MessageCircleMore, label: 'Broadcast' },
  { to: '/admin/recommendations', icon: Gift, label: 'Upsells' },
  { to: '/admin/inventory', icon: Boxes, label: 'Inventory' },
  { to: '/admin/catalog', icon: Tags, label: 'Packages & Products' },
  { to: '/admin/staff', icon: UserSquare2, label: 'Staff' },
  { to: '/admin/intake-questions', icon: ClipboardList, label: 'Booking questions' },
  { to: '/admin/roles', icon: ShieldCheck, label: 'Roles & permissions' },
  { to: '/admin/attendance', icon: CalendarCheck2, label: 'Attendance' },
  { to: '/admin/cms', icon: FileEdit, label: 'Website content' },
  { to: '/admin/audit-logs', icon: History, label: 'Audit log' },
]

/** The nav list, logo and footer links. Used by the fixed desktop sidebar and the phone/tablet drawer. */
export function SidebarContent({ onNavigate, animated = true }: { onNavigate?: () => void; animated?: boolean }) {
  const logout = useAuthStore((s) => s.logout)
  const roles = useAuthStore((s) => s.user?.roles)
  const permissions = useAuthStore((s) => s.user?.permissions)
  const items = NAV.filter((item) => canSee(item.to, roles, permissions))

  return (
    <>
      <div className="flex items-center gap-2.5 px-6 py-6">
        <div className="flex h-9 w-9 items-center justify-center rounded-full bg-accent text-accent-ink">
          <Aperture className="h-5 w-5" aria-hidden="true" />
        </div>
        <div>
          <p className="font-display text-base leading-tight text-ink">{STUDIO_NAME}</p>
          <p className="text-[11px] text-ink-faint">Studio manager</p>
        </div>
      </div>

      <nav aria-label="Main" className="min-h-0 flex-1 space-y-0.5 overflow-y-auto px-3 py-2">
        {items.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.end} onClick={onNavigate} className="block rounded-[var(--radius-md)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent">
            {({ isActive }) => (
              <div className="relative">
                {isActive && (animated ? (
                  <motion.div
                    layoutId="sidebar-active"
                    className="absolute inset-0 rounded-[var(--radius-md)] bg-accent"
                    transition={{ type: 'spring', stiffness: 500, damping: 38 }}
                  />
                ) : <div className="absolute inset-0 rounded-[var(--radius-md)] bg-accent" />)}
                <div
                  className={cn(
                    'relative z-10 flex items-center gap-3 rounded-[var(--radius-md)] px-3.5 py-2.5 text-sm font-medium transition-colors duration-150',
                    isActive ? 'text-accent-ink' : 'text-ink-muted hover:text-ink'
                  )}
                  aria-current={isActive ? 'page' : undefined}
                >
                  <item.icon className="h-[18px] w-[18px]" strokeWidth={2} aria-hidden="true" />
                  {item.label}
                </div>
              </div>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="space-y-0.5 border-t border-border px-3 py-3">
        <NavLink to="/admin/settings" onClick={onNavigate} className="block rounded-[var(--radius-md)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent">
          {({ isActive }) => (
            <div className={cn('flex items-center gap-3 rounded-[var(--radius-md)] px-3.5 py-2.5 text-sm font-medium transition-colors', isActive ? 'bg-surface-2 text-ink' : 'text-ink-muted hover:text-ink hover:bg-surface-2')}>
              <Settings className="h-[18px] w-[18px]" aria-hidden="true" />
              Settings
            </div>
          )}
        </NavLink>
        <button type="button" onClick={logout} className="flex w-full items-center gap-3 rounded-[var(--radius-md)] px-3.5 py-2.5 text-sm font-medium text-ink-muted transition-colors hover:bg-surface-2 hover:text-danger focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent">
          <LogOut className="h-[18px] w-[18px]" aria-hidden="true" />
          Sign out
        </button>
      </div>
    </>
  )
}

export function Sidebar() {
  return (
    <aside className="fixed left-0 top-0 z-30 hidden h-screen w-64 flex-col border-r border-border bg-surface lg:flex">
      <SidebarContent />
    </aside>
  )
}
