package com.cameraman.engagement.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import com.cameraman.package_mgmt.domain.PackageCategory;
import jakarta.persistence.*;
import lombok.*;

/**
 * An admin-configurable rule that generates a SuggestedOutreach entry when its
 * trigger date arrives for a booking. Ships with a handful of seeded templates
 * (see V17 migration) covering the exact scenarios discussed: maternity ->
 * newborn suggestion, birthday -> next-year reminder, wedding -> anniversary
 * shoot, any category -> post-delivery review request. Admins can edit, disable,
 * or add their own.
 */
@Entity
@Table(name = "suggestion_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SuggestionRule extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 150)
    private String name;

    /** Null = applies to every category. */
    @Enumerated(EnumType.STRING)
    @Column
    private PackageCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private SuggestionTriggerType triggerType;

    /** Required when triggerType = RELATIVE_TO_ANSWER_DATE — which intake
     *  question's (date-type) answer to anchor off of. */
    @Column(name = "trigger_question_key", length = 80)
    private String triggerQuestionKey;

    /** Days offset from the anchor date. Negative = before (e.g. -60 = 60 days
     *  before the anchor), positive = after. */
    @Column(name = "offset_days", nullable = false)
    private int offsetDays;

    /** Message template. Supports {clientName} — extend with more placeholders
     *  as needed. */
    @Column(name = "message_template", nullable = false, columnDefinition = "TEXT")
    private String messageTemplate;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** When true, the engine sends this rule's message immediately once its trigger date
     *  arrives (subject to the candidate having opted in to marketing contact and having
     *  a contact number) instead of dropping it into the admin review queue. Off by
     *  default — a new rule always starts in review-required mode so an admin can see how
     *  a few of its messages read before trusting it to go out unattended. */
    @Column(name = "auto_send", nullable = false)
    @Builder.Default
    private boolean autoSend = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    @Builder.Default
    private OutreachChannel channel = OutreachChannel.WHATSAPP;

    /** When true, this rule doesn't stop after its first suggestion — it fires again
     *  every year (anchorDate + N years + offsetDays, for N = 1, 2, 3, ...). This is
     *  what makes "remind me every birthday", not just the 1st, possible: a maternity
     *  ->newborn rule with recurring=false fires once around the baby's 1st birthday
     *  and never again, while the same rule with recurring=true keeps suggesting a
     *  birthday-shoot message every year after. See SuggestionEngineScheduler for how
     *  the occurrence number is tracked so each year only sends once. */
    @Column(name = "recurring", nullable = false)
    @Builder.Default
    private boolean recurring = false;
}
