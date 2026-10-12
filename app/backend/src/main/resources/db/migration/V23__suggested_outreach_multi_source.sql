-- The suggestion engine now evaluates Studio Sessions and Outdoor Shoots, not just the
-- general Bookings table (see SuggestionEngineScheduler) — so suggested_outreach.booking_id
-- can now legitimately hold a studio_sessions.id or outdoor_shoots.id, not only a
-- bookings.id. The old hard foreign key to bookings(id) would reject those with a
-- constraint violation the first time a suggestion got generated for a session or shoot.
--
-- Column is kept named booking_id (rather than renamed) to avoid rippling the change
-- through the Java entity, repository query method, DTOs, and frontend model for a purely
-- cosmetic rename — source_type below covers the clarity need instead: it records which
-- table the id actually refers to.
--
-- Trade-off worth knowing: dropping the FK also drops its ON DELETE CASCADE, so a deleted
-- booking/session/shoot no longer auto-removes its suggested_outreach rows. Acceptable
-- here — the app has no hard-delete flow for any of these three (only status transitions
-- like CANCELLED), so the cascade was never actually exercised in practice.

ALTER TABLE suggested_outreach DROP CONSTRAINT IF EXISTS suggested_outreach_booking_id_fkey;

ALTER TABLE suggested_outreach ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'BOOKING';

COMMENT ON COLUMN suggested_outreach.booking_id IS
  'References bookings.id, studio_sessions.id, or outdoor_shoots.id depending on source_type — no DB-level FK since it can point at three different tables.';
