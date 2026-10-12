package com.cameraman.photoselection.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;

public class SelectionDto {
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotBlank private String galleryId;
        private String bookingId;
        private String clientId;
        private String clientName;
        private Integer selectionLimit;
        private LocalDateTime expiresAt;
        private String adminNotes;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SelectionResponse {
        private String id;
        private String galleryId;
        private String bookingId;
        private String clientId;
        private String clientName;
        private String accessToken;
        private Integer selectionLimit;
        private int selectedCount;
        private String status;
        private LocalDateTime lockedAt;
        private LocalDateTime expiresAt;
        private String adminNotes;
        private String clientNotes;
        private LocalDateTime createdAt;
        /** When the picks were last saved (draft or otherwise); shown to the client as "Draft saved at ...". */
        private LocalDateTime lastSavedAt;
    }

    /** Whole-draft save: the complete list of picked photo ids replaces whatever was saved before. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class SaveDraftRequest {
        private java.util.List<String> photoIds = new java.util.ArrayList<>();
        /** Optional notes for the editor, kept with the draft so they are not lost either. */
        private String clientNotes;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SelectedPhotoResponse {
        private String id;
        private String photoId;
        private LocalDateTime selectedAt;
        private boolean favourite;
        private String notes;
    }
}
