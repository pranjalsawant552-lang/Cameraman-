-- Tracks whether the "you haven't picked your photos yet" WhatsApp reminder has
-- already been sent for this selection, so the scheduled job doesn't re-send it
-- every day a client stays overdue.

ALTER TABLE photo_selections
    ADD COLUMN reminder_sent_at TIMESTAMP;
