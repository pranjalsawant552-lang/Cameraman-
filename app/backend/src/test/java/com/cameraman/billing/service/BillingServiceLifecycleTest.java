package com.cameraman.billing.service;

import com.cameraman.billing.domain.Invoice;
import com.cameraman.billing.domain.InvoiceStatus;
import com.cameraman.billing.domain.InvoiceType;
import com.cameraman.billing.dto.BillingDto;
import com.cameraman.billing.repository.InvoiceRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.gallery.repository.GalleryRepository;
import com.cameraman.notification.service.EmailService;
import com.cameraman.settings.service.StudioSettingsService;
import com.cameraman.storage.service.StorageService;
import com.cameraman.user.repository.UserRepository;
import com.cameraman.whatsapp.service.WhatsAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Invoice status machine: DRAFT -> SENT -> PARTIALLY_PAID -> PAID, plus refund / cancel rules. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceLifecycleTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoicePdfService pdfService;
    @Mock private StorageService storageService;
    @Mock private EmailService emailService;
    @Mock private WhatsAppService whatsAppService;
    @Mock private StudioSettingsService settingsService;
    @Mock private GalleryRepository galleryRepository;
    @Mock private UserRepository userRepository;

    private BillingService service;

    @BeforeEach
    void setUp() {
        service = new BillingService(invoiceRepository, pdfService, storageService, emailService,
                whatsAppService, settingsService, galleryRepository, userRepository,
                null /* bookingRepository */, null /* studioSessionRepository */, null /* outdoorShootRepository */);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Invoice invoice(InvoiceStatus status, String total, String paid) {
        Invoice inv = Invoice.builder().id("i1").invoiceNumber("INV-1").invoiceType(InvoiceType.OFFLINE)
                .clientId("c1").clientName("Asha").clientMobile("9876543210").clientEmail("a@b.com")
                .invoiceDate(LocalDate.now()).status(status)
                .totalAmount(new BigDecimal(total)).amountPaid(new BigDecimal(paid))
                .balanceDue(new BigDecimal(total).subtract(new BigDecimal(paid))).build();
        when(invoiceRepository.findById("i1")).thenReturn(Optional.of(inv));
        return inv;
    }

    private BillingDto.PaymentRequest pay(String amount) {
        return BillingDto.PaymentRequest.builder().amount(new BigDecimal(amount)).paymentMethod("CASH").build();
    }

    // ───────── finalize / edit ─────────

    @Test
    void finalize_movesDraftToSent() {
        invoice(InvoiceStatus.DRAFT, "1000", "0");
        assertThat(service.finalizeInvoice("i1").getStatus()).isEqualTo("SENT");
    }

    @Test
    void finalize_nonDraft_isRejected() {
        invoice(InvoiceStatus.SENT, "1000", "0");
        assertThatThrownBy(() -> service.finalizeInvoice("i1")).isInstanceOf(BusinessException.class);
    }

    // ───────── payments ─────────

    @Test
    void payment_onDraft_isRejected() {
        invoice(InvoiceStatus.DRAFT, "1000", "0");
        assertThatThrownBy(() -> service.recordPayment("i1", pay("100"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void payment_onCancelled_isRejected() {
        invoice(InvoiceStatus.CANCELLED, "1000", "0");
        assertThatThrownBy(() -> service.recordPayment("i1", pay("100"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void partialPayment_setsPartiallyPaid_andFullSetsPaid() {
        invoice(InvoiceStatus.SENT, "1000", "0");
        BillingDto.InvoiceResponse partial = service.recordPayment("i1", pay("400"));
        assertThat(partial.getStatus()).isEqualTo("PARTIALLY_PAID");
        assertThat(partial.getBalanceDue()).isEqualByComparingTo("600");

        BillingDto.InvoiceResponse full = service.recordPayment("i1", pay("600"));
        assertThat(full.getStatus()).isEqualTo("PAID");
        assertThat(full.getBalanceDue()).isEqualByComparingTo("0");
    }

    @Test
    void overpayment_isRejected() {
        invoice(InvoiceStatus.SENT, "1000", "900");
        assertThatThrownBy(() -> service.recordPayment("i1", pay("100.01"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void zeroOrNegativePayment_isRejected() {
        invoice(InvoiceStatus.SENT, "1000", "0");
        assertThatThrownBy(() -> service.recordPayment("i1", pay("0"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.recordPayment("i1", pay("-5"))).isInstanceOf(BusinessException.class);
    }

    // ───────── cancel ─────────

    @Test
    void cancel_unpaidInvoice_succeeds() {
        invoice(InvoiceStatus.SENT, "1000", "0");
        assertThat(service.cancelInvoice("i1", "duplicate").getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_withPaymentsOnIt_isBlockedUntilRefunded() {
        invoice(InvoiceStatus.PARTIALLY_PAID, "1000", "300");
        assertThatThrownBy(() -> service.cancelInvoice("i1", "x"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Refund");
    }

    @Test
    void cancel_afterFullRefund_succeeds() {
        invoice(InvoiceStatus.PAID, "1000", "1000");
        assertThat(service.recordRefund("i1", new BigDecimal("1000")).getStatus()).isEqualTo("REFUNDED");
        assertThat(service.cancelInvoice("i1", "client withdrew").getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_alreadyCancelled_isRejected() {
        invoice(InvoiceStatus.CANCELLED, "1000", "0");
        assertThatThrownBy(() -> service.cancelInvoice("i1", "x")).isInstanceOf(BusinessException.class);
    }

    // ───────── refunds ─────────

    @Test
    void partialRefund_keepsPartiallyPaid_andRestoresBalance() {
        invoice(InvoiceStatus.PAID, "1000", "1000");
        BillingDto.InvoiceResponse res = service.recordRefund("i1", new BigDecimal("250"));
        assertThat(res.getStatus()).isEqualTo("PARTIALLY_PAID");
        assertThat(res.getAmountPaid()).isEqualByComparingTo("750");
        assertThat(res.getBalanceDue()).isEqualByComparingTo("250");
    }

    @Test
    void refund_moreThanPaid_isRejected() {
        invoice(InvoiceStatus.PARTIALLY_PAID, "1000", "300");
        assertThatThrownBy(() -> service.recordRefund("i1", new BigDecimal("300.01"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void refund_nonPositive_isRejected() {
        invoice(InvoiceStatus.PAID, "1000", "1000");
        assertThatThrownBy(() -> service.recordRefund("i1", BigDecimal.ZERO)).isInstanceOf(BusinessException.class);
    }

    // ───────── client visibility / sending ─────────

    @Test
    void myInvoices_excludesDrafts() {
        when(invoiceRepository.findByClientIdAndStatusNot(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        service.getMyInvoices("c1", PageRequest.of(0, 10));
        verify(invoiceRepository).findByClientIdAndStatusNot("c1", InvoiceStatus.DRAFT, PageRequest.of(0, 10));
    }

    @Test
    void sendWhatsApp_forDraft_isRejected() {
        invoice(InvoiceStatus.DRAFT, "1000", "0");
        assertThatThrownBy(() -> service.sendWhatsAppInvoice("i1")).isInstanceOf(BusinessException.class);
    }

    @Test
    void sendWhatsApp_beforePdfExists_givesFriendlyError() {
        invoice(InvoiceStatus.SENT, "1000", "0");
        assertThatThrownBy(() -> service.sendWhatsAppInvoice("i1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("PDF");
    }

    @Test
    void sendEmail_withoutAddress_isRejected() {
        Invoice inv = invoice(InvoiceStatus.SENT, "1000", "0");
        inv.setClientEmail(null);
        assertThatThrownBy(() -> service.sendEmailInvoice("i1")).isInstanceOf(BusinessException.class);
    }
}
