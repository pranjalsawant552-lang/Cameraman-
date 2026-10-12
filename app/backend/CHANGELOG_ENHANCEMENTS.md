# Backend Enhancement — Changelog

This documents a full audit of the existing backend and the enhancements made
on top of it. The codebase was already mature (20+ modules, consistent
pagination/filtering/search, Flyway-versioned schema) — the work below is
additive and fixes two confirmed dead-code bugs, rather than a rewrite.

## Confirmed gaps found (with evidence)

1. **In-app notifications were dead code.** The `Notification` entity,
   repository, and `GET /notifications` / unread-count / mark-all-read
   endpoints all existed — but nothing anywhere in the codebase ever created
   a `Notification` row. Fully wired on the read side, nothing on the write
   side.
2. **Gallery-published notifications were never sent.** `NotificationService
   .sendGalleryPublished(...)` was fully implemented but never called from
   anywhere — a client whose gallery went live got no WhatsApp/email/in-app
   signal at all.
3. **No real-time channel.** No WebSocket/SSE anywhere in the app — any "live
   updating" UI would have had to poll REST endpoints on a timer.
4. **Suggestion engine could only ever queue for manual review.** The
   engagement/suggestion module (birthday reminders, anniversary shoot
   suggestions, post-delivery review requests, etc.) was well-built but had
   no auto-send option and no way to honor a client's wish not to be
   re-contacted — there was no consent/opt-out field anywhere in the schema.
5. **No cross-sell/upsell feature.** Nothing suggested add-on products or
   package upgrades to a client or to staff during a booking.
6. **No unified search.** Every module already had solid deep pagination +
   filtering, but there was no single "search everything" endpoint for a
   dashboard search bar/typeahead.

Pagination, filtering, and per-module search were **already solid** across
nearly every controller and did not need rework.

## What changed

### 1. Real-time infrastructure (new)
- `common/config/WebSocketConfig.java` — STOMP over WebSocket (+ SockJS
  fallback) at `/ws`, simple broker on `/topic` and `/queue`.
- `common/security/StompAuthChannelInterceptor.java` — authenticates the
  STOMP CONNECT frame using the same JWT already used for REST (the
  handshake HTTP request itself is left public since a browser can't attach
  a bearer header to it; the interceptor rejects any CONNECT without a valid
  token).
- `pom.xml` — added `spring-boot-starter-websocket`.
- `SecurityConfig` — permitted `/ws/**` (handshake only).

### 2. In-app notifications now actually work
- `notification/service/NotificationPublisher.java` (new) — persists a
  `Notification` row per recipient and pushes it live over WebSocket if
  they're connected.
- Wired into: `BookingService` (new booking, cancellation), `PaymentService`
  (payment received), `InventoryService` (crossing into low/out-of-stock,
  de-duplicated so it only fires once per dip, not on every subsequent sale),
  `LeaveService` (leave requested → managers; approved/rejected → the staff
  member), `GalleryService` (gallery published → client, **plus fixed the
  dead `sendGalleryPublished` call**).
