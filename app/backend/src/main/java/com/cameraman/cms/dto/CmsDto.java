package com.cameraman.cms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;

public class CmsDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SlideRequest {
        private String title;
        private String subtitle;
        private String imageUrl;
        private String videoUrl;
        private String ctaText;
        private String ctaUrl;
        private String slideType;
        private int sortOrder;
        private boolean active = true;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SlideResponse {
        private String id;
        private String title;
        private String subtitle;
        private String imageUrl;
        private String videoUrl;
        private String ctaText;
        private String ctaUrl;
        private String slideType;
        private int sortOrder;
        private boolean active;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TestimonialRequest {
        private String clientName;
        private String clientPhotoUrl;
        private String review;
        private int rating;
        private String packageType;
        private int sortOrder;
        private boolean active = true;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TestimonialResponse {
        private String id;
        private String clientName;
        private String clientPhotoUrl;
        private String review;
        private int rating;
        private String packageType;
        private int sortOrder;
        private boolean active;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PageRequest {
        private String title;
        private String content;
        private boolean published;
        private String metaTitle;
        private String metaDescription;
        // Admin CMS-pages screen speaks in "slug"/"active" rather than the
        // "pageKey"/"published" vocabulary used by the public-facing
        // GET /cms/public/pages/{key} endpoint. Accept both.
        private String slug;
        private boolean active;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PageResponse {
        private String id;
        private String pageKey;
        private String title;
        private String content;
        private boolean published;
        private String metaTitle;
        private String metaDescription;
        private LocalDateTime updatedAt;
        private LocalDateTime createdAt;
        // Aliases for the admin CMS-pages screen (same data as pageKey/published).
        private String slug;
        private boolean active;
    }
}
