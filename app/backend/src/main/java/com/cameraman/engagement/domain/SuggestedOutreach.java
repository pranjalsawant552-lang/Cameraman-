package com.cameraman.engagement.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One generated suggestion — "it's been almost a year since {client}'s birthday
 * shoot, want to send this reminder?" By default this lands in the admin review
 * queue (PENDING_REVIEW): an admin reviews, optionally edits the message, and
 * explicitly approves before anything goes to a client. If the originating rule
 * has autoSend enabled, and the client has opted in to marketing contact, it is
 * instead sent immediately by the engine (status AUTO_SENT/AUTO_SEND_FAILED) —
 * see SuggestionEngineScheduler for how these get created and OutreachService for
 * both the manual approve/edit/dismiss actions and the automated send path.
 */
@Entity
@Table(name = "suggested_outreach")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SuggestedOutreach extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "rule_name", length = 150)
    private String ruleName;

    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    /** Which table bookingId actually points at — BOOKING, STUDIO_SESSION, or
     *  OUTDOOR_SHOOT. Needed because the suggestion engine now evaluates all three
     *  tracks (see SuggestionEngineScheduler), and bookingId has no DB-level foreign
     *  key any more since it can reference three different tables. */
    @Column(name = "source_type", nullable = false, length = 20)
    @Builder.Default
    private String sourceType = "BOOKING";

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_name", length = 150)
    private String clientName;

    @Column(name = "client_mobile", length = 15)
    private String clientMobile;

    @Column(name = "suggested_message", nullable = false, columnDefinition = "TEXT")
    private String suggestedMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutreachChannel channel = OutreachChannel.WHATSAPP;

    /** The date the rule's trigger condition fired — mostly for admin context
     *  ("why is this in my queue today?"). */
    @Column(name = "trigger_date")
    private LocalDate triggerDate;

    /** Which annual cycle this is, for a recurring rule (1 = the first anniversary,
     *  2 = the second, etc). Always 1 for a non-recurring rule. This is what lets a
     *  recurring rule generate a fresh suggestion every year for the same booking
     *  instead of the "already suggested for this rule+booking, never again" check
     *  blocking it after the first send — each occurrence number gets its own row. */
    @Column(name = "occurrence_number", nullable = false)
    @Builder.Default
    private int occurrenceNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutreachStatus status = OutreachStatus.PENDING_REVIEW;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "dismissed_reason", length = 300)
    private String dismissedReason;
}
