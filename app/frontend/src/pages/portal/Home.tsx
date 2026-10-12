import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowRight, CalendarDays, Gift, Images, Receipt } from 'lucide-react'
import { Card, CardContent, Badge, Skeleton } from '@/components/ui/primitives'
import { fetchMyBookings, fetchMyGalleries, fetchMyInvoices } from '@/lib/api'
import type { Booking, Gallery, Invoice } from '@/lib/types'
import { useAuthStore } from '@/store/useAuthStore'
import { formatCurrency, formatDate } from '@/lib/utils'
import { BOOKING_STATUS_TONE } from '@/lib/statusTone'

export default function PortalHome() {
  const { user } = useAuthStore()
  const [myBookings, setMyBookings] = useState<Booking[]>([])
  const [myGalleries, setMyGalleries] = useState<Gallery[]>([])
  const [myInvoices, setMyInvoices] = useState<Invoice[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!user?.id) { setLoading(false); return }
    Promise.all([fetchMyBookings(user.id), fetchMyGalleries(), fetchMyInvoices()]).then(([b, g, i]) => {
      setMyBookings(b); setMyGalleries(g); setMyInvoices(i); setLoading(false)
    })
  }, [user?.id])

  const upcoming = myBookings.find((b) => b.status === 'CONFIRMED' || b.status === 'PENDING')
  const latestGallery = myGalleries.find((g) => g.status === 'PUBLISHED')
  const dueInvoice = myInvoices.find((i) => i.balanceDue > 0)

  if (loading) {
    return (
      <div className="space-y-8">
        <Skeleton className="h-72 rounded-[var(--radius-xl)] sm:h-80" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-[var(--radius-lg)]" />)}</div>
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        className="relative overflow-hidden rounded-[var(--radius-xl)] border border-border"
      >
        <img src={latestGallery?.coverPhotoUrl ?? 'https://picsum.photos/seed/portal-hero/1400/500'} alt="" className="h-72 w-full object-cover sm:h-80" />
        <div className="absolute inset-0 bg-gradient-to-t from-black/75 via-black/25 to-transparent" />
        <div className="absolute inset-0 flex flex-col justify-end p-8">
          <p className="font-display text-3xl italic text-white sm:text-4xl">Welcome back, {user?.firstName}.</p>
          {latestGallery ? (
            <div className="mt-3 flex items-center gap-3">
              <p className="text-sm text-white/80">Your gallery "{latestGallery.name}" is ready to view.</p>
              <Link to="/portal/gallery" className="flex items-center gap-1 rounded-full bg-white/15 px-4 py-1.5 text-sm font-medium text-white backdrop-blur-sm transition-colors hover:bg-white/25">
                View gallery <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </div>
          ) : (
            <p className="mt-2 text-sm text-white/70">We'll let you know the moment your next gallery is ready.</p>
          )}
        </div>
      </motion.div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <QuickCard icon={CalendarDays} label="Upcoming shoot" value={upcoming ? formatDate(upcoming.bookingDate) : '—'} sub={upcoming?.packageName} to="/portal/bookings" />
        <QuickCard icon={Images} label="Galleries ready" value={String(myGalleries.filter((g) => g.status === 'PUBLISHED').length)} sub="Tap to browse" to="/portal/gallery" />
        <QuickCard icon={Receipt} label="Balance due" value={dueInvoice ? formatCurrency(dueInvoice.balanceDue) : formatCurrency(0)} sub={dueInvoice?.invoiceNumber ?? 'All settled'} to="/portal/invoices" />
      </div>

      {upcoming && (
        <Card className="p-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Your next session</p>
              <p className="mt-1 font-display text-xl text-ink">{upcoming.packageName}</p>
              <p className="mt-1 text-sm text-ink-muted">{formatDate(upcoming.bookingDate, { weekday: 'long', day: 'numeric', month: 'long' })} · {upcoming.photographerName ?? 'Photographer to be assigned'}</p>
            </div>
            <Badge tone={BOOKING_STATUS_TONE[upcoming.status] as any}>{upcoming.status}</Badge>
          </div>
        </Card>
      )}

      <Card className="overflow-hidden border-accent/25 bg-accent/5 p-0">
        <div className="flex flex-col items-start gap-4 p-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-full bg-accent/15 text-accent"><Gift className="h-5 w-5" /></div>
            <div>
              <p className="font-medium text-ink">A little something for your collection</p>
              <p className="text-sm text-ink-muted">We picked a few add-ons based on your recent shoot.</p>
            </div>
          </div>
          <Link to="/portal/recommended" className="flex shrink-0 items-center gap-1 rounded-full bg-accent px-4 py-2 text-sm font-medium text-accent-ink transition-transform hover:scale-[1.02]">
            See recommendations <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </div>
      </Card>
    </div>
  )
}

function QuickCard({ icon: Icon, label, value, sub, to }: { icon: any; label: string; value: string; sub?: string; to: string }) {
  return (
    <Link to={to}>
      <Card className="p-5 transition-transform duration-200 hover:-translate-y-0.5">
        <CardContent className="flex items-center gap-4 p-0">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[var(--radius-md)] bg-accent/12 text-accent"><Icon className="h-5 w-5" /></div>
          <div className="min-w-0">
            <p className="text-xs text-ink-faint">{label}</p>
            <p className="truncate font-display text-lg text-ink">{value}</p>
            {sub && <p className="truncate text-xs text-ink-faint">{sub}</p>}
          </div>
        </CardContent>
      </Card>
    </Link>
  )
}
