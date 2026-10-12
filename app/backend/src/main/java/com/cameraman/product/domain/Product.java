package com.cameraman.product.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(unique = true, length = 50)
    private String sku;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "gst_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRate = new BigDecimal("18.00");

    @Column(name = "stock_quantity")
    @Builder.Default
    private int stockQuantity = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "image_url")
    private String imageUrl;
}
