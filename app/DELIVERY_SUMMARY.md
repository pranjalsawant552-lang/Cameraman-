# Cameraman Photo Studio — Delivery Summary

Five stages of audit + real implementation work. Each stage's full detail is
in its own report (`STAGE_1_REPORT.md` through `STAGE_5_REPORT.md`) — this
is a index, not a replacement for reading them.

## Correction (Stage 6) — read this first
Earlier reports said invoices had **no tax support**. That was wrong — I'd
only checked the create-request DTO, not the domain model. Full GST
calculation (18% CGST+SGST) already existed on the backend; the frontend
just wasn't showing it, which meant the invoice-creation screen displayed a
total that didn't match what the backend actually charged. Fixed, along
with building the invoice detail/PDF page. Full detail in `STAGE_6_REPORT.md`
— flagging it here too since it changes an earlier gap-list entry.

## What this was, honestly
An already-mature Spring Boot backend (31 modules, 25 migrations, solid
server-side RBAC and data-integrity design — confirmed by reading the code,
not assumed) was sitting almost entirely disconnected from its own frontend,
which ran on mock data throughout. The work across these five stages was
mostly **connecting real infrastructure that already existed**, finding the
bugs and missing pieces that only became visible once real wiring was
attempted, and building the handful of screens that had never been built at
all (Billing/POS, Packages & Products, WhatsApp Broadcast, Registration).

## Real bugs found and fixed (not just gaps filled)
- CORS was wildcard + credentials — any origin could ride a visitor's JWT (Stage 1)
- Login form ignored whatever was typed, always force-logged a sample account (Stage 1)
- Dashboard read mock data directly, bypassing the API layer entirely — so it could never show real data even once wired (Stage 1)
- "Update stage" on a booking was a fake toast, changed nothing (Stage 2)
- "Publish gallery" claimed to notify the client via WhatsApp/email/in-app — no API call existed behind it (Stage 4)
- The entire Customer Portal (Home, Bookings, Invoices, Gallery) had the same mock-data-bypass bug as the Dashboard — every logged-in client would have seen the same demo data (Stage 4)
- "Add staff" and "Add recommendation rule" both said "coming soon in this demo" (Stages 3, 5)
- A client's portal invoices were matched by string-comparing their name — silently wrong for two clients sharing a name (Stage 4)

## Built from nothing (no screen existed before)
- Billing/POS: invoice creation with line items, discounts, payment recording (Stage 2)
- Packages & Products catalog, admin-configurable (Stage 4)
- WhatsApp Broadcast: campaign composer, audience preview, history (Stage 5)
- Customer self-registration (Stage 5)
- Gallery creation, photo upload (Stage 4)
- Invoice detail page with full tax breakdown and PDF download (Stage 6)
- Public marketing site: Home, About, Contact — CMS-backed, funnels to registration (Stage 7)
- Indoor (Studio Sessions) and Outdoor Shoots admin modules — separate from generic Bookings, matching the backend's real domain split (Stage 7)
- Audit Logs, admin CMS editor, inventory item create/edit, verification (email+mobile OTP) (Stage 8)

## Verified, every stage, with real tooling — not read-by-eye
`npm install`, `npx tsc -b`, `npm run build`, `npx oxlint src/` all run
clean at the end of every stage. Backend Java changes (2 total: CORS config
in Stage 1) were reviewed carefully but **not** compiler-verified — this
sandbox has no Maven Central access. Run `mvn clean verify` before deploying.

## Named gaps — nothing silently dropped
- **No per-invoice tax rate override** — GST (18% CGST+SGST) is computed automatically and correctly on every invoice, but there's no way to mark one IGST (interstate) or tax-exempt (Stage 6 — corrected from an earlier wrong claim of "no tax support at all")
- **No invoice detail/PDF page** — ~~list + payment-recording only~~ **done in Stage 6**: full line items, tax breakdown, and PDF download now exist
- **No inventory item edit**, only stock movement (Stage 2)
- **No per-photo visible numbering** (IMG_0001-style) in live mode — backend doesn't expose a display-number field (Stage 4)
- **No bulk gallery download** — only per-photo download URLs exist server-side (Stage 4)
- **No customer email/mobile verification flow** — registration works, verification doesn't yet (Stage 4/5)
- **No payment gateway integration** — the client portal's "Pay" button is honestly labeled as not wired; needs real gateway credentials and backend webhook work this environment can't provide (Stage 5)
- **No bundle code-splitting** — ~~997 KB main JS bundle~~ **fixed in Stage 8**: split into a 304 KB main bundle + per-route chunks; the chunk-size warning present since Stage 1 is gone
- **No admin CMS editor** — **built in Stage 8**: staff can now write the hero slides, testimonials, and page content the public site reads
- **No Audit Log UI** — **built in Stage 8**; the backend endpoint had been ready and unused since it was written

## Setup
- Backend: copy `backend/.env.example` → `.env`, fill in real secrets, run
  Flyway migrations, `mvn clean verify` (unverified by me — no Maven access
  in this sandbox).
- Frontend: copy `frontend/.env.example` → `.env`, set `VITE_API_BASE_URL`
  to your backend's `/api` path. Without it, the app runs standalone on
  demo data exactly as it always did — nothing breaks by leaving it unset.
