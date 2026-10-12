# Deployment / go-live checklist

This app has no demo mode: every screen requires a real, reachable backend.
This document lists everything that still needs a decision or a credential
from you before it's genuinely ready for paying customers. None of it can be
filled in on your behalf — it requires accounts, secrets, or infrastructure
choices that are yours to make.

## 1. Backend build verification (do this first)

The sandbox this project was edited in has no access to Maven Central, so
`mvn clean verify` has **not** been run against the current source. Before
anything else:

```
cd backend
mvn clean verify
```

Fix any compile errors or failing tests this surfaces before proceeding —
in particular, review the tax-type change in `BillingService.java`
(`applyTaxType` / `deriveTaxType`) and the `taxType` field added to
`BillingDto.CreateInvoiceRequest` / `InvoiceResponse`, since these were
hand-written without a compiler in the loop.

## 2. Database

- Stand up PostgreSQL (not H2/SQLite) for anything beyond local dev.
- Confirm the Flyway migrations apply cleanly: `mvn flyway:migrate` or just
  let the app apply them on boot (default).
- Set a real `DB_PASSWORD` — never reuse the example value.
- Set up regular automated backups (e.g. `pg_dump` on a schedule, or your
  cloud provider's managed backups) before real client/payment data exists.

## 3. Secrets & third-party accounts

All of these are read from environment variables — see `backend/.env.example`
for the full list and `backend/src/main/resources/application-prod.yml` for
how they're wired in. **None of these can be set by an AI assistant** — they
require accounts you control:

| Variable(s) | What it's for | Where to get it |
|---|---|---|
| `JWT_SECRET` | Signs auth tokens | Generate locally: `openssl rand -base64 32` — never reuse a value that's ever appeared in a repo or chat |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` / `RAZORPAY_WEBHOOK_SECRET` | Online payments | Razorpay dashboard (live mode keys, not test) |
| `WHATSAPP_PHONE_NUMBER_ID` / `WHATSAPP_ACCESS_TOKEN` / `WHATSAPP_VERIFY_TOKEN` | Booking reminders / broadcasts | Meta Business Platform (WhatsApp Business API) |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | Transactional email (verification, invoices) | Your SMTP provider (Gmail app password, SES, Postmark, etc.) |
| `S3_ENDPOINT` / `S3_BUCKET` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_PUBLIC_BASE_URL` | Photo/gallery storage | AWS S3 or Cloudflare R2 |
| `GDRIVE_ROOT_FOLDER_ID` + OAuth/service-account vars | Google Drive as private storage for originals | See STORAGE.md (optional; blank = S3) |
| `GOOGLE_DRIVE_API_KEY` | Staff-side bulk photo import | Google Cloud Console (optional feature) |
| `GST_NUMBER` | Printed on invoices | Your studio's actual GSTIN |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Caching / rate limiting | A managed Redis instance or self-hosted |

**Do not commit `.env` to source control.** Use your host's secret manager
(Railway/Render/Fly secrets, AWS Secrets Manager, etc.) in production.

## 4. Domain, CORS & TLS

- `CORS_ALLOWED_ORIGINS` must list every real frontend origin exactly
  (staff dashboard + client portal + any staging URL) — no wildcards.
- Serve both frontend and backend over HTTPS. Most hosts (Vercel, Netlify,
  Railway, Render, Cloudflare Pages) provision this automatically for a
  custom domain.
- Set `frontend/.env`'s `VITE_API_BASE_URL` to the backend's real HTTPS URL
  before building.

## 5. First real admin account

There's no seed/demo admin user anymore. Create the first staff account
directly against the database or via `POST /users` with an admin-capable
role (see `backend/src/main/java/com/cameraman/user/controller/UserController.java`),
then use it to create the rest of the staff through the app's own Staff page.

## 6. Known product gaps (updated after the completion pass)

**Done in the completion pass:** client self-booking (`/portal/book`) with server-side price,
slot and blocked-date checks; availability in the admin New-booking modal; photo selection
(client picks and submit, admin approve and re-open, stage and notification side effects);
forgot/reset password, change password, log-out-everywhere, profile and marketing-consent editing;
invoice finalize, send, record payment and cancel from the UI with consistent statuses; GST
rounding and discount order; payment verify idempotency and refund reversal; intake answers
validated server-side.

**Still open (see `COMPLETION_REPORT.md`):**
- Staff detail drawer and Clients admin page; custom-role permissions that unlock endpoints.
- Per-staff shifts and payroll CSV (Phase 9); product edit UI, inquiry replies (Phase 10).
- Direct-to-storage uploads with progress, Drive import, originals-expiry controls (Phase 2).
- Full responsive and accessibility audit of every page; frontend test suite.
- New migrations V26-V28: back up first (V27 deletes duplicate punches).
- Share links (`/g/:token`) route through login, because the backend only serves galleries and
  selections to the owning client.
- The WebSocket client falls back to REST polling if the socket can't connect; confirm your
  ingress passes `Upgrade` headers on `/ws`.

## 7. What was fixed in this pass

**First pass — demo data removal:**
- Removed the frontend's entire demo/mock-data fallback (`mockData.ts`,
  `mockApi.ts` deleted; every function in the new `lib/api.ts` calls a real
  endpoint).
- Fixed notifications and per-booking upsell recommendations, which were
  silently never wired to the real backend.
- Fixed the Attendance page defaulting to a hardcoded fake date instead of
  the real current date.
- Added a per-invoice tax-type override (intra-state / inter-state /
  exempt).
- Added a "backend not configured" screen instead of crashing or faking
  data when `VITE_API_BASE_URL` is unset.

**Second pass — full endpoint-by-endpoint contract audit**, cross-checking
every function in `lib/api.ts` against the actual Java controllers/DTOs:
- Added the missing `GET /galleries/{id}` endpoint.
- Fixed `GET /galleries/{id}/photos`'s response-shape assumption and added
  real per-photo display numbering from the backend's `sortOrder`.
- Fixed `POST /galleries/{id}/publish` sending an empty body where a
  settings payload was required.
- Wired the client portal's dead "Download all" button to a real
  implementation.
- Fixed the Dashboard being wired to the wrong DTO shape and to a
  role-restricted endpoint narrower than the page's actual audience.
- Fixed the attendance "Today" view silently omitting every absent staff
  member and having no status field.
- Fixed CMS page edits always creating a duplicate row instead of updating.
- Fixed studio session / outdoor shoot reschedule sending field names the
  backend's validation would have rejected.

**Third pass — closed every remaining open item from the second pass's gap
list**, except the ones that need your own credentials/infrastructure:
- **Real-time notifications**: added a genuine STOMP/WebSocket client
  (`lib/realtime.ts`) connected to the backend's existing `/ws` endpoint
  and `/user/queue/notifications` channel, authenticated with the same JWT
  used for REST calls, with automatic reconnect and a slower REST-poll
  safety net running alongside (deduplicated so nothing double-fires).
  Disconnects cleanly on logout.
- **Per-client totals**: added real backend aggregate queries (booking
  count + total amount paid, batched per page of results rather than
  N+1 queries) and wired them into `GET /users/clients/search`. The
  Clients page now shows real numbers instead of hardcoded zeros.
- **Gallery "Download all"**: replaced the sequential-per-photo-download
  workaround with a real server-side streaming ZIP endpoint
  (`GET /galleries/{id}/download`), which respects the exact same
  download-allowed/watermark/purged-original rules as a single-photo
  download and streams file-by-file rather than buffering the whole
  archive in memory. Added the same button to the admin Gallery Detail
  page, which didn't have one before.
- **Studio Settings**: the "Studio profile" card on the Settings page was
  previously non-functional — hardcoded placeholder values and a Save
  button that only showed a toast without saving anything. Rewired to load
  and save real values through the backend's `/settings` module (studio
  profile, booking windows, and bank/payment details, each independently
  saveable).

Every other endpoint used by the frontend (auth, users, staff, bookings,
billing, inventory, packages, products, recommendations, suggestion rules,
outreach, audit logs, broadcasts, leave requests, attendance CSV
import/webhook config) was checked field-by-field against its actual
backend DTO and confirmed correct in the second-pass audit.

## 8. Still requires your own accounts/credentials — cannot be closed here

Payment gateway (Razorpay), WhatsApp Business API, SMTP email, and S3/R2
object storage all need real accounts and keys — see section 3 above. No
amount of further code changes closes these; they're waiting on you.

## 9. Fourth pass — public website content now fully admin-editable

Everything on the public marketing site (photos, social links, location,
footer/contact content, About-page claims, home-page service list) is now
either already backed by the existing CMS (hero slides, testimonials, page
text) or newly wired to the settings module in this pass:

- **Real bug found and fixed**: `GET /settings/public` and the new
  `GET /settings/public-info` were not actually reachable without a login
  token — Spring Security's rule list never granted them `permitAll`
  despite the "public" name, so the public site could never have called
  either. Fixed in `SecurityConfig`.
- **Image uploads**: added `POST /cms/media/upload` so hero-slide images,
  the studio logo, and the watermark logo can be uploaded as files (JPEG/
  PNG/WebP/GIF, 10MB limit) from the admin side instead of only accepting a
  pasted URL. The CMS editor's hero slides can now be edited in place too
  (previously create/delete only).
- **Social links & location**: added Instagram/Facebook/YouTube URL and
  Google Maps link settings, editable from Settings → Public website, and
  a new no-auth `GET /settings/public-info` endpoint (a safe subset —
  never bank details or GST number) that the public footer and Contact
  page now render from instead of hardcoded fake address/phone/social text.
- **Fabricated business claims removed**: the About page previously showed
  a hardcoded "4,000+ sessions, 11 years in business" stats row with no way
  to change it. Replaced with an admin-editable stats list (Settings →
  Public website); the section simply doesn't render until real stats are
  entered, rather than showing invented numbers.
- **Home page content**: the "What we shoot" service list and the two
  secondary hero images were hardcoded. The service list is now an
  admin-editable list (icon + title + description) with the old six as the
  default until customized; the secondary hero images now reuse the same
  admin-managed "Hero slides" list (2nd and 3rd slides) instead of separate
  hardcoded stock photos, falling back to a neutral placeholder icon (not a
  fake photo) when fewer than 3 slides exist.

**Still cosmetic-only**: the main hero image and any slide without an
uploaded/pasted image fall back to a Picsum placeholder photo — replace
with real studio photography via the Hero Slides upload once available.

**Also fixed while double-checking a prior pass**: the home page's
testimonials section fell back to three fabricated named client reviews
(with made-up names and quotes) whenever no real testimonials had been
added yet — the same category of issue as the About stats fix above.
Removed; the section now simply doesn't render until real testimonials
exist.

## 10. Boot failure found via an actual run (not just review) — fixed

You ran the backend and it failed to start:

```
UnknownPathException: Could not resolve attribute 'clientId' of
'com.cameraman.booking.domain.Booking'
[SELECT b.clientId, COUNT(b) FROM Booking b WHERE b.clientId IN :clientIds GROUP BY b.clientId]
```

This is exactly the kind of mistake I flagged as a risk from not having a
compiler in my own environment — `Booking.client` is a `@ManyToOne User`
relation (mapped via the `client_id` column), not a plain `clientId`
field. `findByClientId(...)` (a derived-query method, unaffected) works
because Spring Data's method-name parser falls back to the nested path
`client.id` on its own — but that fallback only applies to derived
queries, not to a hand-written `@Query`, where `b.clientId` is invalid HQL.

Fixed in `BookingRepository.countByClientIdIn` — now
`SELECT b.client.id, COUNT(b) FROM Booking b WHERE b.client.id IN
:clientIds GROUP BY b.client.id`. I also re-checked every other custom
`@Query` added across every pass this session (invoice, leave, gallery,
payment, contact) against the real entity field types — all of those
reference plain columns, not relations, and are correct. This was the one
instance of that specific mistake.

**This is a genuine, uncomfortable reminder**: everything I write in this
sandbox is checked as carefully as I can without a JDK/Maven, but "checked
carefully" is not the same as "compiled." Please keep running
`mvn clean verify` (or just booting the app, as you just did) after each
batch of changes — that loop is what actually catches this class of bug,
and it just did.

## 12. Test suite audit — 3 compile-breaking test files found and fixed

There's a real unit test suite in `backend/src/test` that I hadn't looked
at until prompted to find every remaining bug. Checked every test file
against the actual current constructor of the class it tests — found and
fixed three that would have failed to even **compile**, which means
`mvn clean verify` would fail on all three regardless of whether the
application code itself was correct:

- `UserServiceSearchTest` — broken **by my own change**: I added two new
  constructor dependencies to `UserService` (for the real per-client
  totals) and didn't update this test to match. Fixed: added the two
  missing mocks.
- `GalleryServiceSelectionLinkTest` and `BookingServiceCalendarTest` —
  broken **before I ever touched this project**: both tests were missing
  mocks for constructor dependencies (`userRepository`,
  `notificationService`, `notificationPublisher`, `settingsService` on
  Gallery; `notificationPublisher` on Booking) that must have been added
  to those services in an earlier build stage without the tests being
  updated. Not something my session caused, but exactly the kind of thing
  that would have made `mvn clean verify` fail no matter what.

All three now construct their service with every real dependency mocked,
matching the actual current constructors, with no change to what the
tests assert.

**One thing worth knowing about running the full test suite**:
`CameramanPhotoStudioApplicationTests` (`contextLoads`) and other
Spring-context tests use `application-test.yml`, which disables Flyway
but still points at the same PostgreSQL connection as normal — it does
**not** use an embedded/in-memory database. `mvn clean verify` will fail
these specific tests (not from a code bug, but from infrastructure) if
PostgreSQL isn't running and reachable at test time.

## 11. Fifth pass — five backend modules that had no frontend wired up

An audit of every backend controller against the frontend turned up five
complete modules with real, working endpoints that no page or button ever
called. All five are now wired up:

- **Online payments (the most serious gap)**: the client portal's "Pay"
  button did `push('Redirecting to payment (demo)')` — a fake toast that
  did nothing, despite a full Razorpay integration already existing on the
  backend. It now runs a real checkout: creates an order, opens the actual
  Razorpay widget, verifies the payment signature server-side, and updates
  the invoice. **Also found and fixed**: the webhook endpoint
  (`/payments/webhooks/razorpay`) accepted any POST with no signature
  verification at all — anyone who found the URL could have faked a
  "payment succeeded" event. It now verifies the HMAC signature and
  processes the event idempotently. **And**: that webhook route wasn't
  even reachable — it's at `/payments/webhooks/razorpay`, but the
  `permitAll` pattern was `/webhooks/**`, which only matches paths
  starting with `/webhooks/`, not containing it. Razorpay's own servers
  would have gotten a 401. Fixed alongside the earlier `/settings/public`
  fix (same class of bug, found separately).
- **Payments ledger** (admin `/admin/payments`): search/filter/paginate
  every payment, process refunds. While wiring this in, found that the
  admin's existing manual "record payment" modal wrote directly to the
  invoice and never appeared in this ledger — recording a cash payment and
  an online payment went through two disconnected code paths. Fixed by
  routing manual payments through the same endpoint, so there's one
  consistent record.
- **Contact inquiries inbox** (admin `/admin/contact-inquiries`): the
  public Contact form worked and saved submissions, but there was no way
  for staff to ever see them — submissions were silently accumulating with
  no inbox. Also added a status field (New/Contacted/Closed) that didn't
  exist as an API before.
- **Reports & exports** (admin `/admin/reports`): six .xlsx exports
  (bookings, revenue, invoices, galleries, inventory, clients) with a
  date-range picker. No frontend page existed for this at all.
- **Album workflow** (admin `/admin/albums`): create an album for a
  booking and move it through pending → designing → review → revision →
  approved → printing → delivered. No frontend page existed for this at
  all.
- **Global quick search** (admin topbar): the search box in the top bar
  had been purely decorative — a controlled input with no search logic
  behind it at all. Now debounced-searches clients/bookings/galleries/
  invoices/products as you type, with results grouped and clickable.

**One more real bug found while wiring payments**: `useAuthStore` only
ever stored a combined `name` field on the user object, but the Topbar
reads `user.firstName`/`user.lastName` separately — meaning every logged-in
user's name displayed as "undefined undefined" next to their avatar since
the auth-store rewrite two passes ago. Fixed, and the marketing-opt-in
toggle now reflects its real stored value instead of always defaulting to
true.
EOF

## Direct photo uploads (storage CORS)
Staff uploads go straight from the browser to the bucket with a presigned `PUT`. Add a CORS rule on the bucket:
allowed origin = your admin site origin, methods `PUT, GET, HEAD`, allowed headers `*`, expose `ETag`. Without it
uploads still work but fall back to the slower upload through the API.

## Database migrations in this release
V29 album photos, V30 custom-role permissions, V31 attendance shifts/rules, V32 payroll permission, V33 inquiry replies.
Back up the database before first start.
