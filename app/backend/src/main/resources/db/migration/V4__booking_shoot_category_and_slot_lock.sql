-- Add shoot category (casual, wedding, pre-wedding, birthday, etc.) to bookings
ALTER TABLE bookings ADD COLUMN shoot_category VARCHAR(30) NOT NULL DEFAULT 'OTHER';

-- Add CASUAL to package_category usage note: enum values are enforced at the
-- application layer (JPA @Enumerated(STRING)); no DB check constraint needed
-- since PackageCategory.CASUAL was added in the same release.

-- DB-level slot lock: prevent two active (non-cancelled) bookings from ever
-- occupying the exact same date + start_time. This is a safety net in addition
-- to the application-level overlap check in BookingService, protecting against
-- race conditions where two clients submit the same slot at the same instant.
CREATE UNIQUE INDEX idx_booking_slot_lock
    ON bookings (booking_date, start_time)
    WHERE status <> 'CANCELLED';
