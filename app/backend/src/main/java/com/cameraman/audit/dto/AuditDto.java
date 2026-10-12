package com.cameraman.audit.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public class AuditDto {

    @Getter @Setter @Builder
    public static class LogResponse {
        private String id;
        private String action;
        private String entityType;
        private String entityId;
        private String performedBy;
        private String performedByName;
        private String details;
        private String ipAddress;
        private LocalDateTime createdAt;
    }
}
