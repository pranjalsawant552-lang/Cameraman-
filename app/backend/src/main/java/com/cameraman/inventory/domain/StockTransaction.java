package com.cameraman.inventory.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "stock_transactions", indexes = {
    @Index(name = "idx_stock_txn_item", columnList = "item_id"),
    @Index(name = "idx_stock_txn_type", columnList = "transaction_type")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockTransaction extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "quantity_before")
    private int quantityBefore;

    @Column(name = "quantity_after")
    private int quantityAfter;

    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "reference_id", length = 36)
    private String referenceId; // invoice_id, purchase_order_id, etc.

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "supplier", length = 200)
    private String supplier;
}
