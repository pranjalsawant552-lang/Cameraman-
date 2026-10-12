package com.cameraman.gallery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;

public class PhotoDto {
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PhotoResponse {
        private String id;
        private String galleryId;
        private String originalFilename;
        private String status;
        private String thumbnailUrl;
        private String previewUrl;
        private String watermarkedUrl;
        private long fileSizeBytes;
        private Integer width;
        private Integer height;
        private String mimeType;
        private String caption;
        private boolean favourite;
        private int sortOrder;
        private long downloadCount;
        private boolean originalPurged;
        private String failureReason;
        private LocalDateTime createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        private String caption;
        private String tags;
        private boolean favourite;
        private Integer sortOrder;
    }
}
