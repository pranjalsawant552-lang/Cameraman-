-- Studio-wide date blocking (holidays, festivals, planned closures). Checked
-- by both the availability endpoint (shows all slots closed) and booking
-- creation/reschedule (rejects outright), so a blocked date is enforced
-- everywhere a booking could be created, not just hidden in the UI.

CREATE TABLE blocked_dates (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    blocked_date DATE NOT NULL,
    reason VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150),
    CONSTRAINT uq_blocked_date UNIQUE (blocked_date)
);

CREATE INDEX idx_blocked_dates_date ON blocked_dates(blocked_date);
