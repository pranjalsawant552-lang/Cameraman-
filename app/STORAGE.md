# Photo storage and uploads

## Where files go

All file access goes through `StorageService`, which routes per kind of file. Nothing else in the code talks to S3 or Drive.

| Kind | Default | Why |
|---|---|---|
| Originals (full-resolution camera files) | **Google Drive** if configured, else S3 | Big, written once, rarely read. This is where the storage cost is. |
| Thumbnails, previews, watermarked previews | S3 | Tiny, read constantly, need fast native signed URLs. |
| Invoice PDFs | S3 | The server re-reads them to email/re-send. |
| CMS images, inventory QR codes, WhatsApp media | S3 (forced) | Must load with no login; a private Drive cannot do that. |

Change routing with `STORAGE_ORIGINALS`, `STORAGE_DERIVATIVES`, `STORAGE_DOCUMENTS` (`s3` | `gdrive` | `auto`). With no Drive credentials everything stays on S3, exactly as before.

A key says where it lives: `gdrive:<fileId>` is Drive, anything else is S3. Photos stored before this change keep working with no data migration. To add another provider, implement `StorageProvider` (`storage/provider`) and register it in `StorageService`.

## Google Drive setup (originals)

1. Create a folder in Drive. **Do not share it.** Its id is the last part of the URL: `GDRIVE_ROOT_FOLDER_ID`.
2. Give ONE credential type (server-side env vars only):
   - **OAuth refresh token** (uses your own Google storage quota): `GDRIVE_OAUTH_CLIENT_ID`, `GDRIVE_OAUTH_CLIENT_SECRET`, `GDRIVE_OAUTH_REFRESH_TOKEN` (scope `https://www.googleapis.com/auth/drive`).
   - **Service account**: `GDRIVE_SERVICE_ACCOUNT_JSON` (or `_PATH`). Service accounts have no personal Drive quota, so use a **Shared Drive** and add the account as a member; use that Shared Drive's folder id.
3. Restart. The log prints `Storage routing: originals=gdrive ...`.

## Upload flow

The browser asks `POST /galleries/{id}/photos/presigned-url` and the server answers with a mode:

- **EXISTS**: same photo already in the gallery (name+size+modified time); nothing is sent.
- **DIRECT** (originals on S3): browser PUTs straight to the bucket, then `confirm-upload`.
- **CHUNKED** (originals on Drive): browser sends 8 MB pieces to `PUT /galleries/photos/{id}/chunk`; the backend relays them to a Drive resumable session. After any failure the client asks `/upload-progress` and continues from what Drive actually has. The final chunk completes the upload server-side.

Also: progress per file, automatic retry with backoff (and waiting while the phone is offline), plain-language errors (e.g. "Google Drive storage is full"), a multipart fallback if the bucket has no CORS rule, and Drive-import dedupe (same Drive file is never imported twice).

Originals are never recompressed. Previews are 1600 px / q0.82, thumbnails 400 px / q0.8 (`IMAGE_*` env vars). The original is streamed to a temp file and decoded once; the thumbnail is made from the preview; a semaphore caps concurrent full-size decodes; the gallery ZIP streams instead of buffering.

## Security

- Drive credentials and the resumable-session URI exist only on the server (never in a response, log or the DB-to-client DTOs).
- Drive files are never shared or made public. Browser access to a Drive-held original is a short-lived HMAC-signed link to this backend (`/api/files/{token}`), issued only after the existing gallery permission checks, expiring with `S3_SIGNED_URL_EXPIRY`. Invalid/expired tokens return 404. This is the same trust model as the S3 presigned URLs already in use: whoever holds a live link can fetch that file until it expires.
- Set `STORAGE_LINK_SECRET` to rotate the signing key independently of `JWT_SECRET`.

## Cost notes

Drive holds the large files; S3 holds only small derivatives. Re-uploading an existing photo is skipped, retries resume instead of restarting, failed processing no longer orphans partial files, and purged originals are deleted from whichever provider holds them.

## Limits to know about

- **HEIC/HEIF** originals are stored fine but cannot get previews on the server (no Java decoder); the photo shows "Processing failed" with that reason. JPEG/PNG/TIFF/WebP work. Many phones already hand JPEG to the browser.
- Drive has API quotas; very large simultaneous uploads from many people may see rate-limit messages and retry.
- Drive-held originals are served through the backend, so large downloads use server bandwidth, unlike S3 presigned downloads.
