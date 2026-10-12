-- ============================================================
-- V8__broadcast_permissions.sql
-- Wires the new bulk WhatsApp broadcast module into the existing
-- granular permission system (role_permissions), same as every other module.
-- ============================================================

INSERT INTO permissions (id, module, action, description) VALUES
  (gen_random_uuid()::text, 'BROADCAST', 'VIEW', 'View broadcast campaigns'),
  (gen_random_uuid()::text, 'BROADCAST', 'SEND', 'Compose and send bulk WhatsApp messages');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER')
  AND p.module = 'BROADCAST';
