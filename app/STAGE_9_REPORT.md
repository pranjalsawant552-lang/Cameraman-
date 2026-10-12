# Stage 9 report — final

## Verification status (read this first)
- **Frontend:** `tsc -b` 0 errors, `npm run build` clean, `oxlint` 0 errors (a few pre-existing style warnings),
  `npm test` — 39 tests in 7 files, all passing.
- **Backend: NOT compiled or tested here.** Maven Central is blocked in the build sandbox (HTTP 403), so none of the
  Java has been through `javac` against real dependencies, and the new JUnit tests have not been run. Only a
  syntax-level check was possible, and the pure attendance rules were run in a scratch harness.
  **First step on your side: `cd backend && mvn clean verify`, then send me any errors.**
  Existing tests `AttendanceServiceTest` / `AttendanceHardeningTest` were updated for the new constructor.
- New migrations: **V29** album photos, **V30** custom-role permission rows, **V31** shifts/schedules/holidays/rules,
  **V32** `ATTENDANCE:EXPORT` permission, **V33** inquiry replies. Flyway runs them on start.

## What was built
**Gallery uploads** — direct-to-storage with progress, parallel upload, cancel/retry, multipart fallback; Google Drive
import with progress; original-photo expiry card with safe "delete unselected now" (refuses when nothing is selected
unless confirmed). Fixed `@Async` self-invocation (processing now in `PhotoProcessingService`) and duplicate
`@Transactional` annotations.

**Albums** — chosen photos stored (`album_photos`), reorder, page numbers, notes, cover, add from a client's
selection; locked once approved.

**Staff & permissions** — custom roles now unlock endpoints through `CUSTOM:MODULE:ACTION` authorities (built-in roles
unchanged; Users/Roles management stays ADMIN-only on purpose). Clients page is now a table with a detail drawer
(contact, billing totals, bookings, invoices, galleries, editable details; `GET/PUT /clients/{id}`). Staff cards open a
detail drawer (`GET /staff/{id}/overview`: shoots, this month's attendance, leave).

**Attendance & payroll** — shifts, per-staff schedule (shift, weekly off days, salary, overtime rate), studio holidays,
rules (half-day threshold, minimum overtime, lates per half-day), recalculated daily and monthly reports, payroll report
and CSV export (UTF-8 BOM, spreadsheet-formula guard). Payroll/schedule endpoints (they expose salary) are limited to
ADMIN, SUPER_ADMIN, MANAGER, ACCOUNTANT or `ATTENDANCE:EXPORT`.
Pay rule: unpaid days = absences + ½ per half day + ½ per N late arrivals; per-day = salary ÷ working days;
net = salary − deduction + overtime hours × rate. Approved leave, weekly offs and holidays are paid.
A month still in progress is flagged in the UI.

**Catalog & inquiries** — product edit modal (name, price, GST, stock, category, image, active; blank clears optional
fields; validation on both sides). Contact inquiries now track replies: email a reply from the app or log a call /
WhatsApp, with a per-inquiry history, reply count and "last replied by".

**Polish** — mobile navigation drawer (the admin had no navigation below 1024px), skip links, `<main>` landmarks,
page titles, every table scrolls on small screens and has captions/`scope`, form labels are always tied to their
fields (auto ids), icon buttons labelled, accessible Drawer (focus trap, Escape, focus return).
Vitest + Testing Library suite added (`npm test`).

## Things to know / not done
- Payroll figures depend on punches; a missing "out" punch counts as present with 0 hours worked and is flagged.
- Overnight shifts are not supported (shift must end after it starts).
- The `marketingOptIn` value on the Clients list is a placeholder; the drawer shows the real value.
- A visual, in-browser check of every page at phone width was not possible here; the pass was done by code audit.
- Backend tests added: `AttendanceCalculatorTest`, `PayrollServiceTest`, `ContactServiceReplyTest`. Not yet covered
  by tests: PhotoUploadService, AlbumService, ClientService, StaffOverviewService.

## Setup notes
- Direct uploads need a CORS rule on the storage bucket allowing `PUT` from your site origin (otherwise uploads
  silently fall back to the slower normal upload).
- `GOOGLE_DRIVE_API_KEY` enables Drive import (see DEPLOYMENT.md).
- Custom roles: tick permissions on the Roles page; they apply at the user's next login.
