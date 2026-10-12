# Stage 2 Report — Core Operational Workflows Wired (Reads + Writes)

## Scope decision, stated upfront
The original Stage 2 scope in my plan listed six areas (bookings, billing/POS,
inventory, packages/products, staff, attendance, galleries). Once I opened the
actual pages, two things changed my plan and I'm flagging that rather than
quietly shrinking scope:

1. **Billing/POS didn't exist as a screen at all** — `Invoices.tsx` was
   read-only with no way to create an invoice or record a payment. That's the
   single most important workflow in the whole spec (section 6), so it got
   real design + build effort, not just a wiring pass.
2. Doing genuine write-wiring (forms, validation, error handling, optimistic
   updates with rollback) for six modules at real quality in one stage isn't
   honest to attempt. I fully completed **Bookings** and **Billing/Invoices**
   and **Inventory's core stock action**, and moved **Staff, Attendance,
   Packages/Products, and Galleries** to Stage 3 rather than shallow-wire them.
   Stage 3 in my plan already covered customer-portal + engagement; I'm
   folding these in alongside it, and will say so again at the top of that
   report.

## What changed

### Bookings — real create/update lifecycle
- **`components/booking/NewBookingModal.tsx`** (new) — the "New booking"
  button did nothing before this. Real form → `POST /bookings`. Field names
  verified against `BookingDto.CreateRequest` in the Java source.
- **`pages/admin/BookingDetail.tsx`** — **this was a real bug**: "Update
  stage" just fired a toast saying "Booking updated" without changing
  anything, anywhere. Replaced with a real stage `<Select>` that calls
  `PATCH /bookings/{id}/stage`, updates optimistically, and **rolls back if
  the request fails** (e.g. if the backend rejects an invalid stage
  transition) rather than silently showing a stage that was never saved.
  Added a real "Cancel booking" flow with a confirmation modal and reason
  field, calling `POST /bookings/{id}/cancel`.
- `mockApi.ts` — added `createBooking`, `updateBookingStage`, `cancelBooking`,
  `rescheduleBooking`. Demo mode (no backend configured) still works: these
  mutate the in-memory mock arrays so the flow is genuinely exercisable, not
  just disabled, when exploring without a backend.
- Booking creation surfaces the backend's real double-booking rejection
  message in the form — the conflict-detection logic verified as solid in
  the Stage 1 audit now actually reaches a human.

### Billing — a real POS screen, built from nothing
- **`components/billing/NewInvoiceModal.tsx`** (new) — line items with
  description/qty/rate, live subtotal → discount → total calculation,
  submits to `POST /billing/invoices`. Field names verified against
  `BillingDto.CreateInvoiceRequest`/`InvoiceItemRequest`.
- **`components/billing/RecordPaymentModal.tsx`** (new) — records a payment
  against an invoice's balance due, blocks overpayment client-side, calls
  `POST /billing/invoices/{id}/payment`.
- **`pages/admin/Invoices.tsx`** — wired both in; clicking a balance-due
  amount opens the payment modal directly from the list.
- **Honest gap found, not fixed**: the backend's `CreateInvoiceRequest` has
  no tax field at all (checked the DTO directly — no `taxPercent`, no GST
  computation). Section 6/7 of your spec asks for tax support. I did not
  invent a tax field in the UI that the backend can't honor; flagging this
  as a real P1 backend gap for a future stage rather than a frontend TODO.
- **Not built (real gap, not silently skipped)**: there's still no invoice
  *detail* page — no route, no page component existed before or after this
  stage. "View invoice", "Download PDF", "Send to customer" from section 14
  are unaddressed. This is the biggest remaining Billing gap.

### Inventory — real stock movement
- **`components/inventory/AdjustStockModal.tsx`** (new) — stock in/out,
  returns, damaged, and manual corrections, calling
  `POST /inventory/{id}/adjust-stock`. Client-side guards against taking
  stock-out below zero using the real on-hand count.
- `fetchInventory` now calls the real paginated `GET /inventory` (adapted to
  the flat list the page renders) instead of mock data.
- `fetchLowStockCount` (new) wired to `GET /inventory/low-stock` and used to
  fill in the Dashboard's `lowStockItems` tile, closing the Stage 1 TODO left
  there.
- **Not built**: creating new inventory items (`POST /inventory`) and editing
  existing ones (`PUT /inventory/{id}`) — the page has no "Add item" or edit
  action at all, before or after this stage. Real gap for Stage 3/4.

## What I actually verified
```
npm install     → clean (fresh install, node_modules was excluded from the Stage 1 zip)
npx tsc -b      → 0 errors
npm run build   → clean production build
npx oxlint src/ → 0 errors, 19 warnings — all pre-existing patterns in files
                  I didn't touch (Staff.tsx, Galleries.tsx, StatTile.tsx);
                  the 9 files I changed carry 4 warnings, all inherited from
                  Invoices.tsx's existing (pre-Stage-2) effect pattern, not
                  new code — confirmed by isolating lint to just those files.
```
Same caveat as Stage 1: no Maven Central access here, so nothing on the
backend changed this stage (by design — no backend gaps required a fix to
support this UI work), and the frontend is the only thing that needed
compiler verification.

## Carried into Stage 3
Staff management, Attendance, Packages/Products catalog, and Galleries
write-actions (create gallery, upload, assign to client, selection limits) —
plus the originally-planned customer portal and engagement/WhatsApp scope.
Invoice detail/PDF and inventory item CRUD are explicitly flagged above as
real gaps to pick up, not forgotten.
