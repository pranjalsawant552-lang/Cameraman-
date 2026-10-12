# Smoke test (manual)

Run after `docker compose up --build`. Admin: `admin@cameraman.com` / `Admin@2024`.

1. **Register**: `/register` as a new client. You land in the portal.
2. **Book**: `/portal/book`. Pick category, package, a future date, a free time, answer the intake questions, confirm.
   - A blocked date appears struck through.
   - Booking the same slot as a second client fails with "already booked".
3. **Pay advance** (needs Razorpay keys): "Pay advance" on the confirmation card. Without keys the card disappears.
4. **Pipeline**: Admin > Bookings > open the booking > change the stage.
5. **Upload**: Admin > Galleries > new gallery for that booking and client > Upload photos.
6. **Publish**: Publish, then copy the share link or use the WhatsApp button.
7. **Select**: as the client open `/g/<token>`, sign in, heart photos up to the limit, Review and submit.
8. **Approve**: Admin > Photo selections > To approve > Review > Approve. The booking moves to Album designing and the client is notified.
9. **Invoice**: Admin > Invoices > new > open it > Finalize > Send (WhatsApp or Email).
10. **Paid**: Record a manual payment, or pay as the client in Portal > Invoices. Status goes SENT, PARTIALLY_PAID, PAID.
11. **Password**: Login > Forgot password > use the emailed link. Then Settings > Change password.
12. **Attendance punch**: send a punch via the machine or bridge; see it under Admin > Attendance.
13. **Report export**: Admin > Reports > export bookings CSV.

## Added checks (second and third pass)
14. **Roles**: Admin > Roles & permissions: create a role, tick permissions, save. Super admin is locked.
15. **Staff safety**: try to deactivate yourself and the last super admin: both are refused. Deactivate a test staff member: they are logged out and cannot sign in.
16. **Closed day**: Calendar > pick a day > Block. In the client wizard that day is struck through.
17. **Notification switch**: as a client turn off "Gallery ready", publish a gallery: no in-app notification arrives. Payments can't be turned off.
18. **Attendance**: send the same punch twice (CSV import twice): the second shows duplicates, not new rows. A new machine serial appears under "Known machines" as waiting for approval.
19. **Missed punch**: as staff open My schedule > send a correction; as manager approve it under Attendance > Punch corrections: a manual punch appears.
20. **Leave**: apply twice for overlapping dates: the second is refused.
