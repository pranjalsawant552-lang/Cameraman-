# Stage 1 Report — Security Hardening + Frontend↔Backend Wiring Foundation

## Scope of this stage
Wire the frontend off mock data and onto the real backend for the four
highest-traffic screens (Login, Dashboard, Bookings, Clients, Invoices),
fix the CORS/security issue found during the audit, and prove the result
actually compiles — not just read correct on paper.

## What changed

### Frontend (`frontend/src/`)
- **`lib/config.ts`** (new) — `USE_REAL_API` flag, true when `VITE_API_BASE_URL`
  is set. Demo mode (no env var) still works with zero setup, unchanged.
- **`lib/tokenStorage.ts`** (new) — persists the JWT pair from `POST /auth/login`.
- **`lib/apiClient.ts`** (new) — real `fetch` wrapper: unwraps the backend's
  `ApiResponse<T>` envelope, attaches `Authorization: Bearer`, auto-refreshes
  an expired access token once via `POST /auth/refresh` and retries, forces
  logout + redirect only if the refresh itself fails.
- **`store/useAuthStore.ts`** (rewritten) — `login()` now calls the real
  `POST /auth/login`; `hydrate()` restores a session from a stored token on
  page load via `GET /users/me`; `logout()` revokes the refresh token
  server-side. Demo-mode shortcuts (`loginAsStaff`/`loginAsClient`) kept for
  when no backend is configured.
- **`pages/auth/Login.tsx`** (rewritten) — **this was a real bug**: the form
  had visible email/password fields but ignored whatever was typed and
  force-logged-in a sample account. It's now a real controlled form with
  loading and error states. Demo mode is preserved (submit blank fields to
  get the old sample-account behavior).
- **`App.tsx`** — added a startup `hydrate()` call so a page refresh with a
  valid stored token doesn't briefly bounce an already-logged-in user to
  `/login`.
- **`lib/mockApi.ts`** — `fetchBookings`, `fetchBookingById`, `fetchClients`,
  `fetchInvoices` now call the real endpoints when live, with adapters
  mapping the backend's actual response field names (checked against
  `BookingDto.java`, `BillingDto.java`, `UserDto.java` — not assumed).
  Added `fetchDashboardStats()`, combining `GET /dashboard/stats` +
  `GET /bookings` since the backend splits this across endpoints the mock
  shape didn't.
- **`pages/admin/Dashboard.tsx`** (rewritten) — **this was a real
  architecture bug**: this page imported mock data directly, bypassing the
  API layer that every other page went through, so it couldn't be wired at
  all without a rewrite. Fixed, and added loading/error/empty states it
  never had (blank/undefined charts would have rendered silently before).

### Backend (`backend/src/main/java/com/cameraman/`)
- **`common/config/SecurityConfig.java`** — CORS previously used
  `allowedOriginPatterns(List.of("*"))` with `allowCredentials(true)`. Spring
  resolves a wildcard *pattern* (unlike a literal wildcard string) by
  reflecting back whatever `Origin` header the request sent — meaning any
  website could make an authenticated request carrying a visitor's JWT if
  the token leaked into a script. Changed to an explicit allow-list read
  from `app.cors.allowed-origins` (env: `CORS_ALLOWED_ORIGINS`).
- **`application.yml`** / **`.env.example`** — added `CORS_ALLOWED_ORIGINS`
  with a documented default of the local dev ports.

## Honest gaps left visible, not papered over
- **Per-client `totalBookings`/`totalSpend`** — the backend doesn't expose a
  client-level aggregate endpoint yet. Shown as `0` with a `TODO(stage 2)`
  comment at the exact call site, rather than inventing a number.
- **Dashboard "by shoot category" chart** — no backend endpoint returns this
  breakdown (package-popularity groups by package, not category). The UI
  now shows an honest empty state instead of silently rendering nothing.
- **`pendingSuggestions` / `lowStockItems` on the dashboard** — hardcoded to
  `0` until the Engagement and Inventory pages are wired in Stage 2/3; noted
  inline.

## What I actually verified (not just read)
```
npm install     → 111 packages, clean
npx tsc -b      → 0 errors
npm run build   → clean production build (904 KB main bundle — pre-existing,
                   unrelated to this stage; worth code-splitting in Stage 4)
npx oxlint      → 0 errors on all changed files
```
I do **not** have Maven Central access in this sandbox, so the two Java
changes (`SecurityConfig.java`, `application.yml`) were reviewed carefully
against the existing code and Spring Security's actual CORS resolution
behavior, but were not compiler/test-verified. Run `mvn clean verify` on
your end before deploying — this is a small, low-risk change (one class,
one config block) but I want to be upfront that it's unverified by me.

## Not touched in this stage (by design — see Stage 2/3)
Galleries, photo selection, inventory, packages/products, staff, attendance,
engagement/suggestions, broadcast/WhatsApp, customer portal pages, and
reports are all still on `mockData.ts`. Nothing there was silently skipped;
it's the explicit scope of the next two stages.
