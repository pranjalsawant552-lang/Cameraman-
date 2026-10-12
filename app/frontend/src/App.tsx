import { lazy, Suspense, useEffect, useState } from 'react'
import { ErrorBoundary } from '@/components/shared/ErrorBoundary'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom'
import { useAuthStore } from '@/store/useAuthStore'
import { Toaster } from '@/components/ui/Toaster'
import { AdminLayout } from '@/components/layout/AdminLayout'
import { PortalLayout } from '@/components/layout/PortalLayout'
import PublicLayout from '@/components/layout/PublicLayout'
import { BACKEND_CONFIGURED } from '@/lib/config'
import { BackendNotConfigured } from '@/components/layout/BackendNotConfigured'

// Public/auth pages stay eager — they're the first thing an unauthenticated
// visitor loads, and splitting them would just add a round-trip to the very
// pages that most need to feel instant.
import PublicHome from '@/pages/public/Home'
import PublicAbout from '@/pages/public/About'
import PublicContact from '@/pages/public/Contact'
import PublicServices from '@/pages/public/Services'
import PublicPackages from '@/pages/public/Packages'
import PublicFaq from '@/pages/public/Faq'
import PublicNotFound from '@/pages/public/NotFound'
import Login from '@/pages/auth/Login'
import Register from '@/pages/auth/Register'
import ForgotPassword from '@/pages/auth/ForgotPassword'
import ResetPassword from '@/pages/auth/ResetPassword'

// Everything behind a login is lazy-loaded. This was the one open
// performance item flagged repeatedly across prior stages — a single ~1MB
// JS bundle regardless of which of the 20+ admin/portal pages a person
// actually visits. Splitting per-route means a client only ever downloads
// their own portal's code, and an admin loads Broadcast/CMS/Audit-Logs code
// only if they actually open those pages.
const Dashboard = lazy(() => import('@/pages/admin/Dashboard'))
const Bookings = lazy(() => import('@/pages/admin/Bookings'))
const BookingDetail = lazy(() => import('@/pages/admin/BookingDetail'))
const StudioSessions = lazy(() => import('@/pages/admin/StudioSessions'))
const OutdoorShoots = lazy(() => import('@/pages/admin/OutdoorShoots'))
const Clients = lazy(() => import('@/pages/admin/Clients'))
const Galleries = lazy(() => import('@/pages/admin/Galleries'))
const GalleryDetail = lazy(() => import('@/pages/admin/GalleryDetail'))
const Invoices = lazy(() => import('@/pages/admin/Invoices'))
const InvoiceDetail = lazy(() => import('@/pages/admin/InvoiceDetail'))
const Payments = lazy(() => import('@/pages/admin/Payments'))
const ContactInquiries = lazy(() => import('@/pages/admin/ContactInquiries'))
const Reports = lazy(() => import('@/pages/admin/Reports'))
const Albums = lazy(() => import('@/pages/admin/Albums'))
const Selections = lazy(() => import('@/pages/admin/Selections'))
const Roles = lazy(() => import('@/pages/admin/Roles'))
const Calendar = lazy(() => import('@/pages/admin/Calendar'))
const IntakeQuestions = lazy(() => import('@/pages/admin/IntakeQuestions'))
const MySchedule = lazy(() => import('@/pages/admin/MySchedule'))
const Engagement = lazy(() => import('@/pages/admin/Engagement'))
const Recommendations = lazy(() => import('@/pages/admin/Recommendations'))
const Inventory = lazy(() => import('@/pages/admin/Inventory'))
const Catalog = lazy(() => import('@/pages/admin/Catalog'))
const Broadcast = lazy(() => import('@/pages/admin/Broadcast'))
const Staff = lazy(() => import('@/pages/admin/Staff'))
const Attendance = lazy(() => import('@/pages/admin/Attendance'))
const AdminSettings = lazy(() => import('@/pages/admin/Settings'))
const AuditLogs = lazy(() => import('@/pages/admin/AuditLogs'))
const CmsEditor = lazy(() => import('@/pages/admin/CmsEditor'))

const PortalHome = lazy(() => import('@/pages/portal/Home'))
const PortalBookings = lazy(() => import('@/pages/portal/Bookings'))
const PortalBook = lazy(() => import('@/pages/portal/Book'))
const PortalGallery = lazy(() => import('@/pages/portal/Gallery'))
const PortalInvoices = lazy(() => import('@/pages/portal/Invoices'))
const PortalRecommended = lazy(() => import('@/pages/portal/Recommended'))
const PortalSettings = lazy(() => import('@/pages/portal/Settings'))
const VerifyAccount = lazy(() => import('@/pages/portal/VerifyAccount'))

