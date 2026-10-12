-- ============================================================
-- V6__contact_inquiries.sql
-- The public "Contact Us" form posted to /contact, but no such
-- endpoint existed anywhere in the backend — every submission
-- silently 404'd while the frontend told the visitor it succeeded.
-- This adds real storage so inquiries are captured and staff can
-- see/action them.
-- ============================================================

CREATE TABLE contact_inquiries (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    mobile VARCHAR(20),
    message VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX idx_contact_inquiries_status ON contact_inquiries(status);
CREATE INDEX idx_contact_inquiries_created ON contact_inquiries(created_at DESC);
