# Completion report

**Verification status.** The frontend was verified here: `npm ci`, `tsc -b`, `npm run build` and `npm run lint` pass (0 errors, 40 style warnings).
The **backend was NOT compiled or tested** (no Maven or Maven Central in my environment). Run `cd backend && mvn clean verify` first and send me any errors.

## Done

**Phase 1: client self-booking.** `/portal/book` wizard (category, package, date, time, details and intake, review, confirm, optional advance payment).
Server (`BookingService`): for a client the package must exist and be active; price, type and advance are derived server-side; past dates and bad time ranges are rejected; reschedule now checks slot conflicts and handles the unique-index race.
Admin `NewBookingModal` shows live availability, closed days and a package picker. Public CTAs and portal nav link to the flow; login and register honour `?next=`.
Fixed: `FAMILY` and `PRODUCT` were not in the backend `PackageCategory` (admin booking would 500); frontend order stages did not match the backend enum (stage change always failed).

**Phase 2 (partial): selection and gallery.** Portal gallery has selection mode (heart, counter, limit, review and submit, read-only after lock, download selected). Admin `/admin/selections` (list, review, approve, re-open, lock). Gallery detail has copy link, WhatsApp and "ask client to pick photos".
Server: selected photos must belong to the selection's gallery; expiry enforced on every action; lock moves the booking to SELECTION_LOCKED; approve (LOCKED only) moves it to ALBUM_DESIGNING and notifies the client; reminders already skip non-OPEN selections.

**Phase 3: account security.** Forgot and reset password pages, change password, log out of all devices (`POST /auth/logout-all`), profile and marketing-consent editing (the old portal Settings only showed toasts). Server: forgot-password no longer reveals whether an email exists; reset tokens stored as SHA-256 hashes.

**Phase 4: invoices and payments.** InvoiceDetail: Finalize, Send (WhatsApp/Email, new `POST /billing/invoices/{id}/send-email`), Record payment, Cancel. Server: totals rounded HALF_UP per tax component, invoice discount percent now applied, discount capped at the subtotal; payments blocked on DRAFT/CANCELLED; cancel blocked while payments exist; refunds now reverse the invoice (REFUNDED / PARTIALLY_PAID) and are cumulative; verify is idempotent and row-locked; clients can only create orders for their own finalized invoice or booking and never above the balance; booking advances update the booking; clients no longer see DRAFT invoices; manual payments fail loudly instead of saving an unreconciled payment.

**Phase 7 (server side only).** Intake answers can be saved by the booking's own client, are validated (required, number, date, select options, length) and are ownership-checked. The wizard asks the questions. API client for admin CRUD is in `lib/api.ts`; the admin screen is not built.

**Phase 8 (partial).** Portal bookings: cancel and reschedule own bookings, stage stepper shows all 11 real stages.

**Tooling.** `.oxlintrc.json` now ignores `node_modules` (lint previously reported 200 false errors).

## Tests added (backend, unverified until you run them)
`BookingServiceSelfBookingTest`, `IntakeQuestionServiceTest`, `InvoiceGstTest`, `PaymentServiceTest`, `BillingServiceLifecycleTest`, `AuthServicePasswordResetTest`, `PhotoSelectionServiceTest`. Existing `BookingServiceCalendarTest` constructor updated for the new dependency.

## Added in the second pass (Phase 5)
- **Safety rules** (new `UserAdminService`, used by `PUT /users/{id}`, `activate`, `deactivate`): nobody can deactivate themselves or change their own roles; the last active SUPER_ADMIN can't be deactivated or stripped; only a SUPER_ADMIN can grant or touch SUPER_ADMIN; deactivation revokes refresh tokens immediately; everything is audit-logged. Deactivated accounts were already rejected at login (`isEnabled`).
- **Roles** (`RoleService`): names normalised and validated, SUPER_ADMIN fully locked, built-in roles can't be deactivated, unknown permission ids rejected, changes audited.
- **Staff**: employee ID must be unique (attendance depends on it); staff responses now include the account status.
- **UI**: `/admin/roles` (role list, grouped permission checklist, create, activate/deactivate); Staff page has Edit (all fields incl. Employee ID and role), Deactivate/Reactivate with confirmation, and a "Deactivated" badge; sidebar items are hidden by role (`lib/access.ts`).
- Tests: `UserAdminServiceTest`, `RoleServiceTest`, `StaffServiceTest` (all unverified until you run Maven).
- Assumptions: the login response has roles but no permissions, so menus follow a role map that mirrors the controllers' `@PreAuthorize` rules (UX only, the server enforces). The Staff "detail drawer" (bookings, attendance, leave balance) and a separate Clients admin page are not built. Custom roles can be created and given permissions, but the backend's `@PreAuthorize` checks use role names, so a custom role's permissions don't yet unlock endpoints by themselves.

