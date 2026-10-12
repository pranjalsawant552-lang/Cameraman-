# Stage 5 Report — Engagement, WhatsApp Broadcast, Registration, Final Sweep

This closes out everything named as remaining after Stage 4.

## What changed

### Suggestion/Engagement review — already-built UI, now live-wired
`Engagement.tsx` was already a real, functional page (approve/dismiss
outreach, create/edit suggestion rules) — not a stub. Wired its five backing
functions to the real `/outreach` and `/suggestion-rules` endpoints, field
names verified against `EngagementDto.java`. One real design decision:
`saveSuggestionRule` has to pick between `POST` (create) and `PUT` (update)
against a backend that only knows one of those per rule — it does this by
checking whether the rule still carries the `r-<timestamp>` placeholder ID
that `blankRule()` stamps on new, unsaved rules; a real saved rule's ID never
matches that pattern. Documented inline since it's the kind of implicit
contract that's easy to break later without knowing why.

### WhatsApp Broadcast — built from nothing
No page, route, or nav entry existed for this despite the backend having a
complete `BroadcastController` (audience preview, campaign creation with
media upload, campaign history) built on the official WhatsApp Business API
(confirmed back in Stage 1's audit — not an unofficial automation). Built
`pages/admin/Broadcast.tsx`: composer with title/message/optional image,
an audience-size preview pulled from real opted-in client counts, send
action, and a campaign history list with delivery counts. Wired to
`/broadcasts`, `/broadcasts/audience-preview`, `/broadcasts/media`.

### Catalog → Billing integration
The one closed loop from Stage 4's "not built" list: `NewInvoiceModal` can
now add a line item directly from Packages/Products via a dropdown, instead
of every invoice being typed freehand. Auto-fills description and price from
the catalog; staff can still edit the line after adding it.

### Customer self-registration
`pages/auth/Register.tsx` (new) + `useAuthStore.register()`, calling the
real `POST /auth/register`. Confirmed via the DTO that registration
auto-issues a token pair and signs the client in immediately (the same
pattern as most consumer signup flows) — email verification is something to
prompt for in-app afterward, not a blocker before login. Linked from the
client-mode login screen.

### Final sweep — found one more real bug
Grepped the whole frontend for "coming soon"/"(demo)" patterns as a last
check before calling this done. Found `Recommendations.tsx`'s "Add rule"
button was the exact same non-functional pattern as the Staff.tsx bug from
Stage 3 ("Add-on rule creation coming soon in this demo"), and its per-rule
toggle/delete actions were fake too (`push('Updated (demo)')` with no API
call — at least honestly labeled, but still not real). Fixed all three:
built `AddRecommendationRuleModal.tsx` (category + product picker, sourced
from the Stage 4 catalog), and wired toggle/delete to
`PUT`/`DELETE /recommendations/rules/{id}` with optimistic updates that roll
back on failure, matching the pattern used for booking stage changes back in
Stage 2.

### One gap found and deliberately left alone
The client portal's "Pay {amount}" button on an unpaid invoice still shows
`push('Redirecting to payment (demo)')`. This is honestly labeled, not a
lie like the gallery-publish bug from Stage 4 was — and I left it that way
on purpose: wiring a real "Pay" button means integrating an actual payment
gateway (Razorpay/Stripe — the backend has no such integration built at all,
checked before deciding this), handling webhooks for payment confirmation,
and reconciling that against the invoice/payment records. That's a
real infrastructure project with its own credentials and its own testing
surface, not something to fake through the existing invoice endpoints. Flagging
it as the single most consequential remaining gap in the whole system rather
than papering over it with a call to `recordInvoicePayment` that would
silently mark something "paid" with no money having moved.

## What I actually verified
```
npx tsc -b       → 0 errors
npm run build    → clean production build (957 KB main bundle — the
                     code-splitting warning is unchanged from Stage 4;
                     deliberately not addressed, see below)
npx oxlint src/  → 0 errors, 21 warnings; isolated to the files touched
                     this stage — the 2 warnings there are both in
                     Engagement.tsx, which I only rewired at the API layer
                     and did not edit as a component, so they predate this
                     stage
grep sweep       → re-checked the entire frontend for "coming soon"/"TODO"/
                     "(demo)" patterns as a completion check, not just the
                     modules I'd already planned to touch
```

## What remains — stated plainly, not buried
Two things did **not** get done, and I want them named clearly rather than
implied as "polish" that quietly vanished:

1. **Payment gateway integration** (the "Pay" button above) — real
   infrastructure work needing gateway credentials this environment doesn't
   have, and backend work (webhook handlers, reconciliation) that doesn't
   exist yet either.
2. **Bundle code-splitting** — the production JS bundle is 957 KB
   (280 KB gzipped), and Vite's own build output has been warning about this
   since Stage 1. It's a real performance item (spec section 33/34) but a
   cosmetic build warning, not a functional bug, and splitting 24 routes
   into lazy chunks correctly — without breaking the auth-gated routing — is
   its own careful pass I chose not to rush in the same batch as functional
   fixes.

Everything else named across the original 50-section spec that was
realistically buildable without external infrastructure (payment gateway
keys, a live SMTP/WhatsApp account, a production database) has been wired,
verified, or explicitly named as a gap somewhere in these five reports —
not silently dropped.
