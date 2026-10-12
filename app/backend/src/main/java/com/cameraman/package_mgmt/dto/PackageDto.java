package com.cameraman.package_mgmt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PackageDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotBlank private String name;
        private String description;
        @NotBlank private String category;
        @NotNull private BigDecimal basePrice;
        private BigDecimal discountedPrice;
        private BigDecimal gstRate;
        private Integer durationHours;
        private Integer photoCount;
        private Integer editedCount;
        private Integer printCount;
        private boolean includesAlbum;
        private boolean includesVideo;
        private boolean includesUsb;
        private Integer deliveryDays;
        private int sortOrder;
        private String thumbnailUrl;
        private List<PackageItemRequest> items;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        private String name;
        private String description;
        private BigDecimal basePrice;
        private BigDecimal discountedPrice;
        private Boolean active;
        private Integer sortOrder;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PackageItemRequest {
        @NotBlank private String description;
        @Min(1) private int quantity;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PackageResponse {
        private String id;
        private String name;
        private String description;
        private String category;
        private BigDecimal basePrice;
        private BigDecimal discountedPrice;
        private BigDecimal effectivePrice;
        private BigDecimal priceWithGst;
        private BigDecimal gstRate;
        private Integer durationHours;
        private Integer photoCount;
        private Integer editedCount;
        private Integer printCount;
        private boolean includesAlbum;
        private boolean includesVideo;
        private boolean includesUsb;
        private Integer deliveryDays;
        private boolean active;
        private boolean archived;
        private int sortOrder;
        private String thumbnailUrl;
        private List<PackageItemResponse> items;
        private LocalDateTime createdAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PackageItemResponse {
        private String id;
        private String description;
        private int quantity;
    }
}
