package com.cameraman.broadcast.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "broadcast_recipients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BroadcastRecipient {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "campaign_id", nullable = false, length = 36)
    private String campaignId;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, SENT, FAILED

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
