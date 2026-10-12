package com.cameraman.inventory.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "inventory_items", indexes = {
    @Index(name = "idx_inventory_sku", columnList = "sku", unique = true),
    @Index(name = "idx_inventory_category", columnList = "category"),
    @Index(name = "idx_inventory_barcode", columnList = "barcode")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryItem extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(length = 100)
    private String barcode;

    @Column(name = "qr_code_url")
    private String qrCodeUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryCategory category;

    @Column(name = "unit", length = 20)
    @Builder.Default
    private String unit = "PCS";

    @Column(name = "quantity_on_hand")
    @Builder.Default
    private int quantityOnHand = 0;

    @Column(name = "reorder_level")
    @Builder.Default
    private int reorderLevel = 5;

    @Column(name = "reorder_quantity")
    @Builder.Default
    private int reorderQuantity = 10;

    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", precision = 10, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "location", length = 100)
    private String location;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public boolean isLowStock() {
        return quantityOnHand <= reorderLevel;
    }

    public boolean isOutOfStock() {
        return quantityOnHand <= 0;
    }
}