## Added in the third pass (Phases 6-11)
**Migrations (new, run automatically by Flyway):** `V26__notification_preferences.sql`, `V27__attendance_hardening.sql` (removes duplicate punches, then adds a unique index; adds `attendance_devices`), `V28__attendance_corrections.sql`. Back up the database before first start: V27 deletes duplicate punch rows (keeping the earliest).

**Phase 6:** `/admin/calendar` (month view of bookings, close/reopen days or a date range, managers/admins only to change); booking page now shows time, venue, expected delivery, client notes, intake answers, editor assignment, courier details, stage-change note, order history and a Reschedule button; progress bar no longer divides by zero.
**Phase 7:** `/admin/intake-questions` (add, edit, order, required, show/hide per category).
**Phase 8:** notification preferences are stored per user and enforced in `NotificationPublisher` (booking, order progress, gallery, marketing can be switched off; payments and account/work alerts cannot; marketing also respects the account-wide consent). Portal invoices: PDF receipt download, no "Pay" button on cancelled invoices. Booking cards link to gallery/invoices and show courier tracking.
**Phase 9:** idempotent punches (code + time + device), duplicates counted separately in CSV results; ZK status column parsed (0/4 in, 1/5 out, others unknown; daily in/out still uses first/last punch of the day); `findAll()` scans removed from unmatched-codes and link-code (staff list scan in the daily summary remains: bounded by headcount); linking a code can no longer steal another staff member's code; machines are tracked in `attendance_devices`, new serials start blocked until an admin approves them on the Attendance page (shared-key requests and the old env whitelist still work); missing-punch corrections (staff request, manager approve/reject, audit trail); leave: staff can only file/read/cancel their own, overlap check, 60-day cap, no past dates for staff, `/leaves/mine`; all staff roles may file leave.
**Phase 10:** broadcast recipient list with delivery status, inventory barcode lookup, package duplicate, testimonial edit, `/admin/my-schedule` (my shoots, leave, missed-punch requests). Global search and the six report exports already existed.
**Phase 11:** accessible modal (dialog role, Escape, focus trap and restore, scroll lock, labelled close), route-level error boundary, input limits on new forms, docs updated.

New tests: `UserAdminServiceTest`, `RoleServiceTest`, `StaffServiceTest`, `NotificationPreferenceServiceTest`, `AttendanceHardeningTest`, `LeaveServiceTest`, `AttendanceCorrectionServiceTest` (plus those listed above). Existing `AttendanceServiceTest` was updated for the new constructor and `saveAndFlush`.

## Still not done (honest list)
- **Never compiled/tested on the backend.** Expect a few compile or test errors on the first `mvn clean verify`; send them to me.
- Phase 2: direct-to-storage uploads with progress, import from Drive, originals-expiry controls, and an album photo set (no table for it).
- Phase 5: staff detail drawer and a separate Clients admin page; custom-role permissions don't unlock endpoints (the backend checks role names).
- Phase 9: per-staff shifts, late/early/overtime/half-day thresholds, weekly offs and holidays, payroll CSV (only the global work-hours config exists).
- Phase 10: product edit UI (API client not added), contact-inquiry reply tracking, CMS-editable public images.
- Phase 8: portal "booking detail" is the expanded card, not a separate page; the studio's cancellation window is explained in text but not fetched from settings.
- Phase 11: no automated accessibility audit or full responsive pass of every page; no frontend test suite.

## Needs you
Razorpay keys, WhatsApp and email credentials, Google Drive credentials, S3/MinIO storage settings, a decision on public `/g/:token` links, whether custom roles should drive endpoint access, and the first Maven build.
