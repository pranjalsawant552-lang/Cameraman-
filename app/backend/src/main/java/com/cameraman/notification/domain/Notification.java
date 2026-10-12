package com.cameraman.notification.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_user", columnList = "user_id, is_read")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 20)
    @Builder.Default
    private String channel = "IN_APP";

    @Column(name = "is_read")
    @Builder.Default
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 36)
    private String entityId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
