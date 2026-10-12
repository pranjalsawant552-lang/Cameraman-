# Stage 6 Report — Correction: Tax Support, Invoice Detail Page

## The correction, stated plainly
In Stage 2 and again in the final gap-list, I told you invoices had **no
tax support at all**. That was wrong, and the mistake was mine: I checked
`CreateInvoiceRequest` (the create endpoint's input shape) and stopped there,
without checking the domain model or the response shape. The actual state:

- `Invoice.calculateTotals()` on the backend **does** compute GST
  automatically — 9% CGST + 9% SGST by default (18% total) — on every
  invoice, unconditionally.
- `InvoiceResponse` **does** return the full breakdown:
  `taxableAmount`, `cgstAmount`, `sgstAmount`, `igstAmount`, `totalTax`.
- What's genuinely missing is narrower than "no tax": **the create request
  has no way to override the rate** — no field for marking an invoice
  interstate (IGST) or tax-exempt. Every invoice silently gets the same 18%.

## Why this mattered more than a documentation fix
Because of my earlier wrong conclusion, `NewInvoiceModal`'s live total
preview (built in Stage 2) never added tax — it showed staff a "total" that
was actually just the pre-tax subtotal. In live mode, the invoice the
backend actually created would have been ~18% higher than what staff saw
on screen while building it. That's a real, currency-affecting bug, not a
cosmetic one, and it existed because of my mistake, not a pre-existing
codebase issue. Fixed now:

- `NewInvoiceModal` shows subtotal, discount, an explicit "GST (18%)" line,
  and a total that matches what the backend will actually charge, with a
  note that the rate can't be changed per invoice yet (naming the real
  remaining gap instead of hiding it now that it's understood correctly).
- Demo mode's `createInvoice` fallback now mirrors the same 18% calculation
  the backend does, so demo and live mode no longer quietly disagree with
  each other on math.

## Invoice Detail page — built from nothing, as planned
- **`pages/admin/InvoiceDetail.tsx`** (new) — full line items, the real tax
  breakdown (subtotal, discount, CGST, SGST, IGST where nonzero, total,
  paid, balance due), and a working **Download PDF** button wired to the
  backend's real `GET /billing/invoices/{id}/pdf`.
- Invoice numbers in the list (`Invoices.tsx`) are now links to this page —
  previously there was no way to open an invoice at all after creating it.
- `fetchInvoiceById` and `downloadInvoicePdf` added to `mockApi.ts`.
  `downloadInvoicePdf` in demo mode throws a clear, honest error ("PDF
  generation needs a live backend") rather than faking a PDF, since there's
  no real renderer to fall back to.
- Extended the frontend `Invoice` type with the tax and line-item fields —
  all optional, so nothing that already used the narrower `Invoice` shape
  broke.

## What I actually verified
```
npx tsc -b       → 0 errors
npm run build    → clean production build (964 KB bundle — the pre-existing
                     code-splitting item, unchanged, not addressed here either)
npx oxlint        → 0 errors; isolated the fully-new/heavily-edited files
                     (InvoiceDetail.tsx, types.ts, apiClient.ts,
                     NewInvoiceModal.tsx) — 0 warnings there too. The 2
                     warnings that show up when linting Invoices.tsx are the
                     same pre-existing set-state-in-effect pattern from
                     Stage 2, not new.
```

## Still genuinely missing (the corrected, narrower version)
- No per-invoice tax rate override (IGST for interstate clients, or a
  tax-exempt flag for non-taxable services) — this is now a precise,
  scoped backend feature request rather than "add tax support" from
  scratch, which is a meaningfully smaller piece of work than I'd implied.
- Everything else named as remaining in the previous gap list is unchanged
  by this correction: payment gateway integration, customer email/mobile
  verification, inventory item CRUD, bulk gallery download, code-splitting.

I'm flagging this correction on its own rather than folding it quietly into
a bigger batch, because getting a fact wrong and then building on top of it
without saying so is exactly the kind of thing this whole engagement was
supposed to catch.
