package com.cameraman.engagement.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import com.cameraman.package_mgmt.domain.PackageCategory;
import jakarta.persistence.*;
import lombok.*;

/**
 * An admin-defined question asked of the client for a given shoot category —
 * e.g. MATERNITY -> "Expected delivery date", BIRTHDAY -> "Child's date of birth"
 * and "Which birthday is this?". Answers (BookingIntakeAnswer) captured against a
 * booking feed the SuggestionRule engine — this is deliberately separate from
 * generic booking notes because the suggestion engine needs a *structured*,
 * queryable answer (a real date it can do math on), not freeform text.
 */
@Entity
@Table(name = "intake_questions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IntakeQuestion extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackageCategory category;

    /** Stable machine key referenced by SuggestionRule.triggerQuestionKey, e.g.
     *  "expected_delivery_date" — independent of the human-editable questionText
     *  so a rule doesn't break if the admin rewords the question later. */
    @Column(name = "question_key", nullable = false, length = 80)
    private String questionKey;

    @Column(name = "question_text", nullable = false, length = 300)
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false)
    private IntakeFieldType fieldType;

    /** Comma-separated options, only used when fieldType = SELECT. */
    @Column(name = "options", length = 500)
    private String options;

    @Column(nullable = false)
    @Builder.Default
    private boolean required = false;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
