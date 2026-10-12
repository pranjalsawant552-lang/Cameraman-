package com.cameraman.payment.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_invoice", columnList = "invoice_id"),
    @Index(name = "idx_payment_razorpay", columnList = "razorpay_order_id"),
    @Index(name = "idx_payment_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "invoice_id")
    private String invoiceId;

    @Column(name = "booking_id")
    private String bookingId;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_name", length = 120)
    private String clientName;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 5)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    private PaymentType paymentType; // ADVANCE, PARTIAL, FULL

    // Razorpay fields
    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature", length = 300)
    private String razorpaySignature;

    // Manual payment fields
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "transaction_reference", length = 200)
    private String transactionReference;

    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "receipt_url")
    private String receiptUrl;

    @Column(name = "refund_id", length = 100)
    private String refundId;

    @Column(name = "refunded_amount", precision = 12, scale = 2)
    private BigDecimal refundedAmount;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
}
