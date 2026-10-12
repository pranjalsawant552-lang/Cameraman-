-- Lets a custom role see salary figures and download the payroll export.
INSERT INTO permissions (id, module, action, description) VALUES
  (gen_random_uuid()::text, 'ATTENDANCE', 'EXPORT', 'Payroll, salary figures and export')
ON CONFLICT (module, action) DO NOTHING;
