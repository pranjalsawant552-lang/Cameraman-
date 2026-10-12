package com.cameraman.billing.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "invoice_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "item_type", length = 50)
    private String itemType; // SERVICE, PRODUCT, ALBUM, PRINT, USB

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    @PostLoad @PostPersist @PostUpdate
    public void calcLineTotal() {
        if (unitPrice != null) {
            BigDecimal gross = unitPrice.multiply(new BigDecimal(quantity));
            BigDecimal pct = discountPercent != null ? discountPercent : BigDecimal.ZERO;
            BigDecimal disc = gross.multiply(pct).divide(new BigDecimal("100"));
            lineTotal = gross.subtract(disc).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }
}
