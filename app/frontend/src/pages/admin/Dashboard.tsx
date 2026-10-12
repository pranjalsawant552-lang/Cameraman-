import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Area, AreaChart, Bar, BarChart, ResponsiveContainer, Tooltip, XAxis } from 'recharts'
import { CalendarDays, IndianRupee, Images, Sparkles, ArrowUpRight, RotateCcw } from 'lucide-react'
import { StatTile } from '@/components/ui/StatTile'
import { Card, CardContent, CardHeader, CardTitle, Badge } from '@/components/ui/primitives'
import { Avatar } from '@/components/ui/primitives'
import { Button } from '@/components/ui/Button'
import { STUDIO_NAME } from '@/lib/branding'
import { fetchDashboardStats } from '@/lib/api'
import type { DashboardStats } from '@/lib/types'
import { formatCurrency, formatDate } from '@/lib/utils'
import { useNotificationStore } from '@/store/useNotificationStore'
import { useAuthStore } from '@/store/useAuthStore'

const CATEGORY_TONE: Record<string, string> = {
  WEDDING: 'accent', MATERNITY: 'info', NEWBORN: 'success', BIRTHDAY: 'warning', FAMILY: 'neutral', CORPORATE: 'neutral', PRE_WEDDING: 'accent', EVENT: 'neutral',
}

export default function Dashboard() {
  const { user } = useAuthStore()
  const { items, init } = useNotificationStore()
  useEffect(() => { init() }, [init])

  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchDashboardStats()
      .then((data) => { if (!cancelled) setStats(data) })
      .catch(() => { if (!cancelled) setError("Couldn't load dashboard stats. Please try again.") })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [attempt])

  const load = () => setAttempt((n) => n + 1)

  if (loading) {
    return <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">{Array.from({ length: 4 }).map((_, i) => <div key={i} className="h-28 animate-pulse rounded-[var(--radius-lg)] bg-surface-2" />)}</div>
  }

  if (error || !stats) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 rounded-[var(--radius-lg)] border border-border bg-surface p-12 text-center">
        <p className="text-sm text-ink-muted">{error ?? 'No dashboard data available yet.'}</p>
        <Button variant="secondary" onClick={load}><RotateCcw className="h-3.5 w-3.5" /> Retry</Button>
      </div>
    )
  }

  const growth = stats.revenueLastMonth > 0
    ? (((stats.revenueThisMonth - stats.revenueLastMonth) / stats.revenueLastMonth) * 100).toFixed(1)
    : stats.revenueThisMonth > 0 ? '100.0' : '0.0'

  return (
    <div className="space-y-6">
      <div>
        <p className="font-display text-2xl text-ink">Good to see you, {user?.firstName}.</p>
        <p className="mt-1 text-sm text-ink-muted">Here's what's happening at {STUDIO_NAME} today.</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatTile icon={CalendarDays} label="Shoots today" value={stats.bookingsToday} tone="accent" />
        <StatTile icon={IndianRupee} label="Revenue this month" value={stats.revenueThisMonth} format={formatCurrency} trend={{ direction: 'up', label: `${growth}% vs last month` }} tone="success" />
        <StatTile icon={Sparkles} label="Suggestions to review" value={stats.pendingSuggestions} tone="warning" />
        <StatTile icon={Images} label="Live galleries" value={stats.activeGalleries} tone="accent" />
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader>
            <CardTitle>Revenue trend</CardTitle>
            <Badge tone="success">+{growth}% MoM</Badge>
          </CardHeader>
          <CardContent className="pt-4">
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={stats.revenueTrend} margin={{ left: -18, right: 8 }}>
                <defs>
                  <linearGradient id="rev" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--c-accent)" stopOpacity={0.35} />
                    <stop offset="100%" stopColor="var(--c-accent)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="month" axisLine={false} tickLine={false} tick={{ fill: 'var(--c-ink-faint)', fontSize: 12 }} />
                <Tooltip
                  formatter={(v: any) => formatCurrency(Number(v ?? 0))}
                  contentStyle={{ background: 'var(--c-surface)', border: '1px solid var(--c-border)', borderRadius: 10, fontSize: 13 }}
                  labelStyle={{ color: 'var(--c-ink)' }}
                />
                <Area type="monotone" dataKey="revenue" stroke="var(--c-accent)" strokeWidth={2.5} fill="url(#rev)" />
              </AreaChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>By shoot type</CardTitle></CardHeader>
          <CardContent className="pt-4">
            {stats.bookingsByCategory.length === 0 ? (
              <div className="flex h-[220px] items-center justify-center text-center text-sm text-ink-faint">
                Category breakdown isn't available from the live API yet.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={stats.bookingsByCategory} margin={{ left: -28, right: 8 }}>
                  <XAxis dataKey="category" axisLine={false} tickLine={false} tick={{ fill: 'var(--c-ink-faint)', fontSize: 10 }} interval={0} angle={-20} textAnchor="end" height={50} />
                  <Tooltip contentStyle={{ background: 'var(--c-surface)', border: '1px solid var(--c-border)', borderRadius: 10, fontSize: 13 }} labelStyle={{ color: 'var(--c-ink)' }} />
                  <Bar dataKey="count" fill="var(--c-accent)" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader>
            <CardTitle>Upcoming shoots</CardTitle>
            <Link to="/admin/bookings" className="flex items-center gap-1 text-sm font-medium text-accent hover:underline">
              View all <ArrowUpRight className="h-3.5 w-3.5" />
            </Link>
          </CardHeader>
          <CardContent className="pt-2">
            {stats.upcomingShoots.length === 0 && (
              <p className="py-6 text-center text-sm text-ink-faint">No upcoming confirmed shoots.</p>
            )}
            <div className="divide-y divide-border">
              {stats.upcomingShoots.map((b) => (
                <Link key={b.id} to={`/admin/bookings/${b.id}`} className="flex items-center gap-3 py-3.5 transition-colors hover:bg-surface-2 -mx-2 px-2 rounded-[var(--radius-sm)]">
                  <Avatar src={b.clientAvatar} name={b.clientName} size={40} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-ink">{b.clientName} — {b.packageName}</p>
                    <p className="text-xs text-ink-faint">{formatDate(b.bookingDate)} · {b.photographerName ?? 'Unassigned'}</p>
                  </div>
                  <Badge tone={CATEGORY_TONE[b.shootCategory] as any}>{b.shootCategory.replace('_', ' ')}</Badge>
                </Link>
              ))}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>Live activity</CardTitle></CardHeader>
          <CardContent className="pt-2">
            <div className="space-y-4">
              {items.slice(0, 5).map((n) => (
                <div key={n.id} className="flex gap-3">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-accent" />
                  <div className="min-w-0">
                    <p className="text-sm text-ink leading-snug">{n.title}</p>
                    <p className="text-xs text-ink-faint">{formatDate(n.createdAt, { hour: 'numeric', minute: '2-digit' })}</p>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
