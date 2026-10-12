package com.cameraman.billing.service;

import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.outdoor.repository.OutdoorShootRepository;
import com.cameraman.studio.repository.StudioSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Makes sure every booking, studio session and outdoor shoot has an invoice in the Invoice module.
 *
 * <p>Deliberately NOT @Transactional: each invoice is raised by BillingService in its own
 * REQUIRES_NEW transaction, and one failure must never roll back the booking that triggered it
 * (or the other records in a backfill run). Calls made from inside a transaction are deferred
 * until that transaction commits, so the invoice always sees the committed record.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoInvoiceService {

    private final BillingService billingService;
    private final BookingRepository bookingRepository;
    private final StudioSessionRepository studioSessionRepository;
    private final OutdoorShootRepository outdoorShootRepository;

    public void onStudioSessionCreated(String sessionId, String userId) {
        afterCommit(() -> {
            try { billingService.createInvoiceForStudioSession(sessionId, userId); }
            catch (Exception e) { log.error("Auto-invoice failed for studio session {}: {}", sessionId, e.getMessage(), e); }
        });
    }

    public void onOutdoorShootCreated(String shootId, String userId) {
        afterCommit(() -> {
            try { billingService.createInvoiceForOutdoorShoot(shootId, userId); }
            catch (Exception e) { log.error("Auto-invoice failed for outdoor shoot {}: {}", shootId, e.getMessage(), e); }
        });
    }

    /** Cancels the unpaid invoice of a cancelled booking/session/shoot. True = money was received and needs a decision. */
    public boolean onSourceCancelled(String sourceId, String reason) {
        try { return billingService.cancelInvoiceForSource(sourceId, reason); }
        catch (Exception e) { log.error("Could not cancel invoice for {}: {}", sourceId, e.getMessage(), e); return false; }
    }

    /** errors: up to 5 "<record>: <reason>" lines so the screen can say WHY invoices failed, not just how many. */
    public record BackfillResult(int created, int failed, int alreadyHadInvoice, java.util.List<String> errors) {}

    /** Raises an invoice for every existing booking / session / shoot that does not have one yet. */
    public BackfillResult backfillMissing(String userId) {
        int created = 0, failed = 0, existing = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();
        for (var b : bookingRepository.findAll()) {
            if (b.getStatus() != null && "CANCELLED".equals(b.getStatus().name())) continue;
            if (billingService.hasInvoiceFor(b.getId())) { existing++; continue; }
            try { billingService.createInvoiceForBooking(b.getId(), userId); created++; }
            catch (Exception e) { failed++; log.warn("Backfill invoice failed for booking {}: {}", b.getId(), e.getMessage()); note(errors, b.getBookingNumber(), e); }
        }
        for (var s : studioSessionRepository.findAll()) {
            if (s.getStatus() != null && "CANCELLED".equals(s.getStatus().name())) continue;
            if (billingService.hasInvoiceFor(s.getId())) { existing++; continue; }
            try { billingService.createInvoiceForStudioSession(s.getId(), userId); created++; }
            catch (Exception e) { failed++; log.warn("Backfill invoice failed for studio session {}: {}", s.getId(), e.getMessage()); note(errors, s.getSessionNumber(), e); }
        }
        for (var o : outdoorShootRepository.findAll()) {
            if (o.getStatus() != null && "CANCELLED".equals(o.getStatus().name())) continue;
            if (billingService.hasInvoiceFor(o.getId())) { existing++; continue; }
            try { billingService.createInvoiceForOutdoorShoot(o.getId(), userId); created++; }
            catch (Exception e) { failed++; log.warn("Backfill invoice failed for outdoor shoot {}: {}", o.getId(), e.getMessage()); note(errors, o.getShootNumber(), e); }
        }
        log.info("Invoice backfill: {} created, {} failed, {} already had one", created, failed, existing);
        return new BackfillResult(created, failed, existing, errors);
    }

    private static void note(java.util.List<String> errors, String ref, Exception e) {
        if (errors.size() < 5) errors.add((ref != null ? ref : "record") + ": " + e.getMessage());
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { task.run(); }
            });
        } else {
            task.run();
        }
    }
}
