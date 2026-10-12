-- The photos chosen for an album. Until now an album had a cover_photo_id column and
-- nothing else: there was nowhere to store which photos go into it, in what order.
CREATE TABLE album_photos (
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    album_id    VARCHAR(36) NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    photo_id    VARCHAR(36) NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    position    INT         NOT NULL DEFAULT 0,
    page_number INT,
    note        VARCHAR(300),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP,
    created_by  VARCHAR(150),
    updated_by  VARCHAR(150),
    CONSTRAINT uq_album_photo UNIQUE (album_id, photo_id)
);

CREATE INDEX idx_album_photos_album ON album_photos (album_id, position);
CREATE INDEX idx_album_photos_photo ON album_photos (photo_id);
