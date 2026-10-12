package com.cameraman.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

public class ProductDto {
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotBlank private String name;
        private String description;
        private String categoryId;
        private String sku;
        @NotNull private BigDecimal price;
        private BigDecimal gstRate;
        private int stockQuantity;
        private String imageUrl;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        private String name;
        private String description;
        private String categoryId;
        @jakarta.validation.constraints.DecimalMin("0") private BigDecimal price;
        @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") private BigDecimal gstRate;
        @jakarta.validation.constraints.Min(0) private Integer stockQuantity;
        private Boolean active;
        private String imageUrl;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProductResponse {
        private String id;
        private String categoryId;
        private String categoryName;
        private String name;
        private String description;
        private String sku;
        private BigDecimal price;
        private BigDecimal gstRate;
        private int stockQuantity;
        private boolean active;
        private String imageUrl;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ScanRequest {
        /** OUT (default) or IN. */
        private String mode;
        @jakarta.validation.constraints.Min(1) private Integer quantity;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CategoryRequest {
        @NotBlank private String name;
        private String description;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CategoryResponse {
        private String id;
        private String name;
        private String description;
        private boolean active;
    }
}
