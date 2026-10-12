-- Per-staff shifts, holidays and the late / half-day / overtime rules, plus the pay figures the
-- payroll export needs. Staff with no schedule row keep following the studio-wide settings.

CREATE TABLE work_shifts (
    id            VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name          VARCHAR(80) NOT NULL UNIQUE,
    start_time    TIME        NOT NULL,
    end_time      TIME        NOT NULL,
    grace_minutes INT         NOT NULL DEFAULT 15,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP,
    created_by    VARCHAR(150),
    updated_by    VARCHAR(150),
    CONSTRAINT chk_work_shift_hours CHECK (end_time > start_time)
);

CREATE TABLE staff_schedules (
    id                     VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    staff_id               VARCHAR(36) NOT NULL UNIQUE REFERENCES staff_profiles(id) ON DELETE CASCADE,
    shift_id               VARCHAR(36) REFERENCES work_shifts(id) ON DELETE SET NULL,
    weekly_off_days        VARCHAR(80),
    monthly_salary         NUMERIC(12,2),
    overtime_rate_per_hour NUMERIC(10,2),
    created_at             TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP,
    created_by             VARCHAR(150),
    updated_by             VARCHAR(150)
);

CREATE TABLE attendance_holidays (
    id           VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    holiday_date DATE         NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP,
    created_by   VARCHAR(150),
    updated_by   VARCHAR(150)
);

INSERT INTO studio_settings (setting_key, setting_value, setting_type, category, description, is_public, is_editable)
VALUES
  ('attendance.shift-end', '18:30', 'STRING', 'ATTENDANCE', 'Expected workday end time (HH:mm); time after this can count as overtime', FALSE, TRUE),
  ('attendance.half-day-minutes', '270', 'NUMBER', 'ATTENDANCE', 'Worked time below this many minutes is a half day (0 turns the rule off)', FALSE, TRUE),
  ('attendance.min-overtime-minutes', '30', 'NUMBER', 'ATTENDANCE', 'Smallest overtime (minutes after shift end) that is counted', FALSE, TRUE),
  ('attendance.lates-per-half-day', '3', 'NUMBER', 'ATTENDANCE', 'Every this many late arrivals in a month cost half a day of pay (0 turns the rule off)', FALSE, TRUE)
ON CONFLICT (setting_key) DO NOTHING;
