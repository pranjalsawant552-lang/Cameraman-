package com.cameraman.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class BookingDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        private String clientId; // optional - for registered clients

        @Size(max = 120)
        private String clientName;

        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid mobile number")
        private String clientMobile;

        @Email
        private String clientEmail;

        @NotNull
        private LocalDate bookingDate;

        @NotNull
        private LocalTime startTime;

        private LocalTime endTime;

        private String packageId;
        private String packageName;

        @NotBlank
        private String type; // ONLINE, OFFLINE, WALK_IN

        @NotBlank
        private String shootCategory; // CASUAL, WEDDING, PRE_WEDDING, BIRTHDAY, etc.

        private String photographerId;
        private String venue;
        /** INDOOR (default) or OUTDOOR. OUTDOOR requires a venue. */
        private String locationType;
        private String notes;

        private BigDecimal advanceAmount;
        private BigDecimal totalAmount;

        /** Opt-in to future re-engagement suggestions (birthday reminders, anniversary
         *  shoot suggestions, review requests, etc). Defaults to true when omitted. */
        private Boolean marketingOptIn;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        private LocalDate bookingDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String photographerId;
        private String venue;
        private String locationType;
        private String notes;
        private String status;
        private String shootCategory;
        private BigDecimal totalAmount;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RescheduleRequest {
        @NotNull
        private LocalDate newDate;

        @NotNull
        private LocalTime newStartTime;

        private LocalTime newEndTime;

        @NotBlank
        private String reason;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CancelRequest {
        @NotBlank
        private String reason;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BookingResponse {
        private String id;
        private String bookingNumber;
        private String clientId;
        private String clientName;
        private String clientMobile;
        private String clientEmail;
        private LocalDate bookingDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String packageId;
        private String packageName;
        private String type;
        private String shootCategory;
        private String status;
        private String photographerId;
        private String photographerName;
        private String venue;
        private String locationType;
        /** NOT_READY | AWAITING_PICKUP | PICKED_UP */
        private String pickupStatus;
        private LocalDateTime readyForPickupAt;
        private LocalDateTime pickedUpAt;
        private int pickupReminderCount;
        private LocalDateTime lastPickupReminderAt;
        /** Days since the order was marked ready (only while awaiting pickup). */
        private Long daysAwaitingPickup;
        private String notes;
        private BigDecimal advanceAmount;
        private BigDecimal totalAmount;
        private BigDecimal balanceAmount;
        private String cancelledReason;
        private int rescheduledCount;
        private String orderStage;
        private String orderStageLabel;
        private LocalDate expectedDeliveryDate;
        private String editorId;
        private String editorName;
        private String courierProvider;
        private String courierTrackingNumber;
        private LocalDateTime deliveredAt;
        private List<StageHistoryEntry> stageHistory;
        private boolean marketingOptIn;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StageHistoryEntry {
        private String stage;
        private String stageLabel;
        private String note;
        private String changedByName;
        private LocalDateTime createdAt;
    }

    @Getter @Setter
    public static class UpdateStageRequest {
        @NotBlank
        private String stage;
        private String note;
    }

    @Getter @Setter
    public static class UpdateCourierRequest {
        private String courierProvider;
        private String courierTrackingNumber;
    }

    @Getter @Setter
    public static class AssignEditorRequest {
        @NotBlank
        private String editorId;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CalendarEvent {
        private String id;
        private String bookingNumber;
        private String title;
        private LocalDate date;
        private LocalTime startTime;
        private LocalTime endTime;
        private String status;
        private String type;
        private String shootCategory;
        private String clientName;
        private String photographerName;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AvailabilityRequest {
        @NotNull
        private LocalDate date;
        private String photographerId;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SlotAvailability {
        private LocalTime startTime;
        private LocalTime endTime;
        private boolean available;
        private String bookedBy;
        private String closedReason;
    }

    @Getter @Setter
    public static class BlockDatesRequest {
        @NotNull
        private List<LocalDate> dates;
        private String reason;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BlockedDateResponse {
        private String id;
        private LocalDate date;
        private String reason;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PickupReminderResult {
        private boolean whatsappSent;
        private boolean emailSent;
        private int reminderCount;
        private BookingResponse booking;
    }
}
