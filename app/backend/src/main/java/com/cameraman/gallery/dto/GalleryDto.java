package com.cameraman.gallery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

public class GalleryDto {
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotBlank @Size(max = 200)
        private String name;
        private String bookingId;
        private String clientId;
        private String clientName;
        private String description;
        private Integer selectionLimit;
        private boolean downloadAllowed;
        private boolean watermarkEnabled;
        private boolean passwordProtected;
        private String galleryPassword;
        private LocalDateTime expiresAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        private String name;
        private String description;
        private String status;
        private Integer selectionLimit;
        private boolean downloadAllowed;
        private boolean watermarkEnabled;
        private LocalDateTime expiresAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SetOriginalsExpiryRequest {
        /** Days after publish before unselected originals auto-purge. Null = never auto-expire. */
        private Integer days;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class SetDownloadsRequest {
        /** true = clean photos + downloads for the client; false = watermarked, no downloads. */
        private boolean allowed;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PurgeResult {
        private int purgedCount;
        private LocalDateTime purgedAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GalleryResponse {
        private String id;
        private String name;
        private String bookingId;
        private String clientId;
        private String clientName;
        private String description;
        private String status;
        private String accessToken;
        private LocalDateTime accessTokenExpires;
        private boolean passwordProtected;
        private Integer selectionLimit;
        private boolean downloadAllowed;
        private boolean watermarkEnabled;
        private String coverPhotoId;
        private String coverPhotoUrl;
        private int photoCount;
        private long totalSizeBytes;
        private LocalDateTime publishedAt;
        private LocalDateTime expiresAt;
        private Integer originalsExpiryDays;
        private LocalDateTime originalsExpiredAt;
        private long viewCount;
        private LocalDateTime createdAt;
        /** Set only when this gallery has an OPEN photo selection waiting on the client —
         *  lets the gallery view show a "Pick Your Favorites" call-to-action linking
         *  straight to it, instead of the client having to separately find that link. */
        private String openSelectionToken;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PresignedUploadResponse {
        /** DIRECT mode only: presigned PUT URL. Null for CHUNKED and EXISTS. */
        private String uploadUrl;
        private String photoId;
        private String key;
        /** DIRECT (browser PUTs to storage), CHUNKED (browser sends chunks to the API) or
         *  EXISTS (already uploaded, nothing to send). */
        private String mode;
        /** CHUNKED only: size of every chunk except the last. */
        private Integer chunkSize;
        /** CHUNKED only: bytes already stored, so an interrupted upload resumes. */
        private Long uploadedBytes;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChunkResponse {
        private boolean complete;
        private long receivedBytes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DriveImportRequest {
        @jakarta.validation.constraints.NotBlank
        private String driveFolderUrl;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DriveImportResponse {
        private int filesQueued;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PublishRequest {
        private Integer selectionLimit;
        private boolean downloadAllowed;
        private boolean watermarkEnabled;
        private boolean passwordProtected;
        private String galleryPassword;
        private LocalDateTime expiresAt;
        /** Only true when staff deliberately want a new link (the old one stops working). */
        private boolean regenerateLink;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PresignedUploadRequest {
        @NotBlank @Size(max = 300)
        private String filename;
        @NotBlank @Size(max = 50)
        private String contentType;
        /** Optional declared size in bytes; rejected up-front when over the limit. */
        private Long sizeBytes;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ConfirmUploadRequest {
        private Integer width;
        private Integer height;
    }

    /** Progress of a Google Drive import (in-memory, per gallery). */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ImportProgress {
        private boolean running;
        private int total;
        private int done;
        private int failed;
        /** Files skipped because they were already imported into this gallery. */
        private int skipped;
    }

    /** Live processing counters for a gallery, polled by the upload screen. */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadStatus {
        private long uploading;
        private long processing;
        private long ready;
        private long failed;
        private ImportProgress driveImport;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DriveStatus {
        private boolean configured;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ForcePurgeRequest {
        /** Must be true to purge when the client has not selected any photo yet (would delete every original). */
        private boolean confirmNoSelection;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ViewerInput {
        @jakarta.validation.constraints.NotBlank private String clientId;
        private boolean canSelect;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ViewersRequest {
        @jakarta.validation.Valid private java.util.List<ViewerInput> viewers;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ViewerResponse {
        private String clientId;
        private String name;
        private String email;
        private String mobile;
        private boolean canSelect;
        /** The viewer's own selection: null when they have none, else OPEN / LOCKED / APPROVED ... */
        private String selectionStatus;
        private int selectedCount;
        /** True for the gallery's primary client (always allowed to view; cannot be removed here). */
        private boolean primary;
    }
}
