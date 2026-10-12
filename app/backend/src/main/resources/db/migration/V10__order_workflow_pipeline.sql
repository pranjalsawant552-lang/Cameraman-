-- Order/workflow pipeline: fine-grained post-confirmation fulfilment tracking
-- (Booking Confirmed -> Advance Paid -> ... -> Delivered), separate from the
-- existing coarse `bookings.status` lifecycle. Also adds editor assignment
-- (previously only `photographer_id` existed) and courier/delivery tracking.

ALTER TABLE bookings
    ADD COLUMN editor_id VARCHAR(36) REFERENCES users(id),
    ADD COLUMN order_stage VARCHAR(30) NOT NULL DEFAULT 'BOOKING_CONFIRMED',
    ADD COLUMN expected_delivery_date DATE,
    ADD COLUMN courier_provider VARCHAR(100),
    ADD COLUMN courier_tracking_number VARCHAR(100),
    ADD COLUMN delivery_signature_key VARCHAR(500),
    ADD COLUMN delivered_at TIMESTAMP;

CREATE INDEX idx_booking_order_stage ON bookings(order_stage);
CREATE INDEX idx_booking_editor ON bookings(editor_id);

CREATE TABLE order_stage_history (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    booking_id VARCHAR(36) NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    stage VARCHAR(30) NOT NULL,
    note TEXT,
    changed_by_name VARCHAR(150),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_stage_history_booking ON order_stage_history(booking_id);

-- Backfill: every existing CONFIRMED-or-later booking gets a starting
-- history row at BOOKING_CONFIRMED so the tracker has something to show.
INSERT INTO order_stage_history (id, booking_id, stage, note, created_at)
SELECT gen_random_uuid()::text, id, 'BOOKING_CONFIRMED', 'Migrated from existing booking', COALESCE(created_at, NOW())
FROM bookings
WHERE status IN ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED');
