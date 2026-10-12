-- Which clients may see a gallery (beyond its primary client) and which of them take part in the
-- photo-selection process. A can_select viewer gets their own photo_selections row.
CREATE TABLE gallery_viewers (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    gallery_id VARCHAR(36) NOT NULL REFERENCES galleries(id) ON DELETE CASCADE,
    client_id VARCHAR(36) NOT NULL,
    can_select BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150),
    CONSTRAINT uq_gallery_viewer UNIQUE (gallery_id, client_id)
);
CREATE INDEX idx_gallery_viewers_client ON gallery_viewers(client_id);
CREATE INDEX idx_gallery_viewers_gallery ON gallery_viewers(gallery_id);
