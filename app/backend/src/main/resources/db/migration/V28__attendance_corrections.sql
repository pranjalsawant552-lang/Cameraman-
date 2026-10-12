-- Staff ask for a missed punch to be added; a manager approves or rejects. Approval creates
-- a MANUAL attendance record and keeps a link to it, so the history stays auditable.
CREATE TABLE attendance_corrections (
    id             VARCHAR(36) PRIMARY KEY,
    staff_id       VARCHAR(36) NOT NULL REFERENCES staff_profiles(id),
    staff_name     VARCHAR(150),
    proposed_time  TIMESTAMP   NOT NULL,
    direction      VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    reason         VARCHAR(500) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by    VARCHAR(36),
    reviewed_by_name VARCHAR(150),
    reviewed_at    TIMESTAMP,
    review_notes   VARCHAR(500),
    record_id      VARCHAR(36),
    created_at     TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_attendance_corrections_staff ON attendance_corrections (staff_id, created_at DESC);
CREATE INDEX idx_attendance_corrections_status ON attendance_corrections (status);
