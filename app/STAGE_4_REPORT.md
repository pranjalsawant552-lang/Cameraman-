# Stage 4 Report — Packages/Products Catalog, Gallery Writes, Customer Portal

## Scope decision, stated upfront
Per your call to prioritize by judgement: I built (1) the Packages/Products
catalog — foundational, since it's what billing should price *from* instead
of free-text amounts; (2) Gallery write-actions — the core day-to-day
photographer/editor workflow; and (3) wired the entire Customer Portal's
existing read pages to real data. WhatsApp/Engagement (campaigns, follow-up
review) and deep polish (responsive audit, audit-log UI, code-splitting)
did **not** get touched this stage — they're real, named gaps for a Stage 5,
not silently dropped.

## What changed

### Packages & Products — didn't exist at all before this stage
- No page, no route, no nav link, no types existed for this anywhere in the
  frontend, despite the backend having full `PackageController` and
  `ProductController` modules already built. Section 7 of your spec
  ("admin should be able to configure pricing... do not hardcode prices")
  had literally nothing behind it in the UI.
- **`pages/admin/Catalog.tsx`** (new) — a tabbed Packages/Products view.
- **`components/catalog/NewPackageModal.tsx`** / **`NewProductModal.tsx`**
  (new) — real creation forms, field names verified against
  `PackageDto.CreateRequest` / `ProductDto.CreateRequest`. Product creation
  supports adding a new category inline.
- Package archiving wired to `POST /packages/{id}/archive`.
- Added the route (`/admin/catalog`) and sidebar nav entry — this is genuinely
  new surface area, not a rewire of something hidden.
- **Not built**: editing an existing package/product's price, and — the
  natural next step — having `NewInvoiceModal` (from Stage 2) let staff pick
  a line item *from* this catalog instead of typing a description and price
  freehand. That integration is the real payoff of this catalog existing and
  isn't done yet; flagging it as the highest-value Stage 5 item.

### Galleries — found real bugs, not just gaps
- **Confirmed a bug, not just a missing feature**: "Publish gallery" called
  `push('"..." published — client notified via WhatsApp, email and in-app')`
  — a toast claiming three notifications were sent, with **no API call
  behind it at all**. It told the studio something happened that didn't.
  Fixed: now calls `POST /galleries/{id}/publish` for real, and is disabled
  when the gallery has zero photos (publishing an empty gallery to a client
  isn't a state that should be reachable from the UI).
- **Confirmed a second, quieter bug**: `GalleryDetail.tsx` read gallery data
  from `mockData` directly (same pattern as the Stage 1 Dashboard bug) —
  meaning even in live mode, this page would always show stale demo data
  regardless of what the backend had. Fixed via a new `fetchGalleryById`.
- **`components/gallery/NewGalleryModal.tsx`** (new) — creates a draft
  gallery via `POST /galleries`, with selection-limit, watermark, and
  download-permission controls matching `GalleryDto.CreateRequest`.
- **Real photo upload**, not a stub: found the backend has a direct
  multipart endpoint (`POST /galleries/{id}/photos/upload`), simpler than
  building the presigned-URL flow the DTOs also support. Wired using the
  `apiPostForm` helper built in Stage 3 for attendance CSV import — same
  underlying capability, second real use.
- **Fixed a duplicated bug**: the customer-facing `pages/portal/Gallery.tsx`
  had the *exact same* mockData-bypass problem as the admin gallery page.
  Fixed the same way, via a new `fetchMyGalleries()` calling
  `GET /galleries/mine`.
- **Not built**: a dedicated "Download all" action (there's no bulk-zip
  endpoint on the backend — only a per-photo download-url — so I removed the
  dead button rather than build something that only half-works) and visible
  per-photo numbering in the admin grid (`IMG_0001` style — section 19).
  Demo mode fakes this in `fetchGalleryPhotos`; live mode shows the
  backend's real filename instead, since the backend doesn't expose a
  separate display-number field. Worth a small backend addition later.

### Customer Portal — the whole thing was reading mock data
This was the most important discovery of the stage. `Home.tsx`,
`Bookings.tsx`, and `Invoices.tsx` under `pages/portal/` **all** imported
`BOOKINGS`/`GALLERIES`/`INVOICES` from `mockData.ts` directly — the same
architecture bug as the Stage 1 Dashboard, just never caught because nothing
had tried to wire the portal yet. In live mode, every customer would have
seen the same demo data regardless of who logged in.
- Added `fetchMyBookings(clientId)` → `GET /bookings/client/{clientId}`,
  verified the backend enforces `#clientId == authentication.principal.id`
  server-side — a client genuinely cannot fetch anyone else's bookings by
  passing a different ID, checked directly in `BookingController.java`, not
  assumed.
- Added `fetchMyInvoices()` → `GET /billing/invoices/my` (server resolves the
  client from the JWT itself, no ID needed).
- **Fixed a real correctness bug along the way**: the old `Invoices.tsx`
  matched a client's invoices by string-comparing `clientName` against
  `"${firstName} ${lastName}"` — fragile, and wrong for any two clients who
  share a name. The real endpoint matches by ID server-side.
- All three pages now have loading skeletons where they previously rendered
  instantly from synchronous mock data — a real UX gap that only becomes
  visible once real network latency exists.
- **Not built**: registration, email/mobile verification, and forgot/reset
  password flows (section 12) — the login itself is real (Stage 1), but a
  new customer still can't self-register through the UI. This is the
  biggest remaining portal gap.

## What I actually verified
```
npx tsc -b       → 0 errors
npm run build    → clean production build (945 KB main bundle; the
                     code-splitting warning is pre-existing, not new this stage)
npx oxlint src/  → 0 errors, 22 warnings total; isolated the 15 files touched
                     this stage to confirm only the codebase's existing
                     set-state-in-effect style pattern, plus one real unused
                     import I found and fixed before this report
```
No backend changes this stage — everything needed already existed
server-side (which is itself a good sign for the earlier Stage 1 audit's
assessment that the backend was more complete than the frontend exposed).

## Carried into Stage 5
WhatsApp/Engagement (broadcast campaigns, follow-up suggestion review —
sections 9, 15–17), customer registration/verification, catalog↔billing
integration (pick line items from the new catalog instead of free text),
per-photo numbering, and the originally-planned polish pass (responsive
audit, audit-log UI, bundle code-splitting, a final security review).
