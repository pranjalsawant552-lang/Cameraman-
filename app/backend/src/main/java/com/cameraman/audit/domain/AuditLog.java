package com.cameraman.audit.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
    @Index(name = "idx_audit_user", columnList = "performed_by"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_created", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 36)
    private String entityId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "performed_by", length = 150)
    private String performedBy;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "module", length = 60)
    private String module;
}
