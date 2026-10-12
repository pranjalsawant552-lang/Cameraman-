-- Idle (inactivity) logout window for the admin app, editable from Settings.
-- 0 disables the idle logout. Applies to every signed-in staff user.
INSERT INTO studio_settings (setting_key, setting_value, setting_type, category, description, is_public, is_editable)
VALUES ('session.idle-timeout-minutes', '30', 'NUMBER', 'SECURITY',
        'Minutes of inactivity before a user is signed out automatically (0 = never)', FALSE, TRUE)
ON CONFLICT (setting_key) DO NOTHING;
