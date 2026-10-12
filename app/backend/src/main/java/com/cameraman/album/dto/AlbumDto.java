package com.cameraman.album.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class AlbumDto {
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        private String bookingId;
        private String clientId;
        private String clientName;
        @NotBlank private String name;
        @NotBlank private String albumType;
        private String material;
        private String coverType;
        private Integer pageCount;
        private Integer includedPages;
        private java.math.BigDecimal basePrice;
        private java.math.BigDecimal pricePerExtraPage;
        private String notes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        @NotBlank private String name;
        @NotBlank private String albumType;
        private String material;
        private String coverType;
        private Integer pageCount;
        private Integer includedPages;
        private java.math.BigDecimal basePrice;
        private java.math.BigDecimal pricePerExtraPage;
        private String notes;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AlbumResponse {
        private String id;
        private String bookingId;
        private String clientId;
        private String clientName;
        private String name;
        private String albumType;
        private String material;
        private String coverType;
        private int pageCount;
        private int includedPages;
        private java.math.BigDecimal basePrice;
        private java.math.BigDecimal pricePerExtraPage;
        private java.math.BigDecimal totalPrice;
        private String status;
        private int revisionCount;
        private int maxRevisions;
        private String notes;
        private LocalDateTime approvedAt;
        private LocalDateTime createdAt;
        private String coverPhotoId;
        private long photoCount;
    }

    /** Replaces the album's whole ordered photo set. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SetPhotosRequest {
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.Size(max = 2000)
        private List<String> photoIds;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdatePhotoRequest {
        private Integer pageNumber;
        @jakarta.validation.constraints.Size(max = 300)
        private String note;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AlbumPhotoResponse {
        private String id;
        private String photoId;
        private int position;
        private Integer pageNumber;
        private String note;
        private String filename;
        private String thumbnailUrl;
        private String previewUrl;
        private boolean cover;
    }
}
