package com.cameraman.studio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class StudioSessionDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        private String clientId;
        @NotBlank private String clientName;
        private String clientMobile;
        @Email private String clientEmail;
        private String packageId;
        private String packageName;
        @NotNull private LocalDate sessionDate;
        @NotNull private LocalTime startTime;
        private LocalTime endTime;
        private String studioRoom;
        private String backdropNotes;
        private String propsRequired;
        private String photographerId;
        private String editorId;
        private BigDecimal totalAmount;
        private BigDecimal advanceAmount;
        private String notes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        private String clientName;
        private String clientMobile;
        private String clientEmail;
        private String packageId;
        private String packageName;
        private LocalDate sessionDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String studioRoom;
        private String backdropNotes;
        private String propsRequired;
        private String photographerId;
        private String editorId;
        private String status;
        private BigDecimal totalAmount;
        private BigDecimal advanceAmount;
        private String notes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RescheduleRequest {
        @NotNull private LocalDate newDate;
        @NotNull private LocalTime newStartTime;
        private LocalTime newEndTime;
        private String reason;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CancelRequest {
        private String reason;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AvailabilityRequest {
        @NotNull private LocalDate date;
        private String studioRoom;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SlotAvailability {
        private LocalTime startTime;
        private LocalTime endTime;
        private boolean available;
        private String bookedBy;
        private String closedReason;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StudioSessionResponse {
        private String id;
        private String sessionNumber;
        private String clientId;
        private String clientName;
        private String clientMobile;
        private String clientEmail;
        private String packageId;
        private String packageName;
        private LocalDate sessionDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String studioRoom;
        private String backdropNotes;
        private String propsRequired;
        private String photographerId;
        private String photographerName;
        private String editorId;
        private String editorName;
        private String status;
        private BigDecimal totalAmount;
        private BigDecimal advanceAmount;
        private BigDecimal balanceAmount;
        private String notes;
        private String cancelledReason;
        private int rescheduledCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
