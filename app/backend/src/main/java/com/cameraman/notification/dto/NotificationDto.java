package com.cameraman.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;

public class NotificationDto {
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NotificationResponse {
        private String id;
        private String title;
        private String message;
        private String type;
        private boolean read;
        private String entityType;
        private String entityId;
        private LocalDateTime createdAt;
    }
}
