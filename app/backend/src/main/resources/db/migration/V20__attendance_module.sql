-- Attendance module: device-agnostic punch log. Every ingestion path (manual entry,
-- CSV import, generic webhook, ZKTeco/ADMS protocol push) writes into this one table —
-- see AttendanceSource for how a given row got here.

CREATE TABLE attendance_records (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    employee_code VARCHAR(64) NOT NULL,
    staff_id VARCHAR(36) REFERENCES staff_profiles(id),
    staff_name VARCHAR(150),
    punch_time TIMESTAMP NOT NULL,
    direction VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    source VARCHAR(20) NOT NULL,
    device_serial VARCHAR(100),
    raw_payload TEXT,
    notes VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36)
);

CREATE INDEX idx_attendance_staff_time ON attendance_records (staff_id, punch_time);
CREATE INDEX idx_attendance_employee_code ON attendance_records (employee_code);
CREATE INDEX idx_attendance_punch_time ON attendance_records (punch_time);

-- staff_profiles.employee_id already exists and is unique, but wasn't indexed for lookup
-- (only relied on for the unique constraint) — attendance resolution looks this up on
-- every single punch, so it needs its own index.
CREATE INDEX idx_staff_profiles_employee_id ON staff_profiles (employee_id);