- `NotificationController` — added filter by `unreadOnly`/`type`, mark-single
  -read, and delete endpoints (previously only "list all" and "mark all
  read" existed).
- `UserRepository.findByRoleNameIn(...)` and `NotificationRepository`
  additions to support staff fan-out and filtering.

### 3. Suggestion engine: optional auto-send + consent + recurring
- `SuggestionRule` gained `autoSend` (default `false`) and `channel`
  (WHATSAPP/SMS). A rule left at the default still only ever queues for
  admin review, unchanged from before.
- `SuggestionRule` gained `recurring` (default `false`). A non-recurring rule
  fires once per candidate and never again (e.g. "suggest a 1st birthday
  shoot around a newborn booking's first anniversary"). A **recurring** rule
  instead fires every year from then on — the exact "remind me every
  birthday, not just the first" behavior. Each year is tracked as its own
  `SuggestedOutreach` row (`occurrence_number`), so a year already sent
  doesn't block the next one, and the template gained `{occurrenceNumber}`
  and `{occurrenceOrdinal}` ("1st", "2nd", "3rd"...) placeholders for
  messages like `"Happy {occurrenceOrdinal} birthday, {clientName}!"`.
- `OutreachStatus` gained `AUTO_SENT` / `AUTO_SEND_FAILED`.
- `OutreachService.autoSend(...)` — sends immediately and records the
  outcome; failures are captured as a status, not thrown, so one bad send
  doesn't stop a scheduled batch.
- `Booking.marketingOptIn` and `User.marketingOptIn` (both default `true` to
  preserve existing behavior for historical rows) — the scheduler now checks
  this (and, for Studio Sessions/Outdoor Shoots, falls back to the linked
  client account's account-wide preference) before generating or auto
  -sending anything.
- `PUT /users/me/marketing-preference` — lets a client opt out themselves.
- `SuggestionEngineScheduler` rewired to branch on auto-send vs. review-queue
  per candidate, to compute the next due occurrence for recurring rules, and
  now notifies admins with a daily summary of both
  ("N queued for review" / "N auto-sent").

### 4. Recommendation module (new): upsell/cross-sell suggestions
- `category_recommendations` table — admin-curated "for a booking in this
  category, suggest this product" mapping (deliberately not a guessed/ML
  recommendation: invoice line items are free-text, not linked to a product
  id, so there's no real purchase-history data to mine honestly).
- Combined with a **computed** package-upgrade suggestion: other active,
  non-archived packages in the same category priced higher than what the
  booking already has, cheapest upgrade first — this part is derived from
  real price data, not curated.
- `GET /recommendations/booking/{id}`, `GET /recommendations/category/{cat}`,
  plus admin CRUD at `/recommendations/rules` (paginated, filterable by
  category).

### 5. Unified quick search (new)
- `GET /search/quick?q=` — cheap, shallow (5 rows per type) search across
  clients, bookings, galleries, invoices, and products for a dashboard
  search bar/typeahead. Reuses each module's existing search query rather
  than duplicating logic. The existing per-module paginated search endpoints
  remain the right tool for a full listing.

## Database migration
- `V24__engagement_autosend_consent_and_recommendations.sql` — auto-send,
  channel, marketing consent, and the recommendations table.
- `V25__recurring_suggestion_rules.sql` — recurring flag + occurrence
  tracking for the suggestion engine.
- All additive only; every new column has a safe default, nothing existing
  was altered or removed.

## Worked example: yearly birthday reminders from a newborn shoot

A newborn shoot is booked. To get a birthday reminder every year (not just
the 1st), create a rule like:

```
POST /suggestion-rules
{
  "name": "Yearly birthday shoot reminder",
  "category": "NEWBORN",
  "triggerType": "RELATIVE_TO_BOOKING_DATE",
  "offsetDays": -7,
  "recurring": true,
  "autoSend": false,
  "channel": "WHATSAPP",
  "messageTemplate": "Hi {clientName}! Baby's {occurrenceOrdinal} birthday is coming up on {triggerDate} — want to book a birthday shoot to celebrate at {studioName}?"
}
```

- `offsetDays: -7` means each year's message goes out a week before that
  year's anniversary of the shoot date.
- `recurring: true` means this doesn't stop after the 1st birthday — it
  keeps firing every year (2nd, 3rd, 4th birthday...) for as long as the
  booking exists and the rule stays active.
- The 1st year's message renders `{occurrenceOrdinal}` as "1st", the next
  year as "2nd", and so on automatically.
- With `autoSend: false` (the default), each year's message still lands in
  the admin review queue first rather than going out unattended — flip it
  to `true` once you're happy with how the messages read.

The exact same pattern works for any category — a `WEDDING` rule with
`RELATIVE_TO_BOOKING_DATE` + `recurring: true` gives a yearly anniversary
-shoot reminder; a wildcard rule (no category) with `recurring: true` gives
a yearly "just checking in" message for every client, regardless of shoot
type.

## Known limitations / honest scope notes
- Auto-send only supports WHATSAPP and SMS channels (both already have a
  generic "send this text" method in the codebase). EMAIL auto-send was not
  added because the module's free-text templates don't have an email layout
  behind them the way transactional emails (booking confirmation, invoice,
  etc.) do — building that honestly would need a real template, not just
  wiring an existing method.
- Studio Sessions and Outdoor Shoots don't have their own per-record
  marketing-consent flag (only Booking and User do) — they fall back to the
  linked client account's account-wide preference. Extending this to give
  every track its own flag was judged lower value than the rest of this work
  for the size of the change involved.
- I do not have internet access to Maven Central in this environment, so
  this could not be compiled or run here. Every new/changed file was
  reviewed by hand against the actual entity fields, repository methods, and
  existing conventions in the codebase (not assumed from memory) — but a
  `mvn clean verify` on your end before deploying is strongly recommended.
