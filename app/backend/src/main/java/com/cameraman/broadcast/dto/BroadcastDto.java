package com.cameraman.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

public class BroadcastDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateCampaignRequest {
        @NotBlank @Size(max = 150)
        private String title;

        @NotBlank @Size(max = 4000)
        private String messageText;

        private String mediaUrl;
        private String mediaType; // NONE, IMAGE, VIDEO
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CampaignResponse {
        private String id;
        private String title;
        private String messageText;
        private String mediaUrl;
        private String mediaType;
        private String status;
        private int totalRecipients;
        private int sentCount;
        private int failedCount;
        private String createdByName;
        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RecipientResponse {
        private String id;
        private String name;
        private String mobile;
        private String status;
        private String errorMessage;
        private LocalDateTime sentAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AudiencePreviewResponse {
        private long totalEligible;
        private java.util.List<String> sampleNames;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MediaUploadResponse {
        private String url;
        private String mediaType;
    }
}
