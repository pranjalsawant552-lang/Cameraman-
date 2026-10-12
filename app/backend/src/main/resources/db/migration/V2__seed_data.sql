-- ============================================================
-- V2__seed_data.sql
-- Initial roles, permissions, and studio settings
-- ============================================================

-- ============================================================
-- DEFAULT ROLES
-- ============================================================
INSERT INTO roles (id, name, description) VALUES
  (gen_random_uuid()::text, 'SUPER_ADMIN', 'Super Administrator with all access'),
  (gen_random_uuid()::text, 'ADMIN', 'Studio Administrator'),
  (gen_random_uuid()::text, 'MANAGER', 'Studio Manager'),
  (gen_random_uuid()::text, 'RECEPTIONIST', 'Front desk receptionist'),
  (gen_random_uuid()::text, 'PHOTOGRAPHER', 'Photographer staff'),
  (gen_random_uuid()::text, 'EDITOR', 'Photo editor staff'),
  (gen_random_uuid()::text, 'ACCOUNTANT', 'Accounting staff'),
  (gen_random_uuid()::text, 'INVENTORY_MANAGER', 'Inventory management'),
  (gen_random_uuid()::text, 'CLIENT', 'Studio client / customer');

-- ============================================================
-- DEFAULT PERMISSIONS
-- ============================================================
INSERT INTO permissions (id, module, action, description) VALUES
  -- Booking permissions
  (gen_random_uuid()::text, 'BOOKING', 'VIEW', 'View bookings'),
  (gen_random_uuid()::text, 'BOOKING', 'CREATE', 'Create bookings'),
  (gen_random_uuid()::text, 'BOOKING', 'UPDATE', 'Update bookings'),
  (gen_random_uuid()::text, 'BOOKING', 'DELETE', 'Delete bookings'),
  (gen_random_uuid()::text, 'BOOKING', 'APPROVE', 'Approve bookings'),
  (gen_random_uuid()::text, 'BOOKING', 'EXPORT', 'Export bookings'),
  -- Gallery permissions
  (gen_random_uuid()::text, 'GALLERY', 'VIEW', 'View galleries'),
  (gen_random_uuid()::text, 'GALLERY', 'CREATE', 'Create galleries'),
  (gen_random_uuid()::text, 'GALLERY', 'UPDATE', 'Update galleries'),
  (gen_random_uuid()::text, 'GALLERY', 'DELETE', 'Delete galleries'),
  (gen_random_uuid()::text, 'GALLERY', 'UPLOAD', 'Upload photos'),
  (gen_random_uuid()::text, 'GALLERY', 'DOWNLOAD', 'Download photos'),
  -- Invoice permissions
  (gen_random_uuid()::text, 'INVOICE', 'VIEW', 'View invoices'),
  (gen_random_uuid()::text, 'INVOICE', 'CREATE', 'Create invoices'),
  (gen_random_uuid()::text, 'INVOICE', 'UPDATE', 'Update invoices'),
  (gen_random_uuid()::text, 'INVOICE', 'DELETE', 'Delete invoices'),
  (gen_random_uuid()::text, 'INVOICE', 'EXPORT', 'Export invoices'),
  (gen_random_uuid()::text, 'INVOICE', 'PRINT', 'Print invoices'),
  -- Payment permissions
  (gen_random_uuid()::text, 'PAYMENT', 'VIEW', 'View payments'),
  (gen_random_uuid()::text, 'PAYMENT', 'CREATE', 'Record payments'),
  (gen_random_uuid()::text, 'PAYMENT', 'REFUND', 'Process refunds'),
  -- User permissions
  (gen_random_uuid()::text, 'USER', 'VIEW', 'View users'),
  (gen_random_uuid()::text, 'USER', 'CREATE', 'Create users'),
  (gen_random_uuid()::text, 'USER', 'UPDATE', 'Update users'),
  (gen_random_uuid()::text, 'USER', 'DELETE', 'Delete users'),
  (gen_random_uuid()::text, 'USER', 'INACTIVE', 'Deactivate users'),
  -- Package permissions
  (gen_random_uuid()::text, 'PACKAGE', 'VIEW', 'View packages'),
  (gen_random_uuid()::text, 'PACKAGE', 'CREATE', 'Create packages'),
  (gen_random_uuid()::text, 'PACKAGE', 'UPDATE', 'Update packages'),
  (gen_random_uuid()::text, 'PACKAGE', 'DELETE', 'Delete packages'),
  -- Inventory permissions
  (gen_random_uuid()::text, 'INVENTORY', 'VIEW', 'View inventory'),
  (gen_random_uuid()::text, 'INVENTORY', 'CREATE', 'Add inventory'),
  (gen_random_uuid()::text, 'INVENTORY', 'UPDATE', 'Update inventory'),
  (gen_random_uuid()::text, 'INVENTORY', 'ADJUST', 'Adjust stock'),
  -- Report permissions
  (gen_random_uuid()::text, 'REPORT', 'VIEW', 'View reports'),
  (gen_random_uuid()::text, 'REPORT', 'EXPORT', 'Export reports'),
  -- Settings permissions
  (gen_random_uuid()::text, 'SETTINGS', 'VIEW', 'View settings'),
  (gen_random_uuid()::text, 'SETTINGS', 'UPDATE', 'Update settings'),
  -- Audit permissions
  (gen_random_uuid()::text, 'AUDIT', 'VIEW', 'View audit logs');

