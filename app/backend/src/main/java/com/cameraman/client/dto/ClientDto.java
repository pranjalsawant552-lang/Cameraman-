package com.cameraman.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ClientDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        @NotBlank @Size(max = 60)
        private String firstName;
        @Size(max = 60)
        private String lastName;
        @Email @Size(max = 150)
        private String email;
        @Pattern(regexp = "^$|^[0-9+\\- ]{7,15}$", message = "Enter a valid mobile number")
        private String mobile;
        @Size(max = 2000)
        private String address;
        @Size(max = 4000)
        private String notes;
        private Boolean marketingOptIn;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BookingSummary {
        private String id;
        private String bookingNumber;
        private LocalDate bookingDate;
        private String packageName;
        private String shootCategory;
        private String status;
        private String orderStage;
        private BigDecimal totalAmount;
        private BigDecimal balanceAmount;
        private String locationType;
        private String invoiceId;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InvoiceSummary {
        private String id;
        private String invoiceNumber;
        private LocalDate invoiceDate;
        private String status;
        private BigDecimal totalAmount;
        private BigDecimal amountPaid;
        private BigDecimal balanceDue;
        /** The booking / session / shoot this invoice belongs to (null for walk-in invoices). */
        private String sourceId;
        private String clientName;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GallerySummary {
        private String id;
        private String name;
        private String status;
        private int photoCount;
        private LocalDateTime publishedAt;
    }

    /** Everything the admin client drawer shows, in one call. */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VisitSummary {
        /** STUDIO_SESSION or OUTDOOR_SHOOT. */
        private String type;
        private String id;
        private String number;
        private LocalDate date;
        private String packageName;
        private String status;
        private BigDecimal totalAmount;
        private BigDecimal balanceAmount;
        private String invoiceId;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SelectionSummary {
        private String id;
        private String galleryId;
        private String galleryName;
        private String status;
        private int selectedCount;
        private Integer selectionLimit;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Overview {
        private String id;
        private String firstName;
        private String lastName;
        private String email;
        private String mobile;
        private String address;
        private String notes;
        private String status;
        private boolean emailVerified;
        private boolean mobileVerified;
        private boolean marketingOptIn;
        private LocalDateTime createdAt;
        private LocalDateTime lastLoginAt;

        private long bookingCount;
        private long completedBookings;
        private long cancelledBookings;
        private BigDecimal totalBilled;
        private BigDecimal totalPaid;
        private BigDecimal balanceDue;
        private LocalDate lastBookingDate;

        private List<BookingSummary> recentBookings;
        private List<InvoiceSummary> recentInvoices;
        private List<GallerySummary> galleries;
        /** Indoor studio sessions and outdoor shoots (these are separate records from bookings). */
        private List<VisitSummary> studioSessionsAndShoots;
        private List<SelectionSummary> selections;
    }
}
