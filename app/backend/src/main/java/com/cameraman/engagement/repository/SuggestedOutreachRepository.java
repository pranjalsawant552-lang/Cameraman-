package com.cameraman.engagement.repository;

import com.cameraman.engagement.domain.OutreachStatus;
import com.cameraman.engagement.domain.SuggestedOutreach;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuggestedOutreachRepository extends JpaRepository<SuggestedOutreach, String> {
    Page<SuggestedOutreach> findByStatus(OutreachStatus status, Pageable pageable);

    /** Non-recurring rules: has this rule+candidate ever gotten a (single, occurrence-1)
     *  suggestion before? If so, never generate another one for it. */
    Optional<SuggestedOutreach> findByRuleIdAndBookingIdAndOccurrenceNumber(String ruleId, String bookingId, int occurrenceNumber);

    /** Recurring rules: what's the most recent annual occurrence already generated for
     *  this rule+candidate? The next one due is simply this + 1 — see
     *  SuggestionEngineScheduler for how that's turned into a trigger date. */
    Optional<SuggestedOutreach> findTopByRuleIdAndBookingIdOrderByOccurrenceNumberDesc(String ruleId, String bookingId);

    long countByStatus(OutreachStatus status);
}