-- ============================================================
-- DEFAULT SUPER_ADMIN USER
-- Password: Admin@2024 (BCrypt encoded)
-- ============================================================
INSERT INTO users (id, email, mobile, password, first_name, last_name, status, email_verified)
VALUES (
  gen_random_uuid()::text,
  'admin@cameraman.com',
  '9999999999',
  '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCZbk8Y9EjNdHN1sW7GqRWq',
  'Studio',
  'Admin',
  'ACTIVE',
  TRUE
);

-- Assign SUPER_ADMIN role
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@cameraman.com' AND r.name = 'SUPER_ADMIN';

-- ============================================================
-- DEFAULT STUDIO SETTINGS
-- ============================================================
INSERT INTO studio_settings (setting_key, setting_value, setting_type, category, description, is_public, is_editable) VALUES
  ('studio.name', 'Cameraman Photo Studio', 'STRING', 'BRANDING', 'Studio Name', TRUE, TRUE),
  ('studio.logo.url', '', 'STRING', 'BRANDING', 'Studio Logo URL', TRUE, TRUE),
  ('studio.watermark.text', 'Cameraman Photo Studio', 'STRING', 'BRANDING', 'Watermark text on photos', FALSE, TRUE),
  ('studio.watermark.logo.url', '', 'STRING', 'BRANDING', 'Watermark logo URL', FALSE, TRUE),
  ('studio.gst.number', '', 'STRING', 'BILLING', 'GST Registration Number', FALSE, TRUE),
  ('studio.address', '', 'STRING', 'CONTACT', 'Studio address', TRUE, TRUE),
  ('studio.phone', '', 'STRING', 'CONTACT', 'Studio phone number', TRUE, TRUE),
  ('studio.email', '', 'STRING', 'CONTACT', 'Studio email address', TRUE, TRUE),
  ('studio.whatsapp.number', '', 'STRING', 'CONTACT', 'WhatsApp business number', TRUE, TRUE),
  ('studio.bank.name', '', 'STRING', 'BILLING', 'Bank name', FALSE, TRUE),
  ('studio.bank.account', '', 'STRING', 'BILLING', 'Bank account number', FALSE, TRUE),
  ('studio.bank.ifsc', '', 'STRING', 'BILLING', 'Bank IFSC code', FALSE, TRUE),
  ('studio.upi.id', '', 'STRING', 'BILLING', 'UPI ID for payments', FALSE, TRUE),
  ('theme.primary.color', '#1a73e8', 'STRING', 'THEME', 'Primary theme color', TRUE, TRUE),
  ('theme.secondary.color', '#34a853', 'STRING', 'THEME', 'Secondary theme color', TRUE, TRUE),
  ('theme.accent.color', '#fbbc04', 'STRING', 'THEME', 'Accent theme color', TRUE, TRUE),
  ('invoice.terms.conditions', 'Payment is due within 30 days. All sales are final.', 'STRING', 'BILLING', 'Default invoice terms', FALSE, TRUE),
  ('booking.advance.percent', '25', 'NUMBER', 'BOOKING', 'Default advance payment percentage', FALSE, TRUE),
  ('gallery.default.expiry.days', '30', 'NUMBER', 'GALLERY', 'Default gallery access expiry in days', FALSE, TRUE),
  ('selection.default.expiry.days', '7', 'NUMBER', 'SELECTION', 'Default selection expiry in days', FALSE, TRUE);

-- ============================================================
-- DEFAULT PACKAGES
-- ============================================================
INSERT INTO packages (id, name, description, category, base_price, gst_rate, duration_hours, photo_count, edited_count, sort_order) VALUES
  (gen_random_uuid()::text, 'Basic Wedding Package', 'Perfect for intimate weddings', 'WEDDING', 25000.00, 18.00, 8, 500, 100, 1),
  (gen_random_uuid()::text, 'Premium Wedding Package', 'Complete wedding coverage with album', 'WEDDING', 50000.00, 18.00, 12, 1000, 200, 2),
  (gen_random_uuid()::text, 'Pre-Wedding Shoot', 'Romantic pre-wedding photography', 'PRE_WEDDING', 15000.00, 18.00, 4, 200, 50, 1),
  (gen_random_uuid()::text, 'Birthday Package', 'Fun birthday photography', 'BIRTHDAY', 8000.00, 18.00, 3, 150, 30, 1),
  (gen_random_uuid()::text, 'Maternity Package', 'Beautiful maternity memories', 'MATERNITY', 12000.00, 18.00, 2, 100, 30, 1),
  (gen_random_uuid()::text, 'Corporate Event Package', 'Professional corporate photography', 'CORPORATE', 20000.00, 18.00, 6, 300, 60, 1);

-- ============================================================
-- DEFAULT CMS PAGES
-- ============================================================
INSERT INTO cms_pages (id, page_key, title, is_published) VALUES
  (gen_random_uuid()::text, 'home', 'Home', TRUE),
  (gen_random_uuid()::text, 'about', 'About Us', TRUE),
  (gen_random_uuid()::text, 'contact', 'Contact Us', TRUE),
  (gen_random_uuid()::text, 'services', 'Our Services', TRUE);

-- ============================================================
-- DEFAULT PRODUCT CATEGORIES
-- ============================================================
INSERT INTO product_categories (id, name) VALUES
  (gen_random_uuid()::text, 'Albums'),
  (gen_random_uuid()::text, 'Frames'),
  (gen_random_uuid()::text, 'Canvas Prints'),
  (gen_random_uuid()::text, 'USB Drives'),
  (gen_random_uuid()::text, 'Photo Gifts'),
  (gen_random_uuid()::text, 'Accessories');
