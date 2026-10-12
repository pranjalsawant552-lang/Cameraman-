package com.cameraman.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateOrderRequest {
        private String invoiceId;
        private String bookingId;
        private String clientId;
        private String clientName;
        @NotNull @DecimalMin("1.00") private BigDecimal amount;
        @NotBlank private String paymentType; // ADVANCE, PARTIAL, FULL
        private String notes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VerifyPaymentRequest {
        @NotBlank private String razorpayOrderId;
        @NotBlank private String razorpayPaymentId;
        @NotBlank private String razorpaySignature;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ManualPaymentRequest {
        private String invoiceId;
        private String bookingId;
        private String clientId;
        private String clientName;
        @NotNull @DecimalMin("0.01") private BigDecimal amount;
        @NotBlank private String paymentMethod; // CASH, UPI, BANK_TRANSFER, CHEQUE
        @NotBlank private String paymentType;
        private String transactionId;
        private String transactionReference;
        private String notes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RefundRequest {
        @NotNull @DecimalMin("0.01") private BigDecimal amount;
        @NotBlank private String reason;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RazorpayOrderResponse {
        private String orderId;
        private BigDecimal amount;
        private String currency;
        private String keyId;
        private String paymentId;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaymentResponse {
        private String id;
        private String invoiceId;
        private String bookingId;
        private String clientId;
        private String clientName;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String status;
        private String paymentType;
        private String razorpayOrderId;
        private String razorpayPaymentId;
        private String transactionId;
        private LocalDateTime paymentDate;
        private String notes;
        private BigDecimal refundedAmount;
        private LocalDateTime refundedAt;
        private LocalDateTime createdAt;
    }
}
