# Changes — Oct 2026 bug/enhancement round

NOT compiled with Maven in the authoring environment (no Maven Central access): run
`./mvnw clean verify` (backend) and `npm ci && npm run build && npm test` (frontend, verified there).
New Flyway migrations: V34 (session idle timeout setting), V35 (booking location type + pickup tracking).

1. Self-registered client login: User.isEnabled() allowed only ACTIVE; PENDING_VERIFICATION is now allowed
   (login promotes to ACTIVE). Register no longer saves the null tokens it gets back; it signs in instead.
2. Bookings are INDOOR/OUTDOOR (bookings.location_type); shown as a badge, filter on list, field in forms.
3. Client-name autosuggest (components/shared/ClientCombobox) in Booking, Studio session, Outdoor shoot, Album forms.
4. Pickup tracking: mark ready / picked up / send reminder (WhatsApp + email, truthful result), list filter.
5. Every booking gets an invoice after commit (BillingService.createInvoiceForBooking); POST /bookings/{id}/invoice.
6. Invoice: download, print, email, WhatsApp. Fixed wrong stored PDF key, @Async email hiding failures,
   WhatsApp "sent" flag set when nothing was sent, expired signed link reuse.
7. Inventory category dropdown + lenient parsing; package category enum mismatch (STUDIO) + null GST default.
8. Staff page crash: formatDate() threw RangeError on empty dates.
9. Album create: blank bookingId/clientId violated FKs; now pickers + backend validation.
10. Sessions: refresh token always issued, proactive/multi-tab-safe refresh, all request types refresh,
    Settings > "Session & security" idle timeout (default 30 min, 0 = off). Also fixed Settings saves that
    were written under keys nothing reads (camelCase vs dotted keys).

## Storage / upload round (see STORAGE.md)

NOT compiled with Maven in the authoring environment (no Maven Central access). Run `./mvnw clean verify`. New migration: V36. New deps: twelvemonkeys imageio-jpeg/webp (pom).

- Storage split into providers behind `StorageService` (S3 + Google Drive); originals to Drive when configured, everything else S3. Config in `storage.routing.*`, `google.drive.storage.*`.
- Chunked resumable upload for Drive, duplicate skipping, retry/backoff and clear errors in the frontend.
- Processing: single decode, thumbnail from preview, streamed temp files, no orphaned derivatives on retry, failure reasons shown.
- `StorageServiceTest` moved to `storage/provider/S3StorageProviderTest`; added routing, signed-link and image tests.


---
# Round 2 (11 Oct 2026)

1. **Booking status**: new `BookingStatusPanel` — one-tap "Mark <next step>", Back (undo), and "Jump to a different step" tucked away. Bookings list rows get the same one-tap next-step button.
2. **Invoice per booking**: Studio sessions and outdoor shoots now also raise an invoice on creation (`AutoInvoiceService`, after commit, never blocks the save). Invoices page has **Generate missing invoices** to backfill existing records (`POST /billing/invoices/backfill`). Invoice.bookingId holds the booking / session / shoot id.
3. **Indoor / Outdoor modules** list the INDOOR / OUTDOOR bookings made from the Bookings page (`LinkedBookings`).
4. **Client drawer** shows all bookings, studio sessions + outdoor shoots, invoices, galleries and photo selections (no more "latest 10").
5. **Gallery sharing**: `gallery_viewers` (V38). Staff pick extra clients who can view a gallery and which of them can select photos; each selector gets their own selection. Clients see shared galleries in their portal.
6. **Invoice print / download**: drawn in the browser in two layouts (Bill book A5 as in the printed bill book, and A4). Correct Marathi shaping; no dependency on the PDF service or storage. Server PDF templates now carry real @page sizes.
7. **Adjust stock**: UI sent STOCK_IN/STOCK_OUT/... which the server's `TransactionType.valueOf` rejected (HTTP 500). Server now parses both vocabularies and returns 400 for unknown values; modal sends valid values.
8. **Barcodes**: every item/product has a code (SKU fallback, V37 backfills). Print one label or all (Inventory, Catalog > Products). Inventory page scan modes: Open item / Take 1 out / Put 1 back in. Endpoints: `POST /inventory/barcode/{code}/scan`, `/products/barcode/{code}/scan`.
9. **Lombok**: pinned 1.18.32 -> 1.18.42 (JDK 21-25), added `lombok-mapstruct-binding` between Lombok and MapStruct in `annotationProcessorPaths`, `release` instead of source/target.

Migrations added: V37 (barcode/SKU backfill), V38 (gallery_viewers).
Not verified here: the Java build (no Maven Central access in the authoring environment). Run `./mvnw clean verify`.

Update: the A4 invoice now uses the identical bill-book design, scaled to A4 (single template in frontend/src/lib/invoiceHtml.ts).

