-- Upload hardening: duplicate detection, resumable provider uploads and readable failure reasons.
ALTER TABLE photos ADD COLUMN IF NOT EXISTS upload_fingerprint VARCHAR(255);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS upload_session_uri VARCHAR(1000);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(300);

CREATE INDEX IF NOT EXISTS idx_photo_gallery_fingerprint
    ON photos (gallery_id, upload_fingerprint)
    WHERE upload_fingerprint IS NOT NULL;
