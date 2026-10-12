-- Album page-count selector + itemized pricing (spec Module 5). Albums
-- previously had zero pricing fields at all — no way to price one beyond
-- ad-hoc invoice line items. totalPrice is computed on the fly in the
-- entity (basePrice + extra-pages * pricePerExtraPage), not stored, so it
-- can never go stale relative to these inputs.

ALTER TABLE albums
    ADD COLUMN cover_type VARCHAR(30),
    ADD COLUMN page_count INT NOT NULL DEFAULT 20,
    ADD COLUMN included_pages INT NOT NULL DEFAULT 20,
    ADD COLUMN base_price NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN price_per_extra_page NUMERIC(12,2) NOT NULL DEFAULT 0;
