package com.cameraman.contact.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contact_inquiries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContactInquiry extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String mobile;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "NEW"; // NEW, CONTACTED, CLOSED

    @Column(name = "reply_count", nullable = false)
    @Builder.Default
    private int replyCount = 0;

    @Column(name = "last_replied_at")
    private java.time.LocalDateTime lastRepliedAt;

    @Column(name = "last_replied_by", length = 150)
    private String lastRepliedBy;
}