CORS: default dev allow-list now includes http://localhost:5174/5175 (Vite moves to 5174 when 5173 is busy).

Public pages update merged (Home, About, Services, Packages, FAQ, Contact, NotFound, theme switcher, command palette). SecurityConfig: GET /packages/active is now public; the existing /files/* signed-link rule was kept.

Login: (1) CORS now accepts http://localhost:* / 127.0.0.1:* by default (patterns), so a Vite port change no longer breaks login; set CORS_ALLOWED_ORIGINS explicitly in production. (2) V39 repairs the seeded admin password hash (V2's hash never matched Admin@2024) and clears any lock. (3) Login shows a clear message when the server is unreachable.


---
# Round 3 (12 Oct 2026) — workflow review fixes

NOT compiled with Maven in the authoring environment (no Maven Central access); changed Java files were syntax-checked with javac only. Run `./mvnw clean verify`. Frontend: tsc, 63 tests and production build pass. No new migrations.

1. **Booking status now moves.** Staff-entered bookings (and studio sessions / outdoor shoots) start CONFIRMED; a client's online request stays PENDING until staff press the new **Confirm booking** button (`POST /bookings/{id}/confirm`) or the first payment is recorded. Order stage "Shoot Done" or later sets COMPLETED; "Advance Paid" confirms a pending booking; stepping back before "Shoot Done" reopens it. The next-day reminder now also covers RESCHEDULED bookings.
2. **Booking and invoice money agree.** Every payment/refund on an invoice copies advance/balance to the booking (BillingService.syncBookingFromInvoice). A payment that names only the booking is applied to its invoice. Editing a booking total re-prices its automatic invoice (refused if below the amount already paid) and drops the stored PDF so it is regenerated.
3. **Cancelling** a booking / session / shoot cancels its invoice when nothing was paid; if an advance was received the invoice stays open and staff get a "Refund decision needed" notification.
4. **Overdue invoices.** Nightly `InvoiceOverdueScheduler` (00:30) marks SENT/PARTIALLY_PAID invoices past their due date OVERDUE. Automatic invoices now get a due date = shoot date + 7 days (older invoices without a due date are not affected). Pending count in the revenue summary now includes partially paid invoices.
5. **Gallery link** is kept when a gallery is published again (new link only if none, expired, or `regenerateLink=true`); the client is only re-notified when a new link is made.
6. **Booking edit validation.** `PUT /bookings/{id}` now rejects past dates, closed days, end-before-start and slot clashes; status cannot be set to CANCELLED through it (use Cancel). Reschedule keeps the original duration when no end time is sent and rejects past dates.


---
# Round 4 (12 Oct 2026) — Redis outage, album picker, photo selection, payment lock

NOT compiled with Maven here (no Maven Central access); changed Java files were syntax-parsed only. Run `./mvnw clean verify`.
Frontend: tsc, 63 tests, lint (0 errors) and production build pass. No new migrations.

1. **"Backfill invoice failed: Unable to connect to Redis".** Invoice creation reads the studio settings, which are `@Cacheable`.
   `RedisConfig` declared a `CacheErrorHandler` bean, but Spring only uses one returned by a `CachingConfigurer`, so it was never
   applied and every cache call threw when Redis was down. New `CacheResilienceConfig` wires it in: a cache outage now logs a
   warning and falls through to the database. (Still start Redis; the JWT blacklist check also needs it.)
   Backfill now returns the first failure reasons and the Invoices screen shows them.
2. **Album "Add from a gallery" showed nothing.** It only looked for galleries linked to the album's booking. It now falls back to the
   client's galleries, then all galleries, has a gallery picker, "Add all", loading/empty states and a link to create a gallery.
3. **Client photo selection.** Picking is instant and local; saved as a draft automatically 2.5 s after the last change and with a
   **Save draft** button (`PUT /selections/token/{token}/draft`, replaces the whole pick list in one call). Shows "Draft saved at …",
   warns before closing with unsaved picks. Click any photo for a **full-screen viewer** (arrows, swipe, Esc, select from inside).
   "All / Selected" filter. Grid uses thumbnails so hundreds of photos stay fast.
4. **Payment lock.** Fully paid invoice => gallery unlocked: clean (no watermark) photos in the portal + downloads, automatically.
   Also covers galleries not linked to a booking (when the client owes nothing else) and invoices re-priced to fully paid.
   **Staff override:** Gallery detail > "Watermark & downloads" card (`PUT /galleries/{id}/downloads`) to unlock or lock manually.
   **Security fix:** while locked, clients no longer receive the clean preview URL, and `download-url?original=true` no longer returns the
   full-resolution file to a client of an unpaid gallery.
5. **Invoices in the client drawer.** They appear once generated (fix 1, then "Generate missing invoices"); bookings, studio sessions
   and outdoor shoots without one now have a **Create invoice** button in the drawer.
