package com.cameraman.engagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public class EngagementDto {

    // ── Intake questions ──
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class QuestionRequest {
        @NotBlank private String category;
        @NotBlank private String questionKey;
        @NotBlank private String questionText;
        @NotBlank private String fieldType;
        private String options;
        private boolean required;
        private int sortOrder;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuestionResponse {
        private String id;
        private String category;
        private String questionKey;
        private String questionText;
        private String fieldType;
        private String options;
        private boolean required;
        private int sortOrder;
        private boolean active;
    }

    // ── Booking answers ──
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SubmitAnswersRequest {
        /** questionKey -> answer value */
        private Map<String, String> answers;
    }

    // ── Suggestion rules ──
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RuleRequest {
        @NotBlank private String name;
        private String category;
        @NotBlank private String triggerType;
        private String triggerQuestionKey;
        private int offsetDays;
        @NotBlank private String messageTemplate;
        private Boolean active;
        /** Defaults to false server-side when omitted — see SuggestionRule.autoSend. */
        private Boolean autoSend;
        /** WHATSAPP or SMS. Defaults to WHATSAPP server-side when omitted. */
        private String channel;
        /** When true, fires every year (not just once) — see SuggestionRule.recurring. */
        private Boolean recurring;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RuleResponse {
        private String id;
        private String name;
        private String category;
        private String triggerType;
        private String triggerQuestionKey;
        private int offsetDays;
        private String messageTemplate;
        private boolean active;
        private boolean autoSend;
        private String channel;
        private boolean recurring;
    }

    // ── Outreach queue ──
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateOutreachRequest {
        private String editedMessage;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DismissOutreachRequest {
        private String reason;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OutreachResponse {
        private String id;
        private String ruleName;
        private String bookingId;
        private String sourceType;
        private String clientId;
        private String clientName;
        private String clientMobile;
        private String suggestedMessage;
        private String channel;
        private LocalDate triggerDate;
        private int occurrenceNumber;
        private String status;
        private LocalDateTime sentAt;
        private String dismissedReason;
        private LocalDateTime createdAt;
    }
}
