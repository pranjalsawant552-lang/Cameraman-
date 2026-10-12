package com.cameraman.billing.scheduler;

import com.cameraman.billing.domain.Invoice;
import com.cameraman.billing.domain.InvoiceStatus;
import com.cameraman.billing.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Nightly: unpaid invoices whose due date has passed become OVERDUE (shown in the Invoices filter and the dashboard). */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceOverdueScheduler {

    private final InvoiceRepository invoiceRepository;

    @Scheduled(cron = "0 30 0 * * *")
    @Transactional
    public void markOverdueInvoices() {
        List<Invoice> late = invoiceRepository.findByStatusInAndDueDateBefore(
                List.of(InvoiceStatus.SENT, InvoiceStatus.PARTIALLY_PAID), LocalDate.now());
        int changed = 0;
        for (Invoice inv : late) {
            if (inv.getBalanceDue() != null && inv.getBalanceDue().signum() > 0) {
                inv.setStatus(InvoiceStatus.OVERDUE);
                invoiceRepository.save(inv);
                changed++;
            }
        }
        if (changed > 0) log.info("Marked {} invoice(s) overdue", changed);
    }
}
