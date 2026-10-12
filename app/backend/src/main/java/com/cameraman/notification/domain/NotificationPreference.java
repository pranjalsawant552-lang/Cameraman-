package com.cameraman.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_notification_preferences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationPreference {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationCategory category;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
