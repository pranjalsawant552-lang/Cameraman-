-- Leave tracking: lets the attendance monthly report distinguish an approved day off
-- from a genuine, unexplained absence (see LeaveRequest.covers()).

CREATE TABLE leave_requests (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    staff_id VARCHAR(36) NOT NULL REFERENCES staff_profiles(id),
    staff_name VARCHAR(150),
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by VARCHAR(36),
    reviewed_by_name VARCHAR(150),
    reviewed_at TIMESTAMP,
    review_notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36)
);

CREATE INDEX idx_leave_staff_dates ON leave_requests (staff_id, from_date, to_date);
CREATE INDEX idx_leave_status ON leave_requests (status);

-- Attendance work-hours config, used by the monthly report to flag late arrivals and
-- treat the weekly off day as neither present nor absent. Sensible defaults are also
-- hardcoded in AttendanceService/StudioSettingsService, so these rows are optional —
-- present here mainly so they show up for editing rather than only existing implicitly.
INSERT INTO studio_settings (setting_key, setting_value, setting_type, category, description, is_public, is_editable)
VALUES
  ('attendance.shift-start', '09:30', 'STRING', 'ATTENDANCE', 'Expected workday start time (HH:mm) — used to flag late arrivals', FALSE, TRUE),
  ('attendance.grace-minutes', '15', 'NUMBER', 'ATTENDANCE', 'Minutes after shift start before a punch counts as late', FALSE, TRUE),
  ('attendance.weekly-off-day', 'SUNDAY', 'STRING', 'ATTENDANCE', 'Day of week treated as the standing day off (not flagged absent)', FALSE, TRUE)
ON CONFLICT (setting_key) DO NOTHING;
