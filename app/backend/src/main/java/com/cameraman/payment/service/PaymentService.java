package com.cameraman.payment.service;

import com.cameraman.common.exception.PaymentException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.payment.domain.*;
import com.cameraman.payment.dto.PaymentDto;
import com.cameraman.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final com.cameraman.billing.service.BillingService billingService;
    private final com.cameraman.notification.service.NotificationPublisher notificationPublisher;
    private final com.cameraman.booking.repository.BookingRepository bookingRepository;

    private static final java.util.List<String> STAFF_NOTIFY_ROLES =
            java.util.List.of("ADMIN", "SUPER_ADMIN", "MANAGER", "ACCOUNTANT");

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    private RazorpayClient razorpayClient;

    public PaymentService(PaymentRepository paymentRepository, com.cameraman.billing.service.BillingService billingService,
                           com.cameraman.notification.service.NotificationPublisher notificationPublisher,
                           com.cameraman.booking.repository.BookingRepository bookingRepository) {
        this.paymentRepository = paymentRepository;
        this.billingService = billingService;
        this.notificationPublisher = notificationPublisher;
        this.bookingRepository = bookingRepository;
    }

    @PostConstruct
    public void init() {
        if (razorpayKeyId != null && !razorpayKeyId.isBlank()) {
            try {
                razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            } catch (RazorpayException e) {
                log.warn("Razorpay client initialization failed: {}", e.getMessage());
            }
        }
    }

    /** Verifies the webhook's HMAC signature against the configured webhook
     *  secret (Razorpay dashboard → Settings → Webhooks — a different
     *  secret from the API key/secret pair) and, on a payment.captured or
     *  order.paid event, marks the matching payment SUCCESS and reconciles
     *  it against its invoice — the same thing verifyAndCapturePayment does
     *  for the synchronous checkout flow. This exists for payment methods
     *  that complete asynchronously (e.g. a UPI collect request the client
     *  approves in their banking app after the checkout tab is closed),
     *  where the synchronous /razorpay/verify call the frontend makes right
     *  after checkout may never happen. Idempotent: replays of the same
     *  event (Razorpay retries webhooks that don't 200 quickly) are
     *  no-ops if the payment is already SUCCESS.
     *
     *  Previously this endpoint accepted any request, logged it, and
     *  returned 200 without checking the signature or touching a single
     *  row — meaning anyone who found the URL could have POSTed a fake
     *  "payment succeeded" event with no way to tell it apart from a real
     *  one. */
    public void handleWebhook(String signature, String payload) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Razorpay webhook received but RAZORPAY_WEBHOOK_SECRET isn't configured — ignoring");
            return;
        }
        if (signature == null || signature.isBlank()) {
            log.warn("Razorpay webhook received with no signature header — ignoring");
            return;
        }
        try {
            Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            log.warn("Razorpay webhook signature verification failed: {}", e.getMessage());
            return;
        }

        JSONObject event = new JSONObject(payload);
        String eventType = event.optString("event", "");
        if (!"payment.captured".equals(eventType) && !"order.paid".equals(eventType)) {
            log.debug("Ignoring Razorpay webhook event type: {}", eventType);
            return;
        }

        try {
            JSONObject paymentEntity = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
            String orderId = paymentEntity.getString("order_id");
            String razorpayPaymentId = paymentEntity.getString("id");

            Payment payment = paymentRepository.findByRazorpayOrderIdForUpdate(orderId).orElse(null);
            if (payment == null) {
                log.warn("Razorpay webhook for unknown order {}", orderId);
                return;
            }
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                log.debug("Payment {} already SUCCESS — webhook is a duplicate/retry, ignoring", payment.getId());
                return;
            }

            payment.setRazorpayPaymentId(razorpayPaymentId);
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaymentDate(LocalDateTime.now());
            payment = paymentRepository.save(payment);
            applyPaymentToInvoice(payment);
        } catch (Exception e) {
            log.error("Failed to process Razorpay webhook: {}", e.getMessage(), e);
        }
    }

    public PaymentDto.RazorpayOrderResponse createRazorpayOrder(PaymentDto.CreateOrderRequest request) {
        return createRazorpayOrder(request, null, true);
    }

    /**
     * @param actorId the authenticated user's id (from the JWT)
     * @param isStaff staff may create orders for any client/invoice; a client may only pay their
     *                own finalized invoice or booking, and never more than what is still owed.
     */
    public PaymentDto.RazorpayOrderResponse createRazorpayOrder(PaymentDto.CreateOrderRequest request, String actorId, boolean isStaff) {
        if (razorpayClient == null) {
            throw new PaymentException("Payment gateway not configured");
        }
        if (!isStaff) {
            authorizeClientOrder(request, actorId);
        }
        try {
            JSONObject orderReq = new JSONObject();
            // Razorpay expects amount in paise
            orderReq.put("amount", toPaise(request.getAmount()));
            orderReq.put("currency", "INR");
            orderReq.put("receipt", "rcpt_" + System.currentTimeMillis());
            orderReq.put("payment_capture", 1);

            JSONObject notes = new JSONObject();
            if (request.getInvoiceId() != null) notes.put("invoice_id", request.getInvoiceId());
            if (request.getBookingId() != null) notes.put("booking_id", request.getBookingId());
            if (request.getClientName() != null) notes.put("client_name", request.getClientName());
            orderReq.put("notes", notes);

            Order razorpayOrder = razorpayClient.orders.create(orderReq);

            // Save pending payment
            Payment payment = Payment.builder()
                    .invoiceId(request.getInvoiceId())
                    .bookingId(request.getBookingId())
                    .clientId(request.getClientId())
                    .clientName(request.getClientName())
                    .amount(request.getAmount())
                    .paymentMethod(PaymentMethod.RAZORPAY)
                    .paymentType(PaymentType.valueOf(request.getPaymentType()))
                    .status(PaymentStatus.PENDING)
                    .razorpayOrderId(razorpayOrder.get("id"))
                    .notes(request.getNotes())
                    .build();
            paymentRepository.save(payment);

            return PaymentDto.RazorpayOrderResponse.builder()
                    .orderId(razorpayOrder.get("id"))
                    .amount(request.getAmount())
                    .currency("INR")
                    .keyId(razorpayKeyId)
                    .paymentId(payment.getId())
                    .build();

        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage(), e);
            throw new PaymentException("Failed to create payment order: " + e.getMessage());
        }
    }

    static long toPaise(BigDecimal rupees) {
        return rupees.multiply(new BigDecimal("100")).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    /** Client-initiated orders: identity comes from the JWT, the target must be theirs, and the amount
     *  can never exceed what is still outstanding. Mutates the request so the stored payment is attributed
     *  to the real user regardless of what the browser sent. */
    void authorizeClientOrder(PaymentDto.CreateOrderRequest request, String actorId) {
        if (actorId == null) throw new PaymentException("Not authenticated");
        boolean hasInvoice = request.getInvoiceId() != null && !request.getInvoiceId().isBlank();
        boolean hasBooking = request.getBookingId() != null && !request.getBookingId().isBlank();
        if (!hasInvoice && !hasBooking) {
            throw new PaymentException("A payment must be for an invoice or a booking");
        }
        request.setClientId(actorId);

        if (hasInvoice) {
            var invoice = billingService.getById(request.getInvoiceId());
            if (!actorId.equals(invoice.getClientId()) || "DRAFT".equals(invoice.getStatus())) {
                throw new ResourceNotFoundException("Invoice", "id", request.getInvoiceId());
            }
            if ("CANCELLED".equals(invoice.getStatus()) || "PAID".equals(invoice.getStatus())) {
                throw new PaymentException("This invoice is not payable");
            }
            if (request.getAmount().compareTo(invoice.getBalanceDue()) > 0) {
                throw new PaymentException("Amount exceeds the balance due on this invoice");
            }
            if (request.getClientName() == null) request.setClientName(invoice.getClientName());
            return;
        }

        var booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", request.getBookingId()));
        if (booking.getClient() == null || !actorId.equals(booking.getClient().getId())) {
            throw new ResourceNotFoundException("Booking", "id", request.getBookingId());
        }
        if (request.getAmount().compareTo(booking.getBalanceAmount()) > 0) {
            throw new PaymentException("Amount exceeds the balance due on this booking");
        }
        if (request.getClientName() == null) request.setClientName(booking.getClientName());
    }

    public PaymentDto.PaymentResponse verifyAndCapturePayment(PaymentDto.VerifyPaymentRequest request) {
        return verifyAndCapturePayment(request, null, true);
    }

    public PaymentDto.PaymentResponse verifyAndCapturePayment(PaymentDto.VerifyPaymentRequest request, String actorId, boolean isStaff) {
        // Verify signature
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.getRazorpayOrderId());
            attributes.put("razorpay_payment_id", request.getRazorpayPaymentId());
            attributes.put("razorpay_signature", request.getRazorpaySignature());
            Utils.verifyPaymentSignature(attributes, razorpayKeySecret);
        } catch (RazorpayException e) {
            throw new PaymentException("Payment signature verification failed");
        }

        Payment payment = paymentRepository.findByRazorpayOrderIdForUpdate(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (!isStaff && (payment.getClientId() == null || !payment.getClientId().equals(actorId))) {
            throw new ResourceNotFoundException("Payment not found");
        }
        // Idempotent: a repeat call (double-click, retry, or the webhook arriving first) must not
        // apply the same payment to the invoice twice.
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return toResponse(payment);
        }

        payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
        payment.setRazorpaySignature(request.getRazorpaySignature());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaymentDate(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        applyPaymentToInvoice(payment);

        return toResponse(payment);
    }

    /**
     * Applies a successful payment to its linked invoice's balance — this is the
     * step that was missing entirely: payments were being recorded in this module's
     * own Payment table but never actually touched the Invoice, so a client could
     * pay in full via Razorpay and the invoice would still show the old balance,
     * the "fully paid" auto-unlock-downloads trigger would never fire, and staff
     * looking at the invoice would have no idea it was paid. Delegates to
     * BillingService.recordPayment so this goes through the exact same path (and
     * gets the exact same auto-unlock-downloads behavior) as a payment recorded
     * directly on the invoice.
     */
    private void applyPaymentToInvoice(Payment payment) {
        applyPaymentToInvoice(payment, false);
    }

    /**
     * @param propagate true for staff-entered manual payments: if the invoice rejects the payment
     *                  (draft, cancelled, overpayment) the whole request fails and nothing is saved.
     *                  Gateway payments pass false: the money has already moved, so a reconcile
     *                  problem is logged for follow-up instead of discarding the payment record.
     */
    private void applyPaymentToInvoice(Payment payment, boolean propagate) {
        // Every booking now has an invoice; a payment that only names the booking belongs on it,
        // otherwise booking and invoice would each keep a different idea of what is paid.
        if ((payment.getInvoiceId() == null || payment.getInvoiceId().isBlank())
                && payment.getBookingId() != null && !payment.getBookingId().isBlank()) {
            billingService.findInvoiceIdForSource(payment.getBookingId()).ifPresent(payment::setInvoiceId);
        }
        if (payment.getInvoiceId() == null || payment.getInvoiceId().isBlank()) {
            applyPaymentToBooking(payment);
            return;
        }
        try {
            var request = com.cameraman.billing.dto.BillingDto.PaymentRequest.builder()
                    .amount(payment.getAmount())
                    .paymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : "RAZORPAY")
                    .transactionId(payment.getRazorpayPaymentId())
                    .notes("Auto-recorded from payment " + payment.getId())
                    .build();
            billingService.recordPayment(payment.getInvoiceId(), request);
        } catch (Exception e) {
            if (propagate) {
                throw e instanceof RuntimeException re ? re : new PaymentException(e.getMessage());
            }
            log.error("Payment {} succeeded but failed to reconcile against invoice {}: {}",
                    payment.getId(), payment.getInvoiceId(), e.getMessage());
            return;
        }

        notificationPublisher.notifyRoles(STAFF_NOTIFY_ROLES, "PAYMENT_RECEIVED",
                "Payment received: \u20b9" + payment.getAmount().toPlainString(),
                (payment.getClientName() != null ? payment.getClientName() : "A client") +
                        " paid \u20b9" + payment.getAmount().toPlainString() + " against invoice " + payment.getInvoiceId(),
                "Payment", payment.getId());
    }

    /** Advance/deposit paid against a booking with no invoice yet: move it from "balance" to "advance". */
    void applyPaymentToBooking(Payment payment) {
        if (payment.getBookingId() == null || payment.getBookingId().isBlank()) {
            log.warn("Payment {} has no invoice or booking — nothing to reconcile", payment.getId());
            return;
        }
        bookingRepository.findById(payment.getBookingId()).ifPresent(b -> {
            BigDecimal total = b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal advance = (b.getAdvanceAmount() != null ? b.getAdvanceAmount() : BigDecimal.ZERO).add(payment.getAmount());
            if (advance.compareTo(total) > 0) advance = total;
            b.setAdvanceAmount(advance);
            b.setBalanceAmount(total.subtract(advance));
            bookingRepository.save(b);
            notificationPublisher.notifyRoles(STAFF_NOTIFY_ROLES, "PAYMENT_RECEIVED",
                    "Advance received: \u20b9" + payment.getAmount().toPlainString(),
                    (b.getClientName() != null ? b.getClientName() : "A client") + " paid an advance for booking " + b.getBookingNumber(),
                    "Booking", b.getId());
        });
    }

    private void reverseAppliedPayment(Payment payment, BigDecimal amount) {
        if (payment.getInvoiceId() != null && !payment.getInvoiceId().isBlank()) {
            billingService.recordRefund(payment.getInvoiceId(), amount);
        } else if (payment.getBookingId() != null && !payment.getBookingId().isBlank()) {
            bookingRepository.findById(payment.getBookingId()).ifPresent(b -> {
                BigDecimal advance = (b.getAdvanceAmount() != null ? b.getAdvanceAmount() : BigDecimal.ZERO).subtract(amount).max(BigDecimal.ZERO);
                b.setAdvanceAmount(advance);
                b.setBalanceAmount(b.getTotalAmount().subtract(advance));
                bookingRepository.save(b);
            });
        }
    }

    public PaymentDto.PaymentResponse recordManualPayment(PaymentDto.ManualPaymentRequest request) {
        Payment payment = Payment.builder()
                .invoiceId(request.getInvoiceId())
                .bookingId(request.getBookingId())
                .clientId(request.getClientId())
                .clientName(request.getClientName())
                .amount(request.getAmount())
                .paymentMethod(PaymentMethod.valueOf(request.getPaymentMethod()))
                .paymentType(PaymentType.valueOf(request.getPaymentType()))
                .status(PaymentStatus.SUCCESS)
                .transactionId(request.getTransactionId())
                .transactionReference(request.getTransactionReference())
                .paymentDate(LocalDateTime.now())
                .notes(request.getNotes())
                .build();
        payment = paymentRepository.save(payment);
        applyPaymentToInvoice(payment, true);
        return toResponse(payment);
    }

    public PaymentDto.PaymentResponse processRefund(String paymentId, PaymentDto.RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));

        if (payment.getStatus() != PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new PaymentException("Can only refund successful payments");
        }
        BigDecimal alreadyRefunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount() : BigDecimal.ZERO;
        BigDecimal refundable = payment.getAmount().subtract(alreadyRefunded);
        if (request.getAmount().compareTo(refundable) > 0) {
            throw new PaymentException("Refund amount cannot exceed the refundable balance of " + refundable.toPlainString());
        }

        try {
            if (payment.getRazorpayPaymentId() != null && razorpayClient != null) {
                JSONObject refundReq = new JSONObject();
                refundReq.put("amount", toPaise(request.getAmount()));
                refundReq.put("notes", new JSONObject().put("reason", request.getReason()));
                com.razorpay.Refund refund = razorpayClient.payments.refund(payment.getRazorpayPaymentId(), refundReq);
                payment.setRefundId(refund.get("id"));
            }
        } catch (RazorpayException e) {
            log.error("Razorpay refund failed: {}", e.getMessage());
            throw new PaymentException("Refund failed: " + e.getMessage());
        }

        // Keep the invoice (or booking advance) in step with the money that actually left.
        reverseAppliedPayment(payment, request.getAmount());

        BigDecimal totalRefunded = alreadyRefunded.add(request.getAmount());
        payment.setRefundedAmount(totalRefunded);
        payment.setRefundReason(request.getReason());
        payment.setRefundedAt(LocalDateTime.now());
        payment.setStatus(totalRefunded.compareTo(payment.getAmount()) == 0
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentDto.PaymentResponse getById(String id) {
        return toResponse(paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id)));
    }

    @Transactional(readOnly = true)
    public com.cameraman.common.response.PagedResponse<PaymentDto.PaymentResponse> list(
            String search, String status, Pageable pageable) {
        org.springframework.data.domain.Page<Payment> page;
        if (search != null && !search.isBlank()) {
            page = paymentRepository.searchPayments(search, pageable);
        } else if (status != null && !status.isBlank()) {
            page = paymentRepository.findByStatus(PaymentStatus.valueOf(status), pageable);
        } else {
            page = paymentRepository.findAll(pageable);
        }
        return com.cameraman.common.response.PagedResponse.of(page.map(this::toResponse));
    }

    private PaymentDto.PaymentResponse toResponse(Payment p) {
        return PaymentDto.PaymentResponse.builder()
                .id(p.getId()).invoiceId(p.getInvoiceId()).bookingId(p.getBookingId())
                .clientId(p.getClientId()).clientName(p.getClientName())
                .amount(p.getAmount()).currency(p.getCurrency())
                .paymentMethod(p.getPaymentMethod() != null ? p.getPaymentMethod().name() : null)
                .status(p.getStatus().name())
                .paymentType(p.getPaymentType() != null ? p.getPaymentType().name() : null)
                .razorpayOrderId(p.getRazorpayOrderId()).razorpayPaymentId(p.getRazorpayPaymentId())
                .transactionId(p.getTransactionId()).paymentDate(p.getPaymentDate())
                .notes(p.getNotes()).refundedAmount(p.getRefundedAmount())
                .refundedAt(p.getRefundedAt()).createdAt(p.getCreatedAt())
                .build();
    }
}
