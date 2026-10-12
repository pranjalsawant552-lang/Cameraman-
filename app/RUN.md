# How to run — Cameraman Photo Studio (with MinIO storage)

Storage now works out of the box with **MinIO** (a free, self-hosted S3 clone).
When you get AWS S3 / iDrive e2 keys you change a few env vars — no code changes.

## Option A — everything in Docker (recommended)

Needs: Docker Desktop (or Docker Engine + Compose v2). Nothing else.

```bash
cd app
cp .env.example .env          # optional; defaults already work
docker compose up --build
```

First build takes a few minutes (Maven + npm downloads). Wait until you see
`Admin login ready` in the output, then open:

| What | URL |
|---|---|
| Website / admin / client portal | http://localhost:3000 |
| API | http://localhost:8080/api |
| Swagger (API docs) | http://localhost:8080/api/swagger-ui.html |
| MinIO console (see uploaded photos) | http://localhost:9001 — `minioadmin` / `minioadmin123` |

**Admin login:** `admin@cameraman.com` / `Admin@2024` (dev-only seed, see note below).

**Client sign-up / OTP:** no SMS/email is configured, so verification codes are
printed in the backend log: `docker compose logs -f backend` (look for the OTP).

Stop: `Ctrl+C`, or `docker compose down`.
Wipe all data (DB + photos): `docker compose down -v`.

## Option B — run backend/frontend directly on your machine

Needs: Java 21, Maven 3.9+, Node 20+, Docker (for Postgres/Redis/MinIO only).

```bash
# 1) infrastructure
cd backend
docker compose -f docker-compose.demo.yml up -d

# 2) backend  (default profile "dev" already points at local MinIO)
mvn spring-boot:run                # or: SPRING_PROFILE=local mvn spring-boot:run

# 3) once the backend has started (migrations done), enable the admin login
docker exec -i cameraman-demo-postgres psql -U cameraman -d cameraman_db < ../dev/seed-admin.sql

# 4) frontend (new terminal)
cd ../frontend
cp .env.example .env
npm install
npm run dev                        # http://localhost:5173
```


## Connecting biometric attendance machines

