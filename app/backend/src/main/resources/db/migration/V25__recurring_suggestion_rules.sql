-- Recurring suggestions (e.g. "every birthday", not just the first one).
--
-- A non-recurring rule (the default, unchanged behavior) fires once per
-- candidate: anchorDate + offsetDays, and never again for that candidate.
--
-- A recurring rule fires once per year: anchorDate + N years + offsetDays,
-- for N = 1, 2, 3, ... — each year's message is tracked as its own row via
-- occurrence_number, so "already sent for this rule+booking" no longer means
-- "never again", it means "already sent for this specific year".
ALTER TABLE suggestion_rules ADD COLUMN recurring BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE suggested_outreach ADD COLUMN occurrence_number INT NOT NULL DEFAULT 1;
CREATE INDEX idx_suggested_outreach_rule_booking_occurrence
    ON suggested_outreach(rule_id, booking_id, occurrence_number);
