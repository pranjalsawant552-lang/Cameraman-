-- 1) Remove duplicate punches (same employee code, time and device), keeping the earliest row.
DELETE FROM attendance_records a
USING attendance_records b
WHERE a.employee_code = b.employee_code
  AND a.punch_time = b.punch_time
  AND COALESCE(a.device_serial, '') = COALESCE(b.device_serial, '')
  AND a.id > b.id;

-- 2) ...and make sure it can never happen again. COALESCE so punches with no device
--    (manual entries) are also protected; plain NULLs would all count as "different".
CREATE UNIQUE INDEX uq_attendance_punch
    ON attendance_records (employee_code, punch_time, (COALESCE(device_serial, '')));

-- 3) Attendance machines known to the system. Unknown serials are recorded as NOT allowed
--    the first time they call, so an admin can approve them from the UI.
CREATE TABLE attendance_devices (
    serial        VARCHAR(100) PRIMARY KEY,
    label         VARCHAR(100),
    allowed       BOOLEAN      NOT NULL DEFAULT FALSE,
    first_seen_at TIMESTAMP    NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMP    NOT NULL DEFAULT now(),
    punch_count   BIGINT       NOT NULL DEFAULT 0
);
