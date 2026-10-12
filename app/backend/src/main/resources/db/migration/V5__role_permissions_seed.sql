-- ============================================================
-- V5__role_permissions_seed.sql
-- The role_permissions join table existed since V1 and permissions
-- were seeded in V2, but no role was ever actually granted any
-- permission — every role had zero rows in role_permissions. Route
-- authorization (@PreAuthorize hasAnyRole(...)) doesn't depend on this
-- table so menus/APIs still worked, but the Roles admin screen and any
-- future permission-level checks were silently non-functional. This
-- fills in sensible default grants per role.
-- ============================================================

-- SUPER_ADMIN and ADMIN: full access to every permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN');

-- MANAGER: operational access, excluding user/role administration and audit.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'MANAGER'
  AND p.module IN ('BOOKING','GALLERY','INVOICE','PAYMENT','PACKAGE','INVENTORY','REPORT','SETTINGS')
  AND NOT (p.module = 'SETTINGS' AND p.action = 'UPDATE');

-- RECEPTIONIST: front-desk booking, invoicing and payments.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'RECEPTIONIST'
  AND ((p.module = 'BOOKING' AND p.action IN ('VIEW','CREATE','UPDATE','APPROVE'))
    OR (p.module = 'INVOICE' AND p.action IN ('VIEW','CREATE','PRINT'))
    OR (p.module = 'PAYMENT' AND p.action IN ('VIEW','CREATE'))
    OR (p.module = 'PACKAGE' AND p.action = 'VIEW'));

-- PHOTOGRAPHER: their own bookings and gallery uploads.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'PHOTOGRAPHER'
  AND ((p.module = 'BOOKING' AND p.action IN ('VIEW','UPDATE'))
    OR (p.module = 'GALLERY' AND p.action IN ('VIEW','UPLOAD')));

-- EDITOR: full gallery management for post-processing/delivery.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'EDITOR'
  AND p.module = 'GALLERY';

-- ACCOUNTANT: invoicing, payments and financial reports.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ACCOUNTANT'
  AND (p.module IN ('INVOICE','PAYMENT')
    OR (p.module = 'REPORT' AND p.action IN ('VIEW','EXPORT')));

-- INVENTORY_MANAGER: stock management and inventory reports.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'INVENTORY_MANAGER'
  AND (p.module = 'INVENTORY'
    OR (p.module = 'REPORT' AND p.action = 'VIEW'));

-- CLIENT: read-only on their own bookings, galleries and invoices
-- (ownership is additionally enforced at the service layer).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'CLIENT'
  AND ((p.module = 'BOOKING' AND p.action IN ('VIEW','CREATE'))
    OR (p.module = 'GALLERY' AND p.action IN ('VIEW','DOWNLOAD'))
    OR (p.module = 'INVOICE' AND p.action = 'VIEW'));
