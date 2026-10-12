package com.cameraman.contact.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One follow-up on a contact inquiry: an email sent from the app, or a call / WhatsApp the team logged. */
@Entity
@Table(name = "contact_inquiry_replies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContactInquiryReply {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "inquiry_id", nullable = false, length = 36)
    private String inquiryId;

    /** EMAIL, PHONE, WHATSAPP or OTHER. */
    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 4000)
    private String message;

    /** True when this app actually sent the text by email. */
    @Column(nullable = false)
    private boolean emailed;

    @Column(name = "replied_by_id", length = 36)
    private String repliedById;

    @Column(name = "replied_by_name", length = 150)
    private String repliedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
