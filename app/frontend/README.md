# Cameraman Photo Studio — Frontend

A studio-management dashboard (staff) and client portal, built against the
camera-studio Spring Boot backend in `../backend`. React 19 + TypeScript +
Vite + Tailwind v4 + Framer Motion.

This app has **no demo/mock-data mode**. Every screen calls the real
backend through `src/lib/api.ts` — there is no fallback to fabricated data,
by design, so a production build can never accidentally show a real user
sample records.

## Running it

```
cp .env.example .env    # then set VITE_API_BASE_URL to your backend
npm install
npm run dev              # http://localhost:5173
npm run build             # production build to dist/
npm run preview           # serve the production build locally
```

If `VITE_API_BASE_URL` isn't set, the app shows a "Backend not configured"
screen instead of running — see `src/components/layout/BackendNotConfigured.tsx`.

## What's built

**Staff dashboard** (`/admin`) — overview with live stats and charts,
bookings (search/filter/pagination), booking detail with the order-stage
pipeline and upsell recommendations, clients, galleries (grid + photo view),
invoices (with a tax-type selector — intra-state/inter-state/exempt),
inventory with low-stock highlighting, and the **Suggestions & reminders**
module — a review queue for pending outreach plus a rule editor with
auto-send and recurring ("every birthday", not just the first) toggles.

**Client portal** (`/portal`) — an image-led home screen, booking status
with the same stage pipeline, a gallery viewer, invoices with a pay action,
recommended add-ons/upgrades, and a settings page with the marketing
opt-in/out toggle.

**Staff & Attendance** (`/admin/staff`, `/admin/attendance`) — a staff
directory with availability toggling, and a full attendance workspace: a
**Today** view, a **Monthly report** (calendar grid per staff member), **Leave
requests** (approve/reject queue), and **Devices & import** — the ZKTeco/ADMS
push URL and generic webhook URL with a copyable secret, shift/grace-period
config, CSV import, an "unmatched codes" linking list, and manual punch entry.

**Theme system** — four complete, hand-tuned themes (Noir, Editorial,
Daylight, Aperture), switchable live from Settings or the top bar, persisted
to `localStorage`. See `src/styles/globals.css` and `src/store/useThemeStore.ts`.

**Notifications** — the bell in the top bar polls `GET /notifications` every
25s for anything new (see `subscribeToLiveNotifications` in `src/lib/api.ts`)
and animates new arrivals in with a bell-shake and pulse. The backend also
publishes over a STOMP/WebSocket channel (`NotificationPublisher`) for true
push delivery — wiring a `@stomp/stompjs` + `sockjs-client` client to
`${API_BASE}/ws` is a reasonable follow-up if instant delivery matters more
than a 25s-worst-case delay.

## Project structure

```
src/
  components/
    ui/        Reusable primitives - Button, Card, Badge, Modal, Tabs, etc.
    layout/    Sidebar, Topbar, PortalLayout, NotificationBell, ThemeSwitcher
  pages/
    admin/     Staff dashboard screens
    portal/    Client portal screens
    auth/      Login, Register
  lib/
    types.ts      TypeScript types mirroring the backend's DTOs
    api.ts        Every backend call the UI makes - the entire integration surface
    apiClient.ts  Fetch wrapper: base URL, auth header, 401 refresh-and-retry
    config.ts     Resolves VITE_API_BASE_URL / BACKEND_CONFIGURED
    branding.ts   Studio name (VITE_STUDIO_NAME, defaulted)
    statusTone.ts Status -> badge-color mappings shared across pages
  store/       Zustand stores: auth, theme, notifications, toasts
  styles/      globals.css - the theme token system
```

## Known gaps / follow-ups

- **Real-time notifications** currently poll rather than push (see above).
- **Per-client totals** (`Client.totalBookings` / `.totalSpend`) are shown as
  0 — the backend doesn't expose a per-client aggregate endpoint yet
  (`lib/api.ts`'s `adaptClient` notes this explicitly).
- **Dashboard "bookings by category"** chart has no backend data source yet
  (closest is package-popularity, which groups by package, not category) and
  is left empty rather than fabricated.
- Decorative stock photography (Picsum/Unsplash-style placeholder URLs) is
  still used in a few marketing-page hero sections — swap these for the
  studio's real photography before launch; this is cosmetic, not fake
  business data.

See `../DEPLOYMENT.md` for the full backend + frontend go-live checklist.
