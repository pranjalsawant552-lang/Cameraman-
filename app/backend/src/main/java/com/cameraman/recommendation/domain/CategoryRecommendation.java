package com.cameraman.recommendation.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import com.cameraman.package_mgmt.domain.PackageCategory;
import jakarta.persistence.*;
import lombok.*;

/**
 * Admin-configured "for a booking in this category, suggest this product as an
 * add-on" mapping — e.g. WEDDING -> premium leather album, BIRTHDAY -> photo
 * frame set. Deliberately a simple admin-curated list rather than a guessed
 * recommendation from purchase history: the studio has no reliable
 * product-level purchase history to mine (invoice line items are free-text,
 * not linked to a product id), so an honest, transparent, staff-controlled
 * mapping is more useful than a fake "smart" recommendation built on data
 * that doesn't actually exist. See RecommendationService for how this
 * combines with package-upgrade suggestions (which *are* computed, from real
 * package/price data) into one "recommended for this booking" response.
 */
@Entity
@Table(name = "category_recommendations", indexes = {
        @Index(name = "idx_category_recommendation_category", columnList = "category")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_category_recommendation", columnNames = {"category", "product_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CategoryRecommendation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PackageCategory category;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    @Builder.Default
    private int priority = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
