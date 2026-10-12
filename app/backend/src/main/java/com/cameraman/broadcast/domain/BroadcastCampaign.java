package com.cameraman.broadcast.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "broadcast_campaigns")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BroadcastCampaign {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "message_text", nullable = false, length = 4000)
    private String messageText;

    @Column(name = "media_url", length = 1000)
    private String mediaUrl;

    @Column(name = "media_type", length = 10)
    private String mediaType; // NONE, IMAGE, VIDEO

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "QUEUED"; // QUEUED, SENDING, COMPLETED, FAILED

    @Column(name = "total_recipients", nullable = false)
    @Builder.Default
    private int totalRecipients = 0;

    @Column(name = "sent_count", nullable = false)
    @Builder.Default
    private int sentCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_by_name", length = 150)
    private String createdByName;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