The app is brand-neutral: every method ends in the same attendance table, matched to staff by
**Employee ID** (set each staff member's Employee ID = their user ID on the machine).

| Machine capability | What to do |
|---|---|
| Has **Cloud Server / ADMS** setting (ZKTeco, eSSL, Realtime, Mantra, most new Indian models) | Machine menu → Comm → Cloud Server: Server address = your server's IP/domain, Port = **3000** (Docker stack; nginx forwards `/iclock/*` to the API). Put the machine's serial (menu → Device Info) in `.env`: `ATTENDANCE_ALLOWED_SERIALS=SN1,SN2` and restart the backend. |
| On the LAN but **no push setting** (ZK protocol, port 4370) | Run `tools/attendance-bridge/bridge.py` on an always-on studio PC (setup in the file header). |
| **Other brands** (Hikvision, Matrix, Suprema, cloud-only devices…) | Anything that can send an HTTP POST can use `POST /api/webhooks/attendance/generic` with `{key, employeeCode, timestamp, deviceSerial}`. Needs a small adapter per brand. |
| Nothing network-capable | Export logs to USB → Attendance page → Import CSV. |

`ATTENDANCE_ALLOWED_SERIALS=*` accepts any machine (trusted LAN only). The shared key from the
Attendance settings screen still works for scripts and the bridge.
Known limits: ZK punches are stored with direction "unknown"; the production nginx
(`backend/nginx`) has no `/iclock` rule yet, and most machines can't do HTTPS.

## Using MinIO now → S3 / iDrive e2 later

Everything is controlled by the `S3_*` variables (see `.env.example`).

| Variable | MinIO (now) | AWS S3 | iDrive e2 |
|---|---|---|---|
| `S3_ENDPOINT` | `http://minio:9000` | `https://s3.ap-south-1.amazonaws.com` | endpoint from e2 console |
| `S3_PRESIGN_ENDPOINT` | `http://localhost:9000` | *(blank)* | *(blank)* |
| `S3_PUBLIC_BASE_URL` | `http://localhost:9000/cameraman-photos` | CloudFront / blank | public URL / blank |
| `S3_REGION` | `us-east-1` | `ap-south-1` | region from console |
| `S3_PATH_STYLE` | `true` | `false` | `true` |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | minioadmin… | IAM keys | e2 keys |

**Switch steps**
1. Create the bucket on S3/e2 (private).
2. Edit `.env`: comment out the MinIO block, enable the AWS or e2 block, fill your keys.
3. Copy existing photos across (the database stores only object keys, so paths stay valid):
   ```bash
   docker run --rm --network host minio/mc:RELEASE.2025-04-16T18-13-26Z sh -c "
     mc alias set old http://localhost:9000 minioadmin minioadmin123 &&
     mc alias set new <NEW_ENDPOINT> <NEW_ACCESS_KEY> <NEW_SECRET_KEY> &&
     mc mirror old/cameraman-photos new/<NEW_BUCKET>"
   ```
4. `docker compose up -d --build backend` — done. (You can delete the `minio` and `minio-init` services afterwards.)
5. Set bucket CORS on S3/e2 allowing your site origin with `GET, PUT, HEAD`, because the browser uploads photos straight to storage with signed URLs.

## What I changed (and why)

- **`StorageService.java`** — the signed-URL generator did not use path-style addressing, so
  with MinIO it produced `bucket.localhost:9000` URLs that don't work. Fixed. Also added
  `S3_PRESIGN_ENDPOINT` (the address *the browser* uses, needed for MinIO-in-Docker) and
  `S3_PATH_STYLE`. With real S3/e2 both behave exactly as before.
- **`application.yml` / `application-dev.yml` / `application-local.yml`** — new settings; the
  `dev` profile now defaults to local MinIO instead of crashing with blank S3 keys; local
  base URL fixed to the Vite port (5173).
- **`docker-compose.yml` (new, at `app/`)** — Postgres, Redis, MinIO (+ bucket creation),
  backend, frontend in one command. MinIO images are pinned (the `latest` tag is no longer maintained).
- **`frontend/Dockerfile` + `nginx.conf` (new)** — production build of the React app.
- **Admin login** — the seeded admin's password hash in migration `V2` doesn't match any
  documented password, so nobody could log in. `dev/seed-admin.sql` (run automatically by
  Compose) sets a valid hash for `Admin@2024`. **Dev only** — change the password, and drop
  the `admin-seed` service, before real use.
- `backend/docker-compose.yml` (production) and `backend/.env.example` pass the two new variables through.

## Honest status / things to know

- I built the **frontend** here (`npm ci` + `npm run build` succeed). I could **not** compile the
  backend or run Docker in my environment (no Maven Central / Docker access), so the first
  `docker compose up --build` is the real test. The Java change is small (≈15 lines, SDK-standard calls).
  If the build prints an error, paste it to me and I'll fix it.
- Razorpay, WhatsApp, email, Google Drive import need your own keys; they just show "not configured" until set.
- MinIO's open-source project is in maintenance mode. It's fine for development; for production use S3/e2 as planned.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Photos upload but thumbnails don't load | `S3_PRESIGN_ENDPOINT` must be an address your **browser** can open (`http://localhost:9000`). |
| `accessKey cannot be blank` on start | S3 keys missing — use `.env` or the `dev`/`local` profile. |
| Browser CORS error | Add your site's origin to `CORS_ALLOWED_ORIGINS` (compose: `backend` service). |
| Port already in use | Stop what's using 3000/8080/9000/9001/5432/6379 or change the left side of the `ports:` mapping. |
| Start fresh | `docker compose down -v && docker compose up --build` |

## New in the completion pass

- **Client booking**: `/portal/book`. Price, type and advance are computed on the server; the client cannot set them.
- **Photo selection**: share link `/g/<token>` (sign-in required). Admin page `/admin/selections`.
- **Account**: `/forgot-password`, `/reset-password?token=...`; Settings has profile, consent, password and "log out of all devices".
- **Invoices**: Finalize, Send, Record payment and Cancel on the invoice page. Cancelling needs zero payments (refund first).
- Reset tokens are now stored hashed; links issued before this change stop working (they expire in an hour anyway).
- No new Flyway migrations. `FAMILY` and `PRODUCT` were added to `PackageCategory` (stored as text).
- See `SMOKE_TEST.md` and `COMPLETION_REPORT.md`.
