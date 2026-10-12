-- ============================================================
-- V9__contact_inquiries_audit_columns.sql
-- ContactInquiry extends BaseAuditEntity, which maps created_by and
-- updated_by columns (in addition to created_at/updated_at). V6 only
-- created the latter two, causing Hibernate schema validation to fail
-- on startup with "missing column [created_by] in table [contact_inquiries]".
-- ============================================================

ALTER TABLE contact_inquiries ADD COLUMN created_by VARCHAR(150);
ALTER TABLE contact_inquiries ADD COLUMN updated_by VARCHAR(150);
