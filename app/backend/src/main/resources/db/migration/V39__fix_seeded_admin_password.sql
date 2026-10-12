-- The admin seeded in V2 ships a BCrypt hash that does NOT match its documented password
-- (Admin@2024), so nobody could sign in. Replace it with a valid hash, but ONLY while the
-- broken seed hash is still in place: an admin who has already changed their password is untouched.
-- Also clears any lock caused by failed attempts against the broken hash.
-- DEV DEFAULT: change this password after first login, and do not rely on it in production.
UPDATE users
   SET password = '$2a$10$NC97mWTw38BPsPwMiNH9hOnNuhlgTC1faVaFvHMcw.NDTpeVutXN2',
       status = 'ACTIVE',
       email_verified = TRUE,
       failed_login_attempts = 0,
       locked_until = NULL
 WHERE email = 'admin@cameraman.com'
   AND password = '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCZbk8Y9EjNdHN1sW7GqRWq';