function Protected({ portal, children }: { portal: 'admin' | 'portal'; children: React.ReactNode }) {
  const current = useAuthStore((s) => s.portal)
  const location = useLocation()
  if (current !== portal) {
    // Remember where the visitor was heading so Login/Register can send them back there.
    const next = current === null ? `?next=${encodeURIComponent(location.pathname + location.search)}` : ''
    return <Navigate to={`/login${next}`} replace />
  }
  return <>{children}</>
}

/** Share links look like /g/<token>. Selections are private to the client, so the link sends
 *  them through login (and back) to their portal gallery rather than exposing photos publicly. */
function GalleryLink() {
  const { token = '' } = useParams()
  return <Navigate to={`/portal/gallery?token=${encodeURIComponent(token)}`} replace />
}

/** Error boundary that resets when the route changes. */
function RoutedBoundary({ children }: { children: React.ReactNode }) {
  const { pathname } = useLocation()
  return <ErrorBoundary resetKey={pathname}>{children}</ErrorBoundary>
}

function RouteFallback() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-accent border-t-transparent" />
    </div>
  )
}

export default function App() {
  const hydrate = useAuthStore((s) => s.hydrate)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    // Restores a session from a persisted JWT (if any) before the router
    // decides whether to bounce to /login — otherwise a refresh would always
    // kick an already-logged-in user back to the login screen for a beat.
    hydrate().finally(() => setReady(true))
  }, [hydrate])

  // This app has no demo/mock-data mode. If no backend URL has been
  // configured, show a clear setup screen instead of either crashing on the
  // first API call or silently falling back to fabricated data.
  if (!BACKEND_CONFIGURED) return <BackendNotConfigured />

  if (!ready) return null

  return (
    <BrowserRouter>
      <div className="grain-overlay" />
      <Toaster />
      <RoutedBoundary>
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/g/:token" element={<GalleryLink />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />

          <Route element={<PublicLayout />}>
            <Route path="/" element={<PublicHome />} />
            <Route path="/services" element={<PublicServices />} />
            <Route path="/packages" element={<PublicPackages />} />
            <Route path="/about" element={<PublicAbout />} />
            <Route path="/faq" element={<PublicFaq />} />
            <Route path="/contact" element={<PublicContact />} />
            <Route path="*" element={<PublicNotFound />} />
          </Route>

          <Route path="/admin" element={<Protected portal="admin"><AdminLayout /></Protected>}>
            <Route index element={<Dashboard />} />
            <Route path="bookings" element={<Bookings />} />
            <Route path="bookings/:id" element={<BookingDetail />} />
            <Route path="studio-sessions" element={<StudioSessions />} />
            <Route path="outdoor-shoots" element={<OutdoorShoots />} />
            <Route path="clients" element={<Clients />} />
            <Route path="galleries" element={<Galleries />} />
            <Route path="galleries/:id" element={<GalleryDetail />} />
            <Route path="invoices" element={<Invoices />} />
            <Route path="invoices/:id" element={<InvoiceDetail />} />
            <Route path="payments" element={<Payments />} />
            <Route path="contact-inquiries" element={<ContactInquiries />} />
            <Route path="reports" element={<Reports />} />
            <Route path="albums" element={<Albums />} />
            <Route path="selections" element={<Selections />} />
            <Route path="roles" element={<Roles />} />
            <Route path="calendar" element={<Calendar />} />
            <Route path="my-schedule" element={<MySchedule />} />
            <Route path="intake-questions" element={<IntakeQuestions />} />
            <Route path="engagement" element={<Engagement />} />
            <Route path="recommendations" element={<Recommendations />} />
            <Route path="inventory" element={<Inventory />} />
            <Route path="catalog" element={<Catalog />} />
            <Route path="broadcast" element={<Broadcast />} />
            <Route path="staff" element={<Staff />} />
            <Route path="attendance" element={<Attendance />} />
            <Route path="audit-logs" element={<AuditLogs />} />
            <Route path="cms" element={<CmsEditor />} />
            <Route path="settings" element={<AdminSettings />} />
          </Route>

          <Route path="/portal" element={<Protected portal="portal"><PortalLayout /></Protected>}>
            <Route index element={<PortalHome />} />
            <Route path="book" element={<PortalBook />} />
            <Route path="bookings" element={<PortalBookings />} />
            <Route path="gallery" element={<PortalGallery />} />
            <Route path="invoices" element={<PortalInvoices />} />
            <Route path="recommended" element={<PortalRecommended />} />
            <Route path="verify" element={<VerifyAccount />} />
            <Route path="settings" element={<PortalSettings />} />
          </Route>

        </Routes>
      </Suspense>
      </RoutedBoundary>
    </BrowserRouter>
  )
}
