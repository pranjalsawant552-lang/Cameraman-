package com.cameraman.gallery.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "galleries", indexes = {
    @Index(name = "idx_gallery_booking", columnList = "booking_id"),
    @Index(name = "idx_gallery_token", columnList = "access_token", unique = true),
    @Index(name = "idx_gallery_client", columnList = "client_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Gallery extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "booking_id", length = 36)
    private String bookingId;

    @Column(name = "client_id", length = 36)
    private String clientId;

    @Column(name = "client_name", length = 120)
    private String clientName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private GalleryStatus status = GalleryStatus.DRAFT;

    @Column(name = "access_token", unique = true, length = 100)
    private String accessToken;

    @Column(name = "access_token_expires")
    private LocalDateTime accessTokenExpires;

    @Column(name = "password_protected")
    @Builder.Default
    private boolean passwordProtected = false;

    @Column(name = "gallery_password", length = 100)
    private String galleryPassword;

    @Column(name = "selection_limit")
    private Integer selectionLimit;

    @Column(name = "download_allowed")
    @Builder.Default
    private boolean downloadAllowed = false;

    @Column(name = "watermark_enabled")
    @Builder.Default
    private boolean watermarkEnabled = true;

    @Column(name = "cover_photo_id", length = 36)
    private String coverPhotoId;

    @Column(name = "photo_count")
    @Builder.Default
    private int photoCount = 0;

    @Column(name = "total_size_bytes")
    @Builder.Default
    private long totalSizeBytes = 0L;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // ── Originals storage lifecycle (separate from the gallery link's own expiry above) ──
    // The heavy full-resolution ORIGINAL files are what actually cost money to store —
    // the client only ever browses/selects from the small watermarked preview, which is
    // ~20-40x smaller. Once the client has finished selecting, there's no reason to keep
    // paying to store full-res originals for the photos they *didn't* pick. This is
    // admin-configurable (set a number of days, or leave null to never auto-expire) and
    // can also be force-triggered immediately. Selected photos' originals are never
    // touched by this — see PhotoUploadService.purgeUnselectedOriginals.
    @Column(name = "originals_expiry_days")
    private Integer originalsExpiryDays;

    @Column(name = "originals_expired_at")
    private LocalDateTime originalsExpiredAt;

    @Column(name = "view_count")
    @Builder.Default
    private long viewCount = 0L;

    @OneToMany(mappedBy = "gallery", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Photo> photos = new ArrayList<>();
}
