package com.cameraman.album.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "albums")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Album extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "booking_id")
    private String bookingId;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_name", length = 120)
    private String clientName;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "album_type", nullable = false)
    private AlbumType albumType;

    @Column(length = 100)
    private String material;

    @Enumerated(EnumType.STRING)
    @Column(name = "cover_type")
    private AlbumCoverType coverType;

    @Column(name = "page_count")
    @Builder.Default
    private int pageCount = 20;

    @Column(name = "included_pages")
    @Builder.Default
    private int includedPages = 20;

    @Column(name = "base_price", precision = 12, scale = 2)
    @Builder.Default
    private java.math.BigDecimal basePrice = java.math.BigDecimal.ZERO;

    @Column(name = "price_per_extra_page", precision = 12, scale = 2)
    @Builder.Default
    private java.math.BigDecimal pricePerExtraPage = java.math.BigDecimal.ZERO;

    @Column(name = "cover_photo_id")
    private String coverPhotoId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AlbumStatus status = AlbumStatus.PENDING;

    @Column(name = "revision_count")
    @Builder.Default
    private int revisionCount = 0;

    @Column(name = "max_revisions")
    @Builder.Default
    private int maxRevisions = 3;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "approved_at")
    private java.time.LocalDateTime approvedAt;

    /** basePrice + (extra pages beyond includedPages) * pricePerExtraPage. Never stale since it's derived, not stored. */
    public java.math.BigDecimal getTotalPrice() {
        java.math.BigDecimal base = basePrice != null ? basePrice : java.math.BigDecimal.ZERO;
        java.math.BigDecimal perPage = pricePerExtraPage != null ? pricePerExtraPage : java.math.BigDecimal.ZERO;
        int extraPages = Math.max(0, pageCount - includedPages);
        return base.add(perPage.multiply(java.math.BigDecimal.valueOf(extraPages)));
    }
}
