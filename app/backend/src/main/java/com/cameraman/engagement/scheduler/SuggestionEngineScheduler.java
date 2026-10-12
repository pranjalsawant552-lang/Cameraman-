package com.cameraman.engagement.scheduler;

import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.domain.BookingStatus;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.engagement.domain.*;
import com.cameraman.engagement.repository.BookingIntakeAnswerRepository;
import com.cameraman.engagement.repository.SuggestedOutreachRepository;
import com.cameraman.engagement.repository.SuggestionRuleRepository;
import com.cameraman.outdoor.domain.OutdoorShoot;
import com.cameraman.outdoor.domain.OutdoorShootCategory;
import com.cameraman.outdoor.domain.OutdoorShootStatus;
import com.cameraman.outdoor.repository.OutdoorShootRepository;
import com.cameraman.package_mgmt.domain.PackageCategory;
import com.cameraman.studio.domain.StudioSession;
import com.cameraman.studio.domain.StudioSessionStatus;
import com.cameraman.studio.repository.StudioSessionRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The actual "suggestion engine": runs daily, evaluates every active
 * SuggestionRule against every non-cancelled shoot of any kind, and drops a new
 * SuggestedOutreach into the admin review queue whenever a rule's trigger date
 * has arrived — e.g. "60 days before the answered expected-delivery-date" for a
 * maternity booking, or "358 days after the shoot date" for a birthday booking.
 *
 * Covers all three booking-shaped tracks the app has (general Bookings, Studio
 * Sessions, Outdoor Shoots) — see EngagementCandidate below. Originally this
 * only looked at the general Bookings table, which meant anything logged
 * purely through Studio Sessions or Outdoor Shoots (the newer, more commonly
 * used modules) got zero suggestion coverage regardless of how any rule was
 * configured. RELATIVE_TO_BOOKING_DATE now works identically across all three;
 * RELATIVE_TO_ANSWER_DATE and RELATIVE_TO_DELIVERY_DATE remain Booking-only
 * because intake answers and a recorded delivery timestamp are both
 * Booking-specific concepts that Studio Sessions/Outdoor Shoots don't have an
 * equivalent for today — a rule using either of those trigger types simply
 * won't fire for a session/shoot candidate, which is correct, not a bug.
 *
 * Never sends anything itself for rules left in the default (review-required) mode —
 * see OutreachService for the explicit admin-approval send step. If a rule has autoSend
 * enabled, though, this scheduler does send it directly (via OutreachService.autoSend)
 * the moment its trigger date arrives, provided the candidate has opted in to marketing
 * contact and has a mobile number on file; otherwise it still falls back to the review
 * queue so nothing is silently dropped. This only decides *when* a suggestion is due,
 * builds the draft message from the rule's template, and — for auto-send rules only —
 * dispatches it.
 *
 * By default a rule fires once per candidate and never again (e.g. "suggest a 1st
 * birthday shoot around a newborn booking's first anniversary"). Setting
 * SuggestionRule.recurring=true instead makes it fire every year from then on — the
 * same rule keeps suggesting a birthday shoot for the 2nd, 3rd, 4th birthday, etc. See
 * nextOccurrenceFor()/applyOccurrence() for how each year is tracked as its own
 * SuggestedOutreach row so a year already sent doesn't block the next one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuggestionEngineScheduler {

    private final SuggestionRuleRepository ruleRepository;
    private final BookingRepository bookingRepository;
    private final StudioSessionRepository studioSessionRepository;
    private final OutdoorShootRepository outdoorShootRepository;
    private final BookingIntakeAnswerRepository answerRepository;
    private final SuggestedOutreachRepository outreachRepository;
    private final com.cameraman.engagement.service.OutreachService outreachService;
    private final com.cameraman.notification.service.NotificationPublisher notificationPublisher;
    private final com.cameraman.settings.service.StudioSettingsService settingsService;

    private static final List<String> ADMIN_ROLES = List.of("ADMIN", "SUPER_ADMIN", "MANAGER");

    @Scheduled(cron = "0 0 6 * * *") // 6:00 AM daily — queue is ready before the studio opens
    @Transactional
    public void evaluateRules() {
        List<SuggestionRule> activeRules = ruleRepository.findByActiveTrue();
        if (activeRules.isEmpty()) {
            return;
        }

        List<EngagementCandidate> candidates = buildCandidates();

        int queuedForReview = 0;
        int autoSent = 0;
        for (SuggestionRule rule : activeRules) {
            for (EngagementCandidate candidate : candidates) {
                if (rule.getCategory() != null && rule.getCategory() != candidate.getCategory()) {
                    continue;
                }

                int nextOccurrence = nextOccurrenceFor(rule, candidate);
                if (nextOccurrence == -1) {
                    continue; // non-recurring rule already has its one-and-only suggestion
                }

                Optional<LocalDate> triggerDate = resolveTriggerDate(rule, candidate, nextOccurrence);
                if (triggerDate.isEmpty() || triggerDate.get().isAfter(LocalDate.now())) {
                    continue;
                }

                if (createOutreach(rule, candidate, triggerDate.get(), nextOccurrence)) {
                    autoSent++;
                } else {
                    queuedForReview++;
                }
            }
        }

        if (queuedForReview > 0) {
            log.info("Suggestion engine: queued {} new outreach suggestion(s) for admin review", queuedForReview);
            notificationPublisher.notifyRoles(ADMIN_ROLES, "SUGGESTIONS_PENDING",
                    "New customer suggestions to review",
                    queuedForReview + " new outreach suggestion(s) are waiting for review in the engagement queue.",
                    "SuggestedOutreach", null);
        }
        if (autoSent > 0) {
            log.info("Suggestion engine: auto-sent {} outreach message(s)", autoSent);
            notificationPublisher.notifyRoles(ADMIN_ROLES, "SUGGESTIONS_AUTO_SENT",
                    "Reminders auto-sent to customers",
                    autoSent + " reminder/suggestion message(s) were sent automatically today.",
                    "SuggestedOutreach", null);
        }
    }

    private List<EngagementCandidate> buildCandidates() {
        List<EngagementCandidate> candidates = new ArrayList<>();

        for (Booking b : bookingRepository.findAll()) {
            if (b.getStatus() == BookingStatus.CANCELLED) { continue; }
            candidates.add(EngagementCandidate.builder()
                    .id(b.getId()).sourceType("BOOKING")
                    .clientId(b.getClient() != null ? b.getClient().getId() : null)
                    .clientName(b.getClientName()).clientMobile(b.getClientMobile())
                    .category(b.getShootCategory())
                    .anchorDate(b.getBookingDate())
                    .deliveredAt(b.getDeliveredAt() != null ? b.getDeliveredAt().toLocalDate() : null)
                    .intakeAnswerBookingId(b.getId())
                    .optedIn(b.isMarketingOptIn() && (b.getClient() == null || b.getClient().isMarketingOptIn()))
                    .build());
        }

        for (StudioSession s : studioSessionRepository.findAll()) {
            if (s.getStatus() == StudioSessionStatus.CANCELLED) { continue; }
            candidates.add(EngagementCandidate.builder()
                    .id(s.getId()).sourceType("STUDIO_SESSION")
                    .clientId(s.getClient() != null ? s.getClient().getId() : null)
                    .clientName(s.getClientName()).clientMobile(s.getClientMobile())
                    .category(null) // Studio Sessions have no category field — matches wildcard rules only
                    .anchorDate(s.getSessionDate())
                    .deliveredAt(null) // no delivery-timestamp concept for this track
                    .intakeAnswerBookingId(null) // no intake-answer flow for this track
                    // No per-session consent flag exists yet (only Booking/User carry one) —
                    // falls back to the linked client account's account-wide preference, or
                    // opted-in by default if there's no linked account to check.
                    .optedIn(s.getClient() == null || s.getClient().isMarketingOptIn())
                    .build());
        }

        for (OutdoorShoot o : outdoorShootRepository.findAll()) {
            if (o.getStatus() == OutdoorShootStatus.CANCELLED) { continue; }
            candidates.add(EngagementCandidate.builder()
                    .id(o.getId()).sourceType("OUTDOOR_SHOOT")
                    .clientId(o.getClient() != null ? o.getClient().getId() : null)
                    .clientName(o.getClientName()).clientMobile(o.getClientMobile())
                    .category(mapOutdoorCategory(o.getCategory()))
                    .anchorDate(o.getShootDate())
                    .deliveredAt(null)
                    .intakeAnswerBookingId(null)
                    .optedIn(o.getClient() == null || o.getClient().isMarketingOptIn())
                    .build());
        }

        return candidates;
    }

    /** OutdoorShootCategory and PackageCategory are separate enums (outdoor shoots existed
     *  before the engagement module and were never unified with it) — map the ones with an
     *  unambiguous equivalent so category-specific rules (e.g. a WEDDING rule) still reach
     *  outdoor-shoot candidates; anything without a clean match falls back to OTHER rather
     *  than guessing. DESTINATION deliberately maps to OTHER, not WEDDING — a destination
     *  shoot isn't necessarily a wedding, and a wrong guess here would misfire a rule. */
    private PackageCategory mapOutdoorCategory(OutdoorShootCategory category) {
        if (category == null) { return null; }
        return switch (category) {
            case WEDDING -> PackageCategory.WEDDING;
            case PRE_WEDDING -> PackageCategory.PRE_WEDDING;
            case EVENT -> PackageCategory.EVENT;
            case CORPORATE_ONSITE -> PackageCategory.CORPORATE;
            case DESTINATION, OTHER -> PackageCategory.OTHER;
        };
    }

    /**
     * Returns which occurrence number is next due to be generated for this rule+candidate,
     * or -1 if nothing more should ever be generated (a non-recurring rule that has
     * already fired once).
     *
     *  - Non-recurring: occurrence 1 only, ever. If it already exists (sent, pending, or
     *    dismissed — doesn't matter), this rule is done with this candidate for good.
     *  - Recurring: whatever the highest occurrence generated so far is, plus one. If none
     *    exist yet, that's occurrence 1 (the first anniversary) — same starting point as a
     *    non-recurring rule, the only difference is it doesn't stop there.
     */
    private int nextOccurrenceFor(SuggestionRule rule, EngagementCandidate candidate) {
        if (!rule.isRecurring()) {
            return outreachRepository.findByRuleIdAndBookingIdAndOccurrenceNumber(rule.getId(), candidate.getId(), 1).isPresent()
                    ? -1 : 1;
        }
        return outreachRepository.findTopByRuleIdAndBookingIdOrderByOccurrenceNumberDesc(rule.getId(), candidate.getId())
                .map(o -> o.getOccurrenceNumber() + 1)
                .orElse(1);
    }

    private Optional<LocalDate> resolveTriggerDate(SuggestionRule rule, EngagementCandidate candidate, int occurrence) {
        return switch (rule.getTriggerType()) {
            case RELATIVE_TO_BOOKING_DATE -> candidate.getAnchorDate() != null
                    ? Optional.of(applyOccurrence(candidate.getAnchorDate(), rule, occurrence)) : Optional.empty();

            case RELATIVE_TO_DELIVERY_DATE -> candidate.getDeliveredAt() != null
                    ? Optional.of(applyOccurrence(candidate.getDeliveredAt(), rule, occurrence)) : Optional.empty();

            case RELATIVE_TO_ANSWER_DATE -> {
                if (rule.getTriggerQuestionKey() == null || candidate.getIntakeAnswerBookingId() == null) {
                    yield Optional.empty();
                }
                yield answerRepository.findByBookingIdAndQuestionKey(candidate.getIntakeAnswerBookingId(), rule.getTriggerQuestionKey())
                        .map(a -> {
                            try {
                                return applyOccurrence(LocalDate.parse(a.getAnswerValue()), rule, occurrence);
                            } catch (DateTimeParseException | NullPointerException e) {
                                return null;
                            }
                        });
            }
        };
    }

    /** For a non-recurring rule this is exactly the original "anchor + offsetDays" math,
     *  untouched — occurrence is always 1 and doesn't factor in. For a recurring rule, each
     *  occurrence lands offsetDays away from that year's anniversary of the anchor date
     *  (plusYears first, so leap-year Feb 29 anchors land correctly rather than drifting
     *  the way repeated plusDays(365) would). */
    private LocalDate applyOccurrence(LocalDate anchor, SuggestionRule rule, int occurrence) {
        return rule.isRecurring()
                ? anchor.plusYears(occurrence).plusDays(rule.getOffsetDays())
                : anchor.plusDays(rule.getOffsetDays());
    }

    private boolean createOutreach(SuggestionRule rule, EngagementCandidate candidate, LocalDate triggerDate, int occurrence) {
        String message = applyTemplate(rule.getMessageTemplate(), candidate, triggerDate, occurrence);
        SuggestedOutreach outreach = SuggestedOutreach.builder()
                .ruleId(rule.getId()).ruleName(rule.getName())
                .bookingId(candidate.getId())
                .sourceType(candidate.getSourceType())
                .clientId(candidate.getClientId())
                .clientName(candidate.getClientName()).clientMobile(candidate.getClientMobile())
                .suggestedMessage(message)
                .channel(rule.getChannel())
                .triggerDate(triggerDate)
                .occurrenceNumber(occurrence)
                .status(OutreachStatus.PENDING_REVIEW)
                .build();

        boolean canAutoSend = rule.isAutoSend() && candidate.isOptedIn()
                && candidate.getClientMobile() != null && !candidate.getClientMobile().isBlank();

        if (canAutoSend) {
            outreachService.autoSend(outreach); // persists with AUTO_SENT or AUTO_SEND_FAILED
            return true;
        }
        outreachRepository.save(outreach);
        return false;
    }

    private static final java.time.format.DateTimeFormatter MESSAGE_DATE_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy");

    private static final String[] ORDINAL_SUFFIXES = {"th", "st", "nd", "rd"};

    /** "1st", "2nd", "3rd", "4th", ... "11th", "12th", "13th" (the 11-13 exception to the
     *  usual 1/2/3 pattern), for a natural-reading {occurrenceOrdinal} in a recurring
     *  rule's template — e.g. "Happy {occurrenceOrdinal} birthday, {clientName}!" */
    private String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        return n + ORDINAL_SUFFIXES[Math.min(n % 10, 3)];
    }

    /** {clientName} was the only placeholder available before — every rule's message had
     *  to either omit useful context or hardcode it, which meant re-creating near-duplicate
     *  rules just to reference a different studio name or mention the shoot date. Expanded
     *  set below covers what's actually available on every candidate regardless of source
     *  (a shoot date exists for all three tracks; a delivery date only for Booking, so that
     *  one is only substituted when present — left as literal text otherwise rather than
     *  silently blanking it out, so a misconfigured rule is obvious in the draft, not hidden).
     *  {occurrenceNumber}/{occurrenceOrdinal} only really mean something for a recurring
     *  rule ("Happy {occurrenceOrdinal} birthday!") but are substituted either way — for a
     *  non-recurring rule they're always 1/"1st", which is harmless if unused in the template. */
    private String applyTemplate(String template, EngagementCandidate candidate, LocalDate triggerDate, int occurrence) {
        String result = template
                .replace("{clientName}", candidate.getClientName() != null ? candidate.getClientName() : "")
                .replace("{studioName}", settingsService.getStudioName())
                .replace("{triggerDate}", triggerDate.format(MESSAGE_DATE_FORMAT))
                .replace("{occurrenceNumber}", String.valueOf(occurrence))
                .replace("{occurrenceOrdinal}", ordinal(occurrence));
        if (candidate.getAnchorDate() != null) {
            result = result.replace("{shootDate}", candidate.getAnchorDate().format(MESSAGE_DATE_FORMAT));
        }
        if (candidate.getDeliveredAt() != null) {
            result = result.replace("{deliveryDate}", candidate.getDeliveredAt().format(MESSAGE_DATE_FORMAT));
        }
        return result;
    }

    /** Unifies Booking/StudioSession/OutdoorShoot into the one shape the engine actually
     *  needs, so the rule-evaluation logic above is written once instead of three times. */
    @Getter
    @Builder
    private static class EngagementCandidate {
        private String id;
        private String sourceType;
        private String clientId;
        private String clientName;
        private String clientMobile;
        private PackageCategory category;
        private LocalDate anchorDate;
        private LocalDate deliveredAt;
        private String intakeAnswerBookingId;
        private boolean optedIn;
    }
}
