-- Indoor/Outdoor classification for bookings, and pickup tracking for ready orders.
ALTER TABLE bookings
    ADD COLUMN location_type VARCHAR(10) NOT NULL DEFAULT 'INDOOR',
    ADD COLUMN ready_for_pickup_at TIMESTAMP,
    ADD COLUMN picked_up_at TIMESTAMP,
    ADD COLUMN pickup_reminder_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_pickup_reminder_at TIMESTAMP;

-- Existing bookings that carry a venue were shot away from the studio.
UPDATE bookings SET location_type = 'OUTDOOR' WHERE venue IS NOT NULL AND btrim(venue) <> '';

CREATE INDEX idx_bookings_location_type ON bookings (location_type);
CREATE INDEX idx_bookings_pickup ON bookings (ready_for_pickup_at, picked_up_at);
