package com.cameraman.billing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class BillingDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateInvoiceRequest {
        @NotBlank private String invoiceType; // ONLINE, OFFLINE
        private String billingType;
        private String bookingId;
        private String clientId;
        @NotBlank private String clientName;
        private String clientMobile;
        @Email private String clientEmail;
        private String clientAddress;
        private String clientGstin;
        private LocalDate invoiceDate;
        private LocalDate dueDate;
        private BigDecimal discountAmount;
        private BigDecimal discountPercent;
        private String notes;
        private String termsConditions;
        private String sizeFormat;
        /** INTRA_STATE (default: 9% CGST + 9% SGST), INTER_STATE (18% IGST),
         *  or EXEMPT (no tax). Null/blank behaves as INTRA_STATE, matching
         *  the pre-existing default rates on the Invoice entity. */
        private String taxType;
        @NotEmpty private List<InvoiceItemRequest> items;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InvoiceItemRequest {
        @NotBlank private String description;
        private String itemType;
        @Min(1) private int quantity;
        @NotNull private BigDecimal unitPrice;
        private BigDecimal discountPercent;
        private String hsnCode;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PaymentRequest {
        @NotNull @DecimalMin("0.01") private BigDecimal amount;
        private String paymentMethod;
        private String transactionId;
        private String notes;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InvoiceResponse {
        private String id;
        private String invoiceNumber;
        private String invoiceType;
        private String billingType;
        private String bookingId;
        private String clientId;
        private String clientName;
        private String clientMobile;
        private String clientEmail;
        private String clientAddress;
        private LocalDate invoiceDate;
        private LocalDate dueDate;
        private BigDecimal subtotal;
        private BigDecimal discountAmount;
        private BigDecimal taxableAmount;
        private BigDecimal cgstAmount;
        private BigDecimal sgstAmount;
        private BigDecimal igstAmount;
        private BigDecimal totalTax;
        private BigDecimal totalAmount;
        private BigDecimal amountPaid;
        private BigDecimal balanceDue;
        private String status;
        private String notes;
        private String pdfUrl;
        private boolean whatsappSent;
        private boolean emailSent;
        private String sizeFormat;
        private String generatedByName;
        /** Derived from the invoice's stored cgst/sgst/igst rates — not a
         *  separately stored column — so existing rows compute a correct
         *  value with no migration needed: INTER_STATE if igstRate > 0,
         *  EXEMPT if cgst/sgst/igst are all 0, INTRA_STATE otherwise. */
        private String taxType;
        private List<InvoiceItemResponse> items;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InvoiceItemResponse {
        private String id;
        private String description;
        private String itemType;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal discountPercent;
        private BigDecimal lineTotal;
        private String hsnCode;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RevenueSummary {
        private BigDecimal totalRevenue;
        private BigDecimal outstandingBalance;
        private long totalInvoices;
        private long paidCount;
        private long pendingCount;
        private LocalDate fromDate;
        private LocalDate toDate;
    }
}
