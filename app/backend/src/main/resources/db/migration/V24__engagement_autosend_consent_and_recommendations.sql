-- ============================================================
-- 1) Suggestion engine: optional auto-send (instead of always
--    landing in the admin review queue) + which channel to use.
-- ============================================================
ALTER TABLE suggestion_rules ADD COLUMN auto_send BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE suggestion_rules ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP';

ALTER TABLE suggested_outreach ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP';
-- Tracks whether this row was sent by the engine itself (auto_send rule) vs by
-- an admin approving/editing it from the review queue. status can now also be
-- 'AUTO_SENT' or 'AUTO_SEND_FAILED' (stored as plain VARCHAR, same as before).

-- ============================================================
-- 2) Marketing/re-engagement consent. Previously nothing tracked whether a
--    client actually wants to be re-contacted for reminders/suggestions —
--    the engine would happily message anyone with a mobile number on file.
--    Defaults to TRUE (opt-out model) to match existing behaviour for
--    historical rows, but is now explicitly captured going forward and can
--    be flipped off per-client.
-- ============================================================
ALTER TABLE bookings ADD COLUMN marketing_opt_in BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN marketing_opt_in BOOLEAN NOT NULL DEFAULT TRUE;

-- ============================================================
-- 3) Product/add-on upsell recommendations. Admin-configurable mapping of
--    "for a booking in category X, suggest these products" (e.g. albums,
--    frames, prints), surfaced to staff at booking/consult time and to the
--    client portal as "recommended add-ons for your shoot".
-- ============================================================
CREATE TABLE category_recommendations (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    category VARCHAR(30) NOT NULL,
    product_id VARCHAR(36) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    priority INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150),
    CONSTRAINT uq_category_recommendation UNIQUE (category, product_id)
);
CREATE INDEX idx_category_recommendation_category ON category_recommendations(category);
