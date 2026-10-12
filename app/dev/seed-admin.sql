-- DEV ONLY. Sets a valid BCrypt hash for the seeded admin so you can log in locally.
-- Login: admin@cameraman.com / Admin@2024   (change it after first login; delete this
-- file and the admin-seed service in docker-compose.yml for any real deployment).
UPDATE users
   SET password = '$2a$10$NC97mWTw38BPsPwMiNH9hOnNuhlgTC1faVaFvHMcw.NDTpeVutXN2', status = 'ACTIVE', email_verified = TRUE
 WHERE email = 'admin@cameraman.com';
