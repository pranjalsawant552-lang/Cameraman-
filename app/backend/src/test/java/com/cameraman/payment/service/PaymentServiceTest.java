package com.cameraman.payment.service;

import com.cameraman.billing.dto.BillingDto;
import com.cameraman.billing.service.BillingService;
import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.common.exception.PaymentException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.notification.service.NotificationPublisher;
import com.cameraman.payment.domain.Payment;
import com.cameraman.payment.domain.PaymentMethod;
import com.cameraman.payment.domain.PaymentStatus;
import com.cameraman.payment.domain.PaymentType;
import com.cameraman.payment.dto.PaymentDto;
import com.cameraman.payment.repository.PaymentRepository;
import com.cameraman.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    private static final String SECRET = "test-secret";

    @Mock private PaymentRepository paymentRepository;
    @Mock private BillingService billingService;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private BookingRepository bookingRepository;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, billingService, notificationPublisher, bookingRepository);
        ReflectionTestUtils.setField(service, "razorpayKeySecret", SECRET);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static String sign(String orderId, String paymentId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder().id("p1").clientId("client-1").invoiceId("inv-1")
                .amount(new BigDecimal("1000.00")).status(status)
                .paymentMethod(PaymentMethod.RAZORPAY).paymentType(PaymentType.PARTIAL)
                .razorpayOrderId("order_1").build();
    }

    private PaymentDto.CreateOrderRequest orderRequest(String invoiceId, String bookingId, String amount) {
        PaymentDto.CreateOrderRequest r = new PaymentDto.CreateOrderRequest();
        r.setInvoiceId(invoiceId);
        r.setBookingId(bookingId);
        r.setAmount(new BigDecimal(amount));
        r.setPaymentType("FULL");
        r.setClientId("spoofed-user");
        return r;
    }

    private BillingDto.InvoiceResponse invoice(String clientId, String status, String balance) {
        return BillingDto.InvoiceResponse.builder().id("inv-1").clientId(clientId).clientName("Asha")
                .status(status).balanceDue(new BigDecimal(balance)).build();
    }

    // ───────── amount conversion ─────────

    @Test
    void toPaise_roundsHalfUp() {
        assertThat(PaymentService.toPaise(new BigDecimal("10.005"))).isEqualTo(1001L);
        assertThat(PaymentService.toPaise(new BigDecimal("499.99"))).isEqualTo(49999L);
        assertThat(PaymentService.toPaise(new BigDecimal("1"))).isEqualTo(100L);
    }

    // ───────── client order authorization ─────────

    @Test
    void clientOrder_identityComesFromJwt_notFromBody() {
        when(billingService.getById("inv-1")).thenReturn(invoice("client-1", "SENT", "5000"));
        PaymentDto.CreateOrderRequest req = orderRequest("inv-1", null, "1000");
        service.authorizeClientOrder(req, "client-1");
        assertThat(req.getClientId()).isEqualTo("client-1");
    }

    @Test
    void clientOrder_forSomeoneElsesInvoice_looksLikeNotFound() {
        when(billingService.getById("inv-1")).thenReturn(invoice("other", "SENT", "5000"));
        assertThatThrownBy(() -> service.authorizeClientOrder(orderRequest("inv-1", null, "1000"), "client-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clientOrder_forDraftInvoice_looksLikeNotFound() {
        when(billingService.getById("inv-1")).thenReturn(invoice("client-1", "DRAFT", "5000"));
        assertThatThrownBy(() -> service.authorizeClientOrder(orderRequest("inv-1", null, "1000"), "client-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clientOrder_forPaidOrCancelledInvoice_isRejected() {
        when(billingService.getById("inv-1")).thenReturn(invoice("client-1", "PAID", "0"));
        assertThatThrownBy(() -> service.authorizeClientOrder(orderRequest("inv-1", null, "1"), "client-1"))
                .isInstanceOf(PaymentException.class);
        when(billingService.getById("inv-1")).thenReturn(invoice("client-1", "CANCELLED", "100"));
        assertThatThrownBy(() -> service.authorizeClientOrder(orderRequest("inv-1", null, "1"), "client-1"))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void clientOrder_overBalanceDue_isRejected() {
        when(billingService.getById("inv-1")).thenReturn(invoice("client-1", "SENT", "500"));
        assertThatThrownBy(() -> service.authorizeClientOrder(orderRequest("inv-1", null, "500.01"), "client-1"))
                .isInstanceOf(PaymentException.class).hasMessageContaining("exceeds");
    }

    @Test
    void clientOrder_withNeitherInvoiceNorBooking_isRejected() {
        assertThatThrownBy(() -> service.authorizeClientOrder(orderRequest(null, null, "100"), "client-1"))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void clientOrder_forSomeoneElsesBooking_looksLikeNotFound() {
        User other = new User(); other.setId("other");
        Booking b = Booking.builder().id("b1").client(other).balanceAmount(new BigDecimal("900")).build();
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(b));
        assertThatThrownBy(() -> service.authorizeClientOrder(orderRequest(null, "b1", "100"), "client-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clientOrder_bookingAdvanceOverBalance_isRejected() {
        User me = new User(); me.setId("client-1");
        Booking b = Booking.builder().id("b1").client(me).balanceAmount(new BigDecimal("900")).build();
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(b));
        assertThatThrownBy(() -> service.authorizeClientOrder(orderRequest(null, "b1", "901"), "client-1"))
                .isInstanceOf(PaymentException.class);
    }

    // ───────── verify: idempotency + ownership ─────────

    private PaymentDto.VerifyPaymentRequest verifyRequest() throws Exception {
        PaymentDto.VerifyPaymentRequest r = new PaymentDto.VerifyPaymentRequest();
        r.setRazorpayOrderId("order_1");
        r.setRazorpayPaymentId("pay_1");
        r.setRazorpaySignature(sign("order_1", "pay_1"));
        return r;
    }

    @Test
    void verify_firstCall_appliesPaymentToInvoiceOnce() throws Exception {
        Payment p = payment(PaymentStatus.PENDING);
        when(paymentRepository.findByRazorpayOrderIdForUpdate("order_1")).thenReturn(Optional.of(p));

        PaymentDto.PaymentResponse res = service.verifyAndCapturePayment(verifyRequest(), "client-1", false);

        assertThat(res.getStatus()).isEqualTo("SUCCESS");
        verify(billingService, times(1)).recordPayment(eq("inv-1"), any());
    }

    @Test
    void verify_replay_doesNotApplyTwice() throws Exception {
        Payment p = payment(PaymentStatus.SUCCESS);
        when(paymentRepository.findByRazorpayOrderIdForUpdate("order_1")).thenReturn(Optional.of(p));

        PaymentDto.PaymentResponse res = service.verifyAndCapturePayment(verifyRequest(), "client-1", false);

        assertThat(res.getStatus()).isEqualTo("SUCCESS");
        verify(billingService, never()).recordPayment(anyString(), any());
    }

    @Test
    void verify_byAnotherClient_looksLikeNotFound() throws Exception {
        Payment p = payment(PaymentStatus.PENDING);
        when(paymentRepository.findByRazorpayOrderIdForUpdate("order_1")).thenReturn(Optional.of(p));
        PaymentDto.VerifyPaymentRequest req = verifyRequest();

        assertThatThrownBy(() -> service.verifyAndCapturePayment(req, "intruder", false))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(billingService, never()).recordPayment(anyString(), any());
    }

    @Test
    void verify_badSignature_isRejected() {
        PaymentDto.VerifyPaymentRequest r = new PaymentDto.VerifyPaymentRequest();
        r.setRazorpayOrderId("order_1");
        r.setRazorpayPaymentId("pay_1");
        r.setRazorpaySignature("deadbeef");
        assertThatThrownBy(() -> service.verifyAndCapturePayment(r, "client-1", false))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void verify_bookingAdvance_movesAmountFromBalanceToAdvance() throws Exception {
        Payment p = payment(PaymentStatus.PENDING);
        p.setInvoiceId(null);
        p.setBookingId("b1");
        p.setAmount(new BigDecimal("300"));
        Booking b = Booking.builder().id("b1").bookingNumber("BK1").totalAmount(new BigDecimal("1000"))
                .advanceAmount(BigDecimal.ZERO).balanceAmount(new BigDecimal("1000")).build();
        when(paymentRepository.findByRazorpayOrderIdForUpdate("order_1")).thenReturn(Optional.of(p));
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(b));

        service.verifyAndCapturePayment(verifyRequest(), "client-1", false);

        assertThat(b.getAdvanceAmount()).isEqualByComparingTo("300");
        assertThat(b.getBalanceAmount()).isEqualByComparingTo("700");
    }

    // ───────── refunds ─────────

    private PaymentDto.RefundRequest refund(String amount) {
        PaymentDto.RefundRequest r = new PaymentDto.RefundRequest();
        r.setAmount(new BigDecimal(amount));
        r.setReason("customer request");
        return r;
    }

    @Test
    void refund_partialThenRemainder_tracksCumulativeAndStatus() {
        Payment p = payment(PaymentStatus.SUCCESS);
        when(paymentRepository.findById("p1")).thenReturn(Optional.of(p));

        PaymentDto.PaymentResponse first = service.processRefund("p1", refund("400"));
        assertThat(first.getStatus()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(first.getRefundedAmount()).isEqualByComparingTo("400");
        verify(billingService).recordRefund("inv-1", new BigDecimal("400"));

        PaymentDto.PaymentResponse second = service.processRefund("p1", refund("600"));
        assertThat(second.getStatus()).isEqualTo("REFUNDED");
        assertThat(second.getRefundedAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void refund_exceedingRemainingBalance_isRejected() {
        Payment p = payment(PaymentStatus.PARTIALLY_REFUNDED);
        p.setRefundedAmount(new BigDecimal("400"));
        when(paymentRepository.findById("p1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.processRefund("p1", refund("700")))
                .isInstanceOf(PaymentException.class);
        verify(billingService, never()).recordRefund(anyString(), any());
    }

    @Test
    void refund_ofPendingPayment_isRejected() {
        when(paymentRepository.findById("p1")).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));
        assertThatThrownBy(() -> service.processRefund("p1", refund("10")))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void refund_ofFullyRefundedPayment_isRejected() {
        Payment p = payment(PaymentStatus.REFUNDED);
        p.setRefundedAmount(new BigDecimal("1000"));
        when(paymentRepository.findById("p1")).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.processRefund("p1", refund("1")))
                .isInstanceOf(PaymentException.class);
    }
}
