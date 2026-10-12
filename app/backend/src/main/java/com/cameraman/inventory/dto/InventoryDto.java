package com.cameraman.inventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class InventoryDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateItemRequest {
        @NotBlank @Size(max = 200) private String name;
        private String description;
        @NotBlank @Size(max = 50) private String sku;
        private String barcode;
        @NotBlank private String category;
        private String unit;
        @Min(0) private int initialQuantity;
        @Min(0) private int reorderLevel;
        @Min(0) private int reorderQuantity;
        private BigDecimal costPrice;
        private BigDecimal sellingPrice;
        private String location;
        private String notes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateItemRequest {
        private String name;
        private String description;
        private String barcode;
        private String category;
        private String unit;
        private Integer reorderLevel;
        private Integer reorderQuantity;
        private BigDecimal costPrice;
        private BigDecimal sellingPrice;
        private String location;
        private String notes;
        private Boolean active;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StockAdjustmentRequest {
        @NotBlank private String transactionType;
        @Min(1) private int quantity;
        private BigDecimal unitPrice;
        private String referenceId;
        private String referenceType;
        private String notes;
        private String supplier;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ScanRequest {
        /** OUT (default) takes stock out, IN puts it back. */
        private String mode;
        @Min(1) private Integer quantity;
        private String notes;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ItemResponse {
        private String id;
        private String name;
        private String description;
        private String sku;
        private String barcode;
        private String qrCodeUrl;
        private String category;
        private String unit;
        private int quantityOnHand;
        private int reorderLevel;
        private int reorderQuantity;
        private BigDecimal costPrice;
        private BigDecimal sellingPrice;
        private String location;
        private boolean active;
        private boolean lowStock;
        private boolean outOfStock;
        private String notes;
        private LocalDateTime createdAt;
    }
}
