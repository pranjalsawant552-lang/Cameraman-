package com.cameraman.storage.dto;

import lombok.*;

public class StorageDto {
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadResponse {
        private String key;
        private String url;
        private String bucket;
        private String contentType;
        private long sizeBytes;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PresignedUrlRequest {
        private String folder;
        private String filename;
        private String contentType;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PresignedUrlResponse {
        private String uploadUrl;
        private String key;
        private long expiresInSeconds;
    }
}
