package com.cameraman.billing.service;

import com.cameraman.billing.domain.*;
import com.cameraman.billing.dto.BillingDto;
import com.cameraman.billing.repository.InvoiceRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.notification.service.EmailService;
import com.cameraman.settings.service.StudioSettingsService;
import com.cameraman.storage.service.StorageService;
import com.cameraman.whatsapp.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final InvoicePdfService pdfService;
    private final StorageService storageService;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final StudioSettingsService settingsService;
    private final com.cameraman.gallery.repository.GalleryRepository galleryRepository;
    private final com.cameraman.user.repository.UserRepository userRepository;
    private final com.cameraman.booking.repository.BookingRepository bookingRepository;
    private final com.cameraman.studio.repository.StudioSessionRepository studioSessionRepository;
    private final com.cameraman.outdoor.repository.OutdoorShootRepository outdoorShootRepository;

    public BillingDto.InvoiceResponse createInvoice(BillingDto.CreateInvoiceRequest request, String generatedByUserId) {
        String generatedByName = null;
        if (generatedByUserId != null) {
            generatedByName = userRepository.findById(generatedByUserId)
                    .map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
                    .orElse(null);
        }

        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber())
                .invoiceType(InvoiceType.valueOf(request.getInvoiceType()))
                .billingType(request.getBillingType())
                .bookingId(request.getBookingId())
                .clientId(request.getClientId())
                .clientName(request.getClientName())
                .clientMobile(request.getClientMobile())
                .clientEmail(request.getClientEmail())
                .clientAddress(request.getClientAddress())
                .clientGstin(request.getClientGstin())
                .invoiceDate(request.getInvoiceDate() != null ? request.getInvoiceDate() : LocalDate.now())
                .dueDate(request.getDueDate())
                .discountAmount(request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO)
                .discountPercent(request.getDiscountPercent() != null ? request.getDiscountPercent() : BigDecimal.ZERO)
                .notes(request.getNotes())
                .termsConditions(request.getTermsConditions() != null
                        ? request.getTermsConditions()
                        : settingsService.getDefaultTermsConditions())
                .sizeFormat(request.getSizeFormat() != null ? request.getSizeFormat() : "A4")
                .status(InvoiceStatus.DRAFT)
                .generatedByUserId(generatedByUserId)
                .generatedByName(generatedByName)
                .build();

        applyTaxType(invoice, request.getTaxType());

        // Map items
        if (request.getItems() != null) {
            List<InvoiceItem> items = request.getItems().stream()
                    .map(i -> InvoiceItem.builder()
                            .invoice(invoice)
                            .description(i.getDescription())
                            .itemType(i.getItemType())
                            .quantity(i.getQuantity())
                            .unitPrice(i.getUnitPrice())
                            .discountPercent(i.getDiscountPercent() != null ? i.getDiscountPercent() : BigDecimal.ZERO)
                            .hsnCode(i.getHsnCode())
                            .build())
                    .collect(Collectors.toList());
            items.forEach(item -> item.calcLineTotal());
            invoice.getItems().addAll(items);
        }

        invoice.calculateTotals();

        // Small retry loop: invoiceNumber is generated from a process-local
        // atomic counter (see generateInvoiceNumber), so it can't collide
        // within one instance, but as a safety net across restarts / multiple
        // instances we retry once on a genuine unique-constraint violation.
        Invoice saved;
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                saved = invoiceRepository.saveAndFlush(invoice);
                break;
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                if (attempts >= 3) throw e;
                invoice.setInvoiceNumber(generateInvoiceNumber());
            }
        }
        return toResponse(saved);
    }

    /** Was entirely missing — the frontend has called PUT /billing/invoices/{id} since the
     *  edit form was built, with no backend endpoint to answer it at all. Editing an
     *  existing invoice has been completely broken; every save on an already-created
     *  invoice would 404/405. Deliberately restricted to DRAFT status: once an invoice is
     *  finalized (sent) or has any payment recorded against it, changing its line items or
     *  totals after the fact is an accounting-integrity problem, not just a UX one — use
     *  cancelInvoice + a fresh createInvoice for a real correction instead. */
    public BillingDto.InvoiceResponse updateInvoice(String id, BillingDto.CreateInvoiceRequest request) {
        Invoice invoice = findById(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessException(
                    "Only a draft invoice can be edited — this one is already " + invoice.getStatus().name().toLowerCase() +
                            ". Cancel it and create a new one instead.",
                    "INVOICE_NOT_EDITABLE");
        }

        invoice.setInvoiceType(InvoiceType.valueOf(request.getInvoiceType()));
        invoice.setBillingType(request.getBillingType());
        invoice.setBookingId(request.getBookingId());
        invoice.setClientId(request.getClientId());
        invoice.setClientName(request.getClientName());
        invoice.setClientMobile(request.getClientMobile());
        invoice.setClientEmail(request.getClientEmail());
        invoice.setClientAddress(request.getClientAddress());
        invoice.setClientGstin(request.getClientGstin());
        if (request.getInvoiceDate() != null) { invoice.setInvoiceDate(request.getInvoiceDate()); }
        invoice.setDueDate(request.getDueDate());
        invoice.setDiscountAmount(request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO);
        invoice.setDiscountPercent(request.getDiscountPercent() != null ? request.getDiscountPercent() : BigDecimal.ZERO);
        invoice.setNotes(request.getNotes());
        if (request.getTermsConditions() != null) { invoice.setTermsConditions(request.getTermsConditions()); }
        if (request.getSizeFormat() != null) { invoice.setSizeFormat(request.getSizeFormat()); }
        applyTaxType(invoice, request.getTaxType());

        // orphanRemoval=true on Invoice.items means clearing then re-adding correctly
        // deletes the old line items and inserts the new set — not a manual diff, just
        // replace the whole collection, same as how the create path builds it the first time.
        invoice.getItems().clear();
        if (request.getItems() != null) {
            List<InvoiceItem> items = request.getItems().stream()
                    .map(i -> InvoiceItem.builder()
                            .invoice(invoice)
                            .description(i.getDescription())
                            .itemType(i.getItemType())
                            .quantity(i.getQuantity())
                            .unitPrice(i.getUnitPrice())
                            .discountPercent(i.getDiscountPercent() != null ? i.getDiscountPercent() : BigDecimal.ZERO)
                            .hsnCode(i.getHsnCode())
                            .build())
                    .collect(Collectors.toList());
            items.forEach(item -> item.calcLineTotal());
            invoice.getItems().addAll(items);
        }

        invoice.calculateTotals();
        return toResponse(invoiceRepository.save(invoice));
    }

    public BillingDto.InvoiceResponse finalizeInvoice(String id) {
        Invoice invoice = findById(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessException("Only draft invoices can be finalized", "INVALID_STATUS");
        }
        invoice.setStatus(InvoiceStatus.SENT);
        Invoice saved = invoiceRepository.save(invoice);

        // Generate PDF asynchronously
        generateAndSendInvoiceAsync(saved.getId());

        return toResponse(saved);
    }

    @Async
    public void generateAndSendInvoiceAsync(String invoiceId) {
        try {
            Invoice invoice = findById(invoiceId);
            byte[] pdfBytes = pdfService.generatePdf(invoice);
            // Use the key the upload actually returns (it contains a random UUID); a guessed
            // "invoices/<number>.pdf" key never existed, so every stored link was dead.
            String key = storageService.uploadBytes(pdfBytes, "invoices", invoice.getInvoiceNumber() + ".pdf", "application/pdf").getKey();
            invoice.setPdfS3Key(key);
            invoice.setPdfUrl(storageService.generateSignedDownloadUrl(key));
            invoiceRepository.save(invoice);

            // Send email if email available
            if (invoice.getClientEmail() != null && !invoice.isEmailSent()) {
                emailService.sendInvoiceEmail(invoice.getClientEmail(), invoice.getClientName(),
                        invoice.getInvoiceNumber(), pdfBytes);
                invoice.setEmailSent(true);
                invoiceRepository.save(invoice);
            }
        } catch (Exception e) {
            log.error("Failed to generate/send invoice {}: {}", invoiceId, e.getMessage(), e);
        }
    }

    public BillingDto.InvoiceResponse recordPayment(String id, BillingDto.PaymentRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be greater than zero", "INVALID_AMOUNT");
        }

        Invoice invoice = findById(id);
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new BusinessException("Finalize the invoice before recording a payment", "INVOICE_NOT_FINAL");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("This invoice is cancelled and cannot take payments", "INVOICE_CANCELLED");
        }

        BigDecimal newAmountPaid = invoice.getAmountPaid().add(request.getAmount());
        if (newAmountPaid.compareTo(invoice.getTotalAmount()) > 0) {
            throw new BusinessException("Payment amount exceeds invoice total", "OVERPAYMENT");
        }

        invoice.setAmountPaid(newAmountPaid);
        invoice.setBalanceDue(invoice.getTotalAmount().subtract(newAmountPaid));

        if (invoice.getBalanceDue().compareTo(BigDecimal.ZERO) == 0) {
            invoice.setStatus(InvoiceStatus.PAID);
            unlockGalleryDownloadsForBooking(invoice);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }

        Invoice savedInvoice = invoiceRepository.save(invoice);
        syncBookingFromInvoice(savedInvoice);
        return toResponse(savedInvoice);
    }

    /**
     * Full payment received — automatically unlock full-resolution, watermark-free
     * downloads for the gallery(ies) tied to this invoice's booking, instead of
     * requiring an admin to remember to flip the toggle manually. Best-effort: any
     * failure here is logged, not thrown, so a notification hiccup never blocks the
     * payment itself from being recorded.
     */
    private void unlockGalleryDownloadsForBooking(Invoice invoice) {
        try {
            java.util.LinkedHashMap<String, com.cameraman.gallery.domain.Gallery> targets = new java.util.LinkedHashMap<>();
            if (invoice.getBookingId() != null && !invoice.getBookingId().isBlank()) {
                galleryRepository.findAllByBookingId(invoice.getBookingId()).forEach(g -> targets.put(g.getId(), g));
            }
            // Galleries that staff did not link to a booking belong to the client: release them too,
            // but only when this client has nothing else still unpaid (otherwise paying one small
            // invoice would release photos of a shoot that is still owed).
            if (invoice.getClientId() != null && !invoice.getClientId().isBlank() && !clientHasOpenInvoices(invoice)) {
                galleryRepository.findByClientId(invoice.getClientId(), org.springframework.data.domain.Pageable.unpaged())
                        .forEach(g -> { if (g.getBookingId() == null || g.getBookingId().isBlank()) targets.put(g.getId(), g); });
            }
            for (com.cameraman.gallery.domain.Gallery gallery : targets.values()) {
                if (gallery.isDownloadAllowed()) {
                    continue; // already unlocked — don't re-notify
                }
                gallery.setDownloadAllowed(true);
                galleryRepository.save(gallery);

                String mobile = invoice.getClientMobile();
                if (mobile != null && !mobile.isBlank()) {
                    try {
                        whatsAppService.sendTextMessage(mobile, String.format(
                                "Hello %s,\n\nYour payment is complete and your photos are now unlocked without watermark, with full-quality downloads! 🎉\n\nHead to your gallery to download them.\n\nThank you for choosing us!",
                                invoice.getClientName()));
                    } catch (Exception e) {
                        log.warn("Unlock message to {} failed: {}", mobile, e.getMessage());
                    }
                }
                log.info("Auto-unlocked gallery {} after full payment on invoice {}", gallery.getId(), invoice.getInvoiceNumber());
            }
        } catch (Exception e) {
            log.error("Failed to auto-unlock gallery downloads for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage());
        }
    }

    private boolean clientHasOpenInvoices(Invoice paid) {
        return invoiceRepository.findByClientId(paid.getClientId(), org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .anyMatch(i -> !i.getId().equals(paid.getId())
                        && (i.getStatus() == InvoiceStatus.SENT || i.getStatus() == InvoiceStatus.PARTIALLY_PAID
                            || i.getStatus() == InvoiceStatus.OVERDUE)
                        && i.getBalanceDue() != null && i.getBalanceDue().signum() > 0);
    }

    public BillingDto.InvoiceResponse cancelInvoice(String id, String reason) {
        Invoice invoice = findById(id);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("Invoice is already cancelled", "INVOICE_CANCELLED");
        }
        if (invoice.getAmountPaid() != null && invoice.getAmountPaid().signum() > 0) {
            throw new BusinessException(
                    "Payments have been received on this invoice. Refund them first, then cancel.", "INVOICE_HAS_PAYMENTS");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setNotes((invoice.getNotes() != null ? invoice.getNotes() + "\n" : "") + "Cancelled: " + reason);
        return toResponse(invoiceRepository.save(invoice));
    }

    /**
     * Reverses part (or all) of a recorded payment on the invoice. Called by PaymentService when
     * a refund is processed so the invoice's paid/balance/status never drift from the payments table.
     * Full reversal -> REFUNDED (can then be cancelled); partial -> PARTIALLY_PAID.
     */
    public BillingDto.InvoiceResponse recordRefund(String id, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Refund amount must be greater than zero", "INVALID_AMOUNT");
        }
        Invoice invoice = findById(id);
        BigDecimal paid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
        if (amount.compareTo(paid) > 0) {
            throw new BusinessException("Refund exceeds the amount paid on this invoice", "REFUND_EXCEEDS_PAID");
        }
        BigDecimal newPaid = paid.subtract(amount);
        invoice.setAmountPaid(newPaid);
        invoice.setBalanceDue(invoice.getTotalAmount().subtract(newPaid));
        if (newPaid.signum() == 0) {
            invoice.setStatus(InvoiceStatus.REFUNDED);
        } else if (invoice.getBalanceDue().signum() > 0) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        Invoice savedInvoice = invoiceRepository.save(invoice);
        syncBookingFromInvoice(savedInvoice);
        return toResponse(savedInvoice);
    }

    @Transactional(readOnly = true)
    public BillingDto.InvoiceResponse getById(String id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<BillingDto.InvoiceResponse> getMyInvoices(String clientId, Pageable pageable) {
        // Clients never see drafts: an invoice is only theirs once staff have finalized it.
        return PagedResponse.of(invoiceRepository.findByClientIdAndStatusNot(clientId, InvoiceStatus.DRAFT, pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PagedResponse<BillingDto.InvoiceResponse> getAllInvoices(String search, String status, Pageable pageable) {
        Page<Invoice> page;
        if (search != null && !search.isBlank()) {
            page = invoiceRepository.searchInvoices(search, pageable);
        } else if (status != null && !status.isBlank()) {
            page = invoiceRepository.findByStatus(InvoiceStatus.valueOf(status), pageable);
        } else {
            page = invoiceRepository.findAll(pageable);
        }
        return PagedResponse.of(page.map(this::toResponse));
    }

    /** Everything needed to raise an invoice for a booking / studio session / outdoor shoot. */
    private record SourceSpec(String sourceId, String clientId, String clientName, String clientMobile,
                              String clientEmail, String what, String reference, java.time.LocalDate date,
                              BigDecimal total, BigDecimal advance) {}

    /**
     * Every booking gets an invoice. Idempotent: if one already exists for the booking it is returned
     * untouched. The booking's total is what the client pays, so GST (when the studio has a GSTIN
     * configured) is backed out of it and the invoice total matches the booking total. The invoice is
     * finalized with its PDF stored so download / print / email / WhatsApp work straight away, but
     * nothing is sent to the client automatically. An advance becomes a recorded payment.
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public BillingDto.InvoiceResponse createInvoiceForBooking(String bookingId, String generatedByUserId) {
        var existing = invoiceRepository.findByBookingId(bookingId, org.springframework.data.domain.PageRequest.of(0, 1));
        if (!existing.isEmpty()) return toResponse(existing.getContent().get(0));

        var booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException("Booking not found", "BOOKING_NOT_FOUND"));
        String what = booking.getPackageName() != null && !booking.getPackageName().isBlank()
                ? booking.getPackageName() : booking.getShootCategory().name().replace('_', ' ') + " photography";
        return raiseSourceInvoice(new SourceSpec(booking.getId(),
                booking.getClient() != null ? booking.getClient().getId() : null,
                booking.getClientName(), booking.getClientMobile(), booking.getClientEmail(),
                what, booking.getBookingNumber(), booking.getBookingDate(),
                booking.getTotalAmount(), booking.getAdvanceAmount()), generatedByUserId);
    }

    /** Same as {@link #createInvoiceForBooking} but for an Indoor / Studio session. Idempotent. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public BillingDto.InvoiceResponse createInvoiceForStudioSession(String sessionId, String generatedByUserId) {
        var existing = invoiceRepository.findByBookingId(sessionId, org.springframework.data.domain.PageRequest.of(0, 1));
        if (!existing.isEmpty()) return toResponse(existing.getContent().get(0));

        var s = studioSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Studio session not found", "SESSION_NOT_FOUND"));
        String what = s.getPackageName() != null && !s.getPackageName().isBlank() ? s.getPackageName() : "Studio session";
        return raiseSourceInvoice(new SourceSpec(s.getId(),
                s.getClient() != null ? s.getClient().getId() : null,
                s.getClientName(), s.getClientMobile(), s.getClientEmail(),
                what, s.getSessionNumber(), s.getSessionDate(), s.getTotalAmount(), s.getAdvanceAmount()), generatedByUserId);
    }

    /** Same as {@link #createInvoiceForBooking} but for an Outdoor shoot. Idempotent. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public BillingDto.InvoiceResponse createInvoiceForOutdoorShoot(String shootId, String generatedByUserId) {
        var existing = invoiceRepository.findByBookingId(shootId, org.springframework.data.domain.PageRequest.of(0, 1));
        if (!existing.isEmpty()) return toResponse(existing.getContent().get(0));

        var o = outdoorShootRepository.findById(shootId)
                .orElseThrow(() -> new BusinessException("Outdoor shoot not found", "SHOOT_NOT_FOUND"));
        String what = o.getPackageName() != null && !o.getPackageName().isBlank() ? o.getPackageName()
                : (o.getCategory() != null ? o.getCategory().name().replace('_', ' ') : "Outdoor") + " shoot";
        return raiseSourceInvoice(new SourceSpec(o.getId(),
                o.getClient() != null ? o.getClient().getId() : null,
                o.getClientName(), o.getClientMobile(), o.getClientEmail(),
                what, o.getShootNumber(), o.getShootDate(), o.getTotalAmount(), o.getAdvanceAmount()), generatedByUserId);
    }

    /**
     * Shared body of the three createInvoiceFor* methods. Invoice.bookingId is a plain string column
     * with no foreign key, so it holds the id of whichever record the invoice belongs to (booking,
     * studio session or outdoor shoot); the reference number is also written into the notes.
     */
    private BillingDto.InvoiceResponse raiseSourceInvoice(SourceSpec src, String generatedByUserId) {
        BigDecimal total = src.total() != null ? src.total() : BigDecimal.ZERO;
        String gstin = settingsService.getStudioSettings().getGstNumber();
        boolean gstRegistered = gstin != null && !gstin.isBlank();
        BigDecimal unitPrice = gstRegistered
                ? total.divide(new BigDecimal("1.18"), 2, java.math.RoundingMode.HALF_UP)
                : total;

        BillingDto.CreateInvoiceRequest req = BillingDto.CreateInvoiceRequest.builder()
                .invoiceType("OFFLINE")
                .bookingId(src.sourceId())
                .clientId(src.clientId())
                .clientName(src.clientName())
                .clientMobile(src.clientMobile())
                .clientEmail(src.clientEmail() != null && !src.clientEmail().isBlank() ? src.clientEmail() : null)
                .invoiceDate(LocalDate.now())
                // Balance is due a week after the shoot, so overdue tracking has a date to work from.
                .dueDate(src.date() != null ? src.date().plusDays(7) : null)
                .taxType(gstRegistered ? "INTRA_STATE" : "EXEMPT")
                .notes(src.reference() + " · " + src.date())
                .items(List.of(BillingDto.InvoiceItemRequest.builder()
                        .description(src.what() + " (" + src.reference() + ")")
                        .itemType("PACKAGE").quantity(1).unitPrice(unitPrice).build()))
                .build();
        BillingDto.InvoiceResponse created = createInvoice(req, generatedByUserId);

        Invoice invoice = findById(created.getId());
        invoice.setStatus(InvoiceStatus.SENT);
        try {
            byte[] pdf = pdfService.generatePdf(invoice);
            String key = storageService.uploadBytes(pdf, "invoices", invoice.getInvoiceNumber() + ".pdf", "application/pdf").getKey();
            invoice.setPdfS3Key(key);
        } catch (Exception e) {
            // Storage / PDF hiccup must not block the booking; the PDF is regenerated on demand.
            log.warn("Could not store PDF for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage());
        }
        invoiceRepository.save(invoice);

        BigDecimal advance = src.advance();
        if (advance != null && advance.signum() > 0 && advance.compareTo(invoice.getTotalAmount()) <= 0) {
            recordPayment(invoice.getId(), BillingDto.PaymentRequest.builder()
                    .amount(advance).paymentMethod("CASH").notes("Advance received at booking").build());
        }
        return toResponse(findById(invoice.getId()));
    }

    // ───────── Booking <-> invoice consistency ─────────

    /** The invoice is the master record of money. After every payment or refund the booking's
     *  advance / balance follow it, and a first payment confirms a still-pending booking. */
    private void syncBookingFromInvoice(Invoice invoice) {
        if (invoice.getBookingId() == null || invoice.getBookingId().isBlank()) return;
        try {
            bookingRepository.findById(invoice.getBookingId()).ifPresent(b -> {
                BigDecimal paid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
                BigDecimal total = b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal advance = paid.min(total).max(BigDecimal.ZERO);
                b.setAdvanceAmount(advance);
                b.setBalanceAmount(total.subtract(advance).max(BigDecimal.ZERO));
                if (paid.signum() > 0 && b.getStatus() == com.cameraman.booking.domain.BookingStatus.PENDING) {
                    b.setStatus(com.cameraman.booking.domain.BookingStatus.CONFIRMED);
                }
                bookingRepository.save(b);
            });
        } catch (Exception e) {
            log.warn("Could not sync booking {} with invoice {}: {}", invoice.getBookingId(), invoice.getInvoiceNumber(), e.getMessage());
        }
    }

    /** Id of the invoice raised for a booking / session / shoot, if any. */
    @Transactional(readOnly = true)
    public java.util.Optional<String> findInvoiceIdForSource(String sourceId) {
        var page = invoiceRepository.findByBookingId(sourceId, org.springframework.data.domain.PageRequest.of(0, 1));
        return page.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(page.getContent().get(0).getId());
    }

    /**
     * Called when staff change a booking's total: keeps the (single-line) automatic invoice in step.
     * Refuses a total lower than what has already been received. The stored PDF is dropped so it is
     * regenerated with the new amount.
     */
    public void resyncInvoiceTotal(String sourceId, BigDecimal newTotal) {
        var page = invoiceRepository.findByBookingId(sourceId, org.springframework.data.domain.PageRequest.of(0, 1));
        if (page.isEmpty()) return;
        Invoice inv = page.getContent().get(0);
        if (inv.getStatus() == InvoiceStatus.CANCELLED || inv.getStatus() == InvoiceStatus.REFUNDED || inv.getItems().size() != 1) return;
        BigDecimal total = newTotal != null ? newTotal : BigDecimal.ZERO;
        String gstin = settingsService.getStudioSettings().getGstNumber();
        boolean gstRegistered = gstin != null && !gstin.isBlank();
        BigDecimal unitPrice = gstRegistered
                ? total.divide(new BigDecimal("1.18"), 2, java.math.RoundingMode.HALF_UP) : total;
        InvoiceItem item = inv.getItems().get(0);
        item.setUnitPrice(unitPrice);
        item.calcLineTotal();
        inv.calculateTotals();
        BigDecimal paid = inv.getAmountPaid() != null ? inv.getAmountPaid() : BigDecimal.ZERO;
        if (inv.getTotalAmount().compareTo(paid) < 0) {
            throw new BusinessException("The new total is lower than the " + paid.toPlainString()
                    + " already received on the invoice. Refund the difference first.", "TOTAL_BELOW_PAID");
        }
        if (inv.getStatus() != InvoiceStatus.DRAFT) {
            if (inv.getBalanceDue().signum() == 0 && paid.signum() > 0) { inv.setStatus(InvoiceStatus.PAID); unlockGalleryDownloadsForBooking(inv); }
            else if (paid.signum() > 0) inv.setStatus(InvoiceStatus.PARTIALLY_PAID);
            else if (inv.getStatus() == InvoiceStatus.PAID) inv.setStatus(InvoiceStatus.SENT);
        }
        inv.setPdfS3Key(null);
        inv.setPdfUrl(null);
        invoiceRepository.save(inv);
    }

    /**
     * Called when a booking / session / shoot is cancelled. An invoice with no money received is
     * cancelled automatically. Returns true when money WAS received, so the caller can tell staff
     * to refund or keep it (the invoice is left open for that decision).
     */
    public boolean cancelInvoiceForSource(String sourceId, String reason) {
        var page = invoiceRepository.findByBookingId(sourceId, org.springframework.data.domain.PageRequest.of(0, 1));
        if (page.isEmpty()) return false;
        Invoice inv = page.getContent().get(0);
        if (inv.getStatus() == InvoiceStatus.CANCELLED || inv.getStatus() == InvoiceStatus.REFUNDED) return false;
        if (inv.getAmountPaid() != null && inv.getAmountPaid().signum() > 0) return true;
        inv.setStatus(InvoiceStatus.CANCELLED);
        inv.setNotes((inv.getNotes() != null ? inv.getNotes() + "\n" : "") + "Cancelled with the booking" + (reason != null && !reason.isBlank() ? ": " + reason : ""));
        invoiceRepository.save(inv);
        return false;
    }

    /** Invoices for a client, newest first (staff view; includes drafts). */

    @Transactional(readOnly = true)
    public List<BillingDto.InvoiceResponse> getInvoicesForClient(String clientId) {
        return invoiceRepository.findByClientId(clientId,
                        org.springframework.data.domain.PageRequest.of(0, 200, org.springframework.data.domain.Sort.by("createdAt").descending()))
                .map(this::toResponse).getContent();
    }

    /** True when an invoice already exists for this booking / session / shoot id. */
    @Transactional(readOnly = true)
    public boolean hasInvoiceFor(String sourceId) {
        return !invoiceRepository.findByBookingId(sourceId, org.springframework.data.domain.PageRequest.of(0, 1)).isEmpty();
    }

    public byte[] downloadInvoicePdf(String id) {
        Invoice invoice = findById(id);
        if (invoice.getPdfS3Key() != null) {
            try {
                return storageService.downloadFile(invoice.getPdfS3Key());
            } catch (Exception e) {
                // Older invoices were saved with a wrong storage key; just regenerate.
                log.warn("Stored PDF for {} unreadable ({}), regenerating", invoice.getInvoiceNumber(), e.getMessage());
            }
        }
        return pdfService.generatePdf(invoice);
    }

    public void sendEmailInvoice(String id) {
        Invoice invoice = findById(id);
        if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("Only a finalized invoice can be sent", "INVOICE_NOT_SENDABLE");
        }
        if (invoice.getClientEmail() == null || invoice.getClientEmail().isBlank()) {
            throw new BusinessException("Client email address not available", "NO_EMAIL");
        }
        try {
            byte[] pdf = downloadInvoicePdf(id);
            // Synchronous on purpose: the @Async variant swallowed failures, so the UI said
            // "Invoice emailed" (and emailSent was set) even when the mail never left.
            emailService.sendInvoiceEmailNow(invoice.getClientEmail(), invoice.getClientName(), invoice.getInvoiceNumber(), pdf);
            invoice.setEmailSent(true);
            invoiceRepository.save(invoice);
        } catch (Exception e) {
            throw new BusinessException("Failed to send invoice email: " + e.getMessage(), "EMAIL_ERROR");
        }
    }

    public void sendWhatsAppInvoice(String id) {
        Invoice invoice = findById(id);
        if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("Only a finalized invoice can be sent", "INVOICE_NOT_SENDABLE");
        }
        if (invoice.getClientMobile() == null || invoice.getClientMobile().isBlank()) {
            throw new BusinessException("Client mobile number not available", "NO_MOBILE");
        }
        if (!whatsAppService.isConfigured()) {
            throw new BusinessException("WhatsApp is not configured on the server yet (set the WhatsApp phone-number id and access token), so nothing was sent.", "WHATSAPP_NOT_CONFIGURED");
        }
        try {
            // Always mint a fresh signed link from the stored key: the pdfUrl saved at creation is a
            // time-limited signed URL and is dead by the time staff click "send".
            if (invoice.getPdfS3Key() == null) {
                // No stored PDF yet: generate and store it now rather than failing with "try again in a minute".
                byte[] pdf = pdfService.generatePdf(invoice);
                String key = storageService.uploadBytes(pdf, "invoices", invoice.getInvoiceNumber() + ".pdf", "application/pdf").getKey();
                invoice.setPdfS3Key(key);
                invoiceRepository.save(invoice);
            }
            String pdfUrl = storageService.generateSignedDownloadUrl(invoice.getPdfS3Key());
            boolean ok = whatsAppService.sendInvoiceChecked(invoice.getClientMobile(), invoice.getClientName(),
                    invoice.getInvoiceNumber(), invoice.getTotalAmount(), pdfUrl);
            if (!ok) throw new BusinessException("WhatsApp did not accept the message. Check the number and the WhatsApp API settings.", "WHATSAPP_ERROR");
            invoice.setWhatsappSent(true);
            invoiceRepository.save(invoice);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Failed to send WhatsApp invoice: " + e.getMessage(), "WHATSAPP_ERROR");
        }
    }

    @Transactional(readOnly = true)
    public BillingDto.RevenueSummary getRevenueSummary(LocalDate from, LocalDate to) {
        BigDecimal revenue = invoiceRepository.sumRevenueByDateRange(from, to);
        BigDecimal outstanding = invoiceRepository.sumOutstandingBalance();
        long totalInvoices = invoiceRepository.countByDateRange(from, to);
        long paidCount = invoiceRepository.countByStatus(InvoiceStatus.PAID);
        long pendingCount = invoiceRepository.countByStatus(InvoiceStatus.SENT)
                + invoiceRepository.countByStatus(InvoiceStatus.PARTIALLY_PAID);

        return BillingDto.RevenueSummary.builder()
                .totalRevenue(revenue).outstandingBalance(outstanding)
                .totalInvoices(totalInvoices).paidCount(paidCount).pendingCount(pendingCount)
                .fromDate(from).toDate(to).build();
    }

    private Invoice findById(String id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));
    }

    private static final java.util.concurrent.atomic.AtomicLong invoiceCounter =
            new java.util.concurrent.atomic.AtomicLong(0);
    private volatile String invoiceCounterMonth = "";

    /**
     * Same fix as BookingService.generateBookingNumber(): the old
     * {@code invoiceRepository.count() + 1} was not concurrency-safe — two
     * simultaneous invoice creations could compute the same count and then
     * collide on the DB's unique invoice_number constraint. Uses a
     * process-local atomic counter, reseeded from the DB on month rollover.
     */
    private synchronized String generateInvoiceNumber() {
        String prefix = "INV-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        if (!prefix.equals(invoiceCounterMonth)) {
            invoiceCounter.set(invoiceRepository.count());
            invoiceCounterMonth = prefix;
        }
        long next = invoiceCounter.incrementAndGet();
        return prefix + String.format("%04d", next);
    }

    /** Sets cgst/sgst/igst rates on the invoice from the request's taxType.
     *  Previously the create/update DTOs had no way to express this at all —
     *  every invoice silently used the entity's hardcoded 9%+9% CGST/SGST
     *  default rates, with no way to mark an interstate sale (which should
     *  be pure IGST) or a tax-exempt one. Null/blank taxType, or the
     *  explicit value INTRA_STATE, leaves the entity defaults untouched. */
    private void applyTaxType(Invoice invoice, String taxType) {
        if (taxType == null || taxType.isBlank() || "INTRA_STATE".equalsIgnoreCase(taxType)) {
            return;
        }
        switch (taxType.toUpperCase()) {
            case "INTER_STATE" -> {
                invoice.setCgstRate(BigDecimal.ZERO);
                invoice.setSgstRate(BigDecimal.ZERO);
                invoice.setIgstRate(new BigDecimal("18.00"));
            }
            case "EXEMPT" -> {
                invoice.setCgstRate(BigDecimal.ZERO);
                invoice.setSgstRate(BigDecimal.ZERO);
                invoice.setIgstRate(BigDecimal.ZERO);
            }
            default -> throw new BusinessException(
                    "taxType must be one of INTRA_STATE, INTER_STATE, EXEMPT", "INVALID_TAX_TYPE");
        }
    }

    /** Derives the tax-type label from the invoice's stored rates rather
     *  than a separate column, so this works for every existing row without
     *  a migration. */
    private String deriveTaxType(Invoice inv) {
        if (inv.getIgstRate() != null && inv.getIgstRate().signum() > 0) return "INTER_STATE";
        boolean cgstZero = inv.getCgstRate() == null || inv.getCgstRate().signum() == 0;
        boolean sgstZero = inv.getSgstRate() == null || inv.getSgstRate().signum() == 0;
        if (cgstZero && sgstZero) return "EXEMPT";
        return "INTRA_STATE";
    }

    private BillingDto.InvoiceResponse toResponse(Invoice inv) {
        List<BillingDto.InvoiceItemResponse> items = inv.getItems().stream()
                .map(i -> BillingDto.InvoiceItemResponse.builder()
                        .id(i.getId()).description(i.getDescription())
                        .itemType(i.getItemType()).quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice()).discountPercent(i.getDiscountPercent())
                        .lineTotal(i.getLineTotal()).hsnCode(i.getHsnCode()).build())
                .collect(Collectors.toList());

        return BillingDto.InvoiceResponse.builder()
                .id(inv.getId()).invoiceNumber(inv.getInvoiceNumber())
                .invoiceType(inv.getInvoiceType().name()).billingType(inv.getBillingType())
                .bookingId(inv.getBookingId()).clientId(inv.getClientId())
                .clientName(inv.getClientName()).clientMobile(inv.getClientMobile())
                .clientEmail(inv.getClientEmail()).clientAddress(inv.getClientAddress())
                .invoiceDate(inv.getInvoiceDate()).dueDate(inv.getDueDate())
                .subtotal(inv.getSubtotal()).discountAmount(inv.getDiscountAmount())
                .taxableAmount(inv.getTaxableAmount()).cgstAmount(inv.getCgstAmount())
                .sgstAmount(inv.getSgstAmount()).igstAmount(inv.getIgstAmount())
                .totalTax(inv.getTotalTax()).totalAmount(inv.getTotalAmount())
                .amountPaid(inv.getAmountPaid()).balanceDue(inv.getBalanceDue())
                .status(inv.getStatus().name()).notes(inv.getNotes())
                .pdfUrl(inv.getPdfUrl()).whatsappSent(inv.isWhatsappSent())
                .emailSent(inv.isEmailSent()).sizeFormat(inv.getSizeFormat())
                .generatedByName(inv.getGeneratedByName())
                .taxType(deriveTaxType(inv))
                .items(items).createdAt(inv.getCreatedAt()).updatedAt(inv.getUpdatedAt())
                .build();
    }
}
