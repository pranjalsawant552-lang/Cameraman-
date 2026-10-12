-- Reply tracking for contact inquiries: every reply (emailed from here, or a phone / WhatsApp
-- follow-up logged by staff) is kept, and the inquiry carries a summary for the list view.

CREATE TABLE contact_inquiry_replies (
    id              VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    inquiry_id      VARCHAR(36) NOT NULL REFERENCES contact_inquiries(id) ON DELETE CASCADE,
    channel         VARCHAR(20) NOT NULL,
    message         VARCHAR(4000) NOT NULL,
    emailed         BOOLEAN NOT NULL DEFAULT FALSE,
    replied_by_id   VARCHAR(36),
    replied_by_name VARCHAR(150),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_contact_inquiry_replies_inquiry ON contact_inquiry_replies(inquiry_id, created_at);

ALTER TABLE contact_inquiries ADD COLUMN reply_count INT NOT NULL DEFAULT 0;
ALTER TABLE contact_inquiries ADD COLUMN last_replied_at TIMESTAMP;
ALTER TABLE contact_inquiries ADD COLUMN last_replied_by VARCHAR(150);

-- Inquiries already marked CONTACTED or CLOSED were handled before replies were tracked.
