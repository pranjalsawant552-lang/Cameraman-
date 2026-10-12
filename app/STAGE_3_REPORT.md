# Stage 3 Report — Staff & Attendance Fully Wired (Reads + Writes)

## Scope decision, stated upfront (same honesty pattern as Stage 2)
Original Stage 3 scope was six areas: Staff, Attendance, Packages/Products,
Gallery write-actions, Customer Portal, WhatsApp/Engagement. I completed
**Staff** and **Attendance** properly — including a two-step backend
orchestration that took real investigation to get right — and I'm stopping
there rather than rushing the other four to say "done." They move to
**Stage 4** alongside the originally-planned polish work. Stage 4's report
will say this again at the top so it isn't buried here.

## What changed

### Staff — real account + profile creation
- **Confirmed the bug, not just a gap**: "Add staff" showed the toast
  *"Staff creation form coming soon in this demo"* — an honest placeholder,
  but a non-functional one.
- **Real discovery that shaped the build**: staff creation isn't one backend
  call. The account lives in the generic `users` table (`POST /users`), the
  studio-specific profile — department, designation, employee ID, booking
  capacity — lives in a separate `staff` table (`POST /staff`) that
  *requires* an existing `userId`. I verified this against
  `StaffDto.CreateStaffRequest` directly rather than assuming a single
  endpoint existed.
- **`components/staff/AddStaffModal.tsx`** (new) — one form, two calls under
  the hood in `createStaffMember()`. If the account creates but the profile
  step fails, the error message says exactly that ("Account was created,
  but the staff profile failed...") instead of a generic failure that would
  hide a half-created record from the admin.
- `fetchStaff` and `toggleStaffAvailability` wired to real
  `GET /staff` / `PATCH /staff/{id}/toggle-availability`, adapted against the
  verified `StaffDto.StaffResponse` fields.
- **Real dead-code removal**: deleted `saveStaff()`, a mock function nothing
  in the app called (grepped first to confirm before removing — see audit
  item 45, "unused components/dead code").

### Attendance — real punches, leave, devices, config
This page (468 lines) was already a genuinely functional, well-designed UI
against mock data — not a placeholder like Staff's button was. The work here
was wiring its twelve backing functions to the real endpoints, not building
new UI.
- **`fetchDaySummary`** — the one function that needed real design thought.
  The backend has no single endpoint that returns computed status
  (Present/Late/Absent/On Leave/Weekly Off) for every staff member on one
  day; that status is only computed server-side inside the *per-staff*
  monthly report. Rather than re-implement that late/absent logic a second
  time in the frontend — which is exactly the kind of business-rule
  duplication the audit was warning about — this calls the backend's
  monthly report once per staff member and reads off today's entry. It's
  N requests for N staff on this one screen; documented as a real perf
  trade-off in the code, with the actual fix (a dedicated
  `GET /attendance/summary` that returns status directly) named for a
  later stage rather than silently accepted.
- `fetchMonthlyReport`, `fetchWorkHoursConfig`/`updateWorkHoursConfig`,
  `fetchWebhookConfig`/`regenerateWebhookSecret`, `fetchUnmatchedCodes`,
  `linkEmployeeCode`, `manualAttendanceEntry`, `fetchLeaveRequests`,
  `actOnLeaveRequest` — all wired to their real endpoints, field-for-field
  verified against `AttendanceDto.java`.
- **`importAttendanceCsv`** needed a new capability, not just a new call: the
  existing `apiClient.ts` only knew how to send JSON. CSV import is a real
  multipart file upload. Added `apiPostForm()` to `apiClient.ts` — it
  deliberately does *not* set a `Content-Type` header, because the browser
  has to set that itself to include the multipart boundary; setting it by
  hand is a common mistake that silently breaks the upload.

## What I actually verified
```
npx tsc -b       → 0 errors
npm run build    → clean production build
npx oxlint        → 0 errors on every file touched this stage (one self-inflicted
                     unused-variable warning found and fixed before this report)
```
No backend changes this stage, so the Maven-access caveat from Stages 1–2
doesn't apply here — nothing needed Java verification.

## Carried into Stage 4
Packages/Products catalog (admin-configurable pricing — spec section 7),
Gallery write-actions (create, upload, assign to client, selection limits —
sections 18–19), Customer Portal (registration, verification, bookings,
invoices, gallery access — section 12–13), and WhatsApp/Engagement (broadcast
campaigns, follow-up suggestion review — sections 9, 15–17). Plus the
originally-planned Stage 4 polish: responsive/error-state pass, audit log UI,
reports/analytics UI, and a final security review.
