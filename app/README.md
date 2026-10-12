# Cameraman Photo Studio — Full Stack

A camera/photo studio management system: Spring Boot backend + React frontend
(staff dashboard + client portal).

```
backend/    Spring Boot 3.5 / Java 21 / PostgreSQL API
frontend/   React 19 / TypeScript / Vite / Tailwind v4 dashboard + client portal
```

**This app has no demo/mock-data mode.** The frontend talks to the real
backend exclusively (`frontend/src/lib/api.ts`) — there is no fallback to
fabricated data, so it can't accidentally show a real user sample records.
See `DEPLOYMENT.md` for the full go-live checklist, including everything
that needs your own credentials (payment gateway, WhatsApp, SMTP, storage).

> **Quick start (Docker + MinIO storage): see [`RUN.md`](RUN.md).**

## Backend

```
cd backend
mvn clean verify        # was not run in the build sandbox this was edited in
                         # (no Maven Central access there) — run this before
                         # deploying, it has not been compiler-verified
mvn spring-boot:run
```

Copy `backend/.env.example` to `.env` and fill in every value before running
against anything but a local dev database — see `DEPLOYMENT.md`.

See `backend/CHANGELOG_ENHANCEMENTS.md` for a full account of what was
audited across earlier stages, and `STAGE_1_REPORT.md` through
`STAGE_8_REPORT.md` / `DELIVERY_SUMMARY.md` for the complete build history.

## Frontend

```
cd frontend
cp .env.example .env    # set VITE_API_BASE_URL to your backend
npm install
npm run build            # or npm run dev for local development
```

See `frontend/README.md` for the full page list and architecture.

## Go-live checklist

See `DEPLOYMENT.md` for the complete list of what needs your own accounts,
credentials, and infrastructure decisions before this is ready for real
users — payment gateway keys, WhatsApp Business API, SMTP/email, object
storage (S3/R2), a production database, domain + TLS, and the backend build
verification above. None of that can be filled in on your behalf; it
requires accounts only you can create.
