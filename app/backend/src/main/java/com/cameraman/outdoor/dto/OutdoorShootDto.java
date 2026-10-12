package com.cameraman.outdoor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class OutdoorShootDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        private String clientId;
        @NotBlank private String clientName;
        private String clientMobile;
        @Email private String clientEmail;
        @NotBlank private String category;
        private String packageId;
        private String packageName;
        @NotNull private LocalDate shootDate;
        private LocalDate endDate;
        @NotNull private LocalTime startTime;
        private LocalTime endTime;
        private String venueName;
        private String venueAddress;
        private String venueCity;
        private String venuePincode;
        private Integer travelDistanceKm;
        private BigDecimal travelCost;
        private boolean weatherDependent;
        private String backupPlanNotes;
        private String equipmentChecklist;
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
        private String category;
        private String packageId;
        private String packageName;
        private LocalDate shootDate;
        private LocalDate endDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String venueName;
        private String venueAddress;
        private String venueCity;
        private String venuePincode;
        private Integer travelDistanceKm;
        private BigDecimal travelCost;
        private Boolean weatherDependent;
        private String backupPlanNotes;
        private String equipmentChecklist;
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
        private LocalDate newEndDate;
        @NotNull private LocalTime newStartTime;
        private LocalTime newEndTime;
        private String reason;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CancelRequest {
        private String reason;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OutdoorShootResponse {
        private String id;
        private String shootNumber;
        private String clientId;
        private String clientName;
        private String clientMobile;
        private String clientEmail;
        private String category;
        private String packageId;
        private String packageName;
        private LocalDate shootDate;
        private LocalDate endDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String venueName;
        private String venueAddress;
        private String venueCity;
        private String venuePincode;
        private Integer travelDistanceKm;
        private BigDecimal travelCost;
        private boolean weatherDependent;
        private String backupPlanNotes;
        private String equipmentChecklist;
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
