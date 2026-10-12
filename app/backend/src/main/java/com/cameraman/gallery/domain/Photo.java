package com.cameraman.gallery.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "photos", indexes = {
    @Index(name = "idx_photo_gallery", columnList = "gallery_id"),
    @Index(name = "idx_photo_status", columnList = "status"),
    @Index(name = "idx_photo_s3_key", columnList = "s3_key")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Photo extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gallery_id", nullable = false)
    private Gallery gallery;

    @Column(nullable = false, length = 300)
    private String originalFilename;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "thumbnail_key", length = 500)
    private String thumbnailKey;

    @Column(name = "preview_key", length = 500)
    private String previewKey;

    @Column(name = "watermarked_key", length = 500)
    private String watermarkedKey;

    @Column(name = "file_size_bytes")
    private long fileSizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "mime_type", length = 50)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PhotoStatus status = PhotoStatus.UPLOADED;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "caption", length = 500)
    private String caption;

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "is_favourite")
    @Builder.Default
    private boolean favourite = false;

    @Column(name = "download_count")
    @Builder.Default
    private long downloadCount = 0L;

    @Column(name = "exif_data", columnDefinition = "TEXT")
    private String exifData;

    // True once the full-resolution original has been deleted from storage (either by
    // the scheduled originals-expiry job or an admin's "force expire" action) because
    // the client didn't select this photo. s3Key is left in place as a historical
    // record of where it *was* — don't attempt to download/presign it once this is
    // true, it will 404 against the storage backend.
    // Client-side fingerprint (name|size|lastModified, or drive:<fileId>) used to skip photos that
    // are already in the gallery instead of uploading and storing them twice.
    @Column(name = "upload_fingerprint", length = 255)
    private String uploadFingerprint;

    // Provider resumable-upload session (Google Drive). A bearer secret: server-side only,
    // never put in a DTO. Cleared once the upload completes.
    @Column(name = "upload_session_uri", length = 1000)
    private String uploadSessionUri;

    // Why processing failed, in words a person can act on. Cleared on success.
    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "original_purged")
    @Builder.Default
    private boolean originalPurged = false;
}
