package com.cameraman.package_mgmt.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "packages", indexes = {
    @Index(name = "idx_package_category", columnList = "category"),
    @Index(name = "idx_package_active", columnList = "active")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Package extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackageCategory category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "discounted_price", precision = 12, scale = 2)
    private BigDecimal discountedPrice;

    @Column(name = "gst_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRate = new BigDecimal("18.00");

    @Column(name = "duration_hours")
    private Integer durationHours;

    @Column(name = "photo_count")
    private Integer photoCount;

    @Column(name = "edited_count")
    private Integer editedCount;

    @Column(name = "print_count")
    private Integer printCount;

    @Column(name = "includes_album")
    @Builder.Default
    private boolean includesAlbum = false;

    @Column(name = "includes_video")
    @Builder.Default
    private boolean includesVideo = false;

    @Column(name = "includes_usb")
    @Builder.Default
    private boolean includesUsb = false;

    @Column(name = "delivery_days")
    private Integer deliveryDays;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "seasonal_offer")
    @Builder.Default
    private boolean seasonalOffer = false;

    @Column(name = "offer_start_date")
    private java.time.LocalDate offerStartDate;

    @Column(name = "offer_end_date")
    private java.time.LocalDate offerEndDate;

    @OneToMany(mappedBy = "pkg", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PackageItem> items = new ArrayList<>();

    public BigDecimal getEffectivePrice() {
        return (discountedPrice != null && discountedPrice.compareTo(BigDecimal.ZERO) > 0)
                ? discountedPrice : basePrice;
    }

    public BigDecimal getPriceWithGst() {
        BigDecimal effective = getEffectivePrice();
        BigDecimal gst = effective.multiply(gstRate).divide(new BigDecimal("100"));
        return effective.add(gst);
    }
}
