package com.cameraman.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

public class RecommendationDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RuleRequest {
        @NotBlank private String category;
        @NotBlank private String productId;
        private Integer priority;
        private Boolean active;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RuleResponse {
        private String id;
        private String category;
        private String productId;
        private String productName;
        private BigDecimal productPrice;
        private int priority;
        private boolean active;
    }

    /** One suggested product add-on, shown to staff or on the client portal. */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProductSuggestion {
        private String productId;
        private String productName;
        private BigDecimal price;
        private String imageUrl;
        private int stockQuantity;
    }

    /** One suggested package upgrade — a pricier, active package in the same category
     *  the client hasn't already booked. */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PackageUpgradeSuggestion {
        private String packageId;
        private String packageName;
        private BigDecimal effectivePrice;
        private BigDecimal priceDifference;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BookingRecommendations {
        private String bookingId;
        private String category;
        private java.util.List<ProductSuggestion> productAddOns;
        private java.util.List<PackageUpgradeSuggestion> packageUpgrades;
    }
}
