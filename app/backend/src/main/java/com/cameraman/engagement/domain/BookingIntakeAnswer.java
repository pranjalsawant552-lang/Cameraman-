package com.cameraman.engagement.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A client's answer to one IntakeQuestion, captured against a specific booking.
 * Kept as a simple key-value row (not a rigid typed column per question) since the
 * set of questions is admin-configurable and grows over time — same flexible
 * pattern already proven to work well for StudioSetting elsewhere in this app.
 */
@Entity
@Table(name = "booking_intake_answers", uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "question_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BookingIntakeAnswer extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    /** Matches IntakeQuestion.questionKey — not a FK to keep this resilient even
     *  if the question definition is later edited or deactivated. */
    @Column(name = "question_key", nullable = false, length = 80)
    private String questionKey;

    /** Raw answer text as entered. For DATE-type questions this is stored as
     *  ISO-8601 (yyyy-MM-dd) so the suggestion engine can parse it reliably. */
    @Column(name = "answer_value", columnDefinition = "TEXT")
    private String answerValue;
}
