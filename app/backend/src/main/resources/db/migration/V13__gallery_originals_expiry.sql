-- Originals storage lifecycle: admin-configurable (and force-triggerable) auto-expiry
-- of full-resolution original photos that weren't selected by the client, so a studio
-- doesn't keep paying to store every unselected shot from every gallery forever. Only
-- the heavy original is ever deleted — thumbnails/previews/watermarked versions (which
-- are what the client actually browses) are untouched, and selected photos' originals
-- are never touched by this at all.

ALTER TABLE galleries
    ADD COLUMN originals_expiry_days INT,
    ADD COLUMN originals_expired_at TIMESTAMP;

ALTER TABLE photos
    ADD COLUMN original_purged BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_gallery_originals_expiry ON galleries(originals_expiry_days, originals_expired_at) WHERE originals_expiry_days IS NOT NULL;
