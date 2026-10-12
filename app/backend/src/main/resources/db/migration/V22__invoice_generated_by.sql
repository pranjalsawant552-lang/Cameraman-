-- Records who generated each invoice, captured by name at creation time (not just the
-- generic BaseAuditEntity.created_by user ID) so it prints correctly on the bill itself
-- and stays accurate even if that staff member's account is later renamed or removed.
ALTER TABLE invoices ADD COLUMN generated_by_name VARCHAR(150);
ALTER TABLE invoices ADD COLUMN generated_by_user_id VARCHAR(36);
