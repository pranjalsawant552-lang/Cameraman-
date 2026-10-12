package com.cameraman.billing.repository;

import com.cameraman.billing.domain.Invoice;
import com.cameraman.billing.domain.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    Page<Invoice> findByClientId(String clientId, Pageable pageable);
    Page<Invoice> findByClientIdAndStatusNot(String clientId, InvoiceStatus status, Pageable pageable);
    Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);
    Page<Invoice> findByBookingId(String bookingId, Pageable pageable);

    @Query("SELECT i FROM Invoice i WHERE " +
           "(LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
           "LOWER(i.clientName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
           "i.clientMobile LIKE CONCAT('%',:q,'%'))")
    Page<Invoice> searchInvoices(@Param("q") String query, Pageable pageable);

    @Query("SELECT COALESCE(SUM(i.totalAmount),0) FROM Invoice i WHERE i.invoiceDate BETWEEN :from AND :to AND i.status NOT IN ('CANCELLED','DRAFT')")
    BigDecimal sumRevenueByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(i.balanceDue),0) FROM Invoice i WHERE i.status IN ('SENT','PARTIALLY_PAID','OVERDUE')")
    BigDecimal sumOutstandingBalance();

    long countByStatus(InvoiceStatus status);

    java.util.List<Invoice> findByStatusInAndDueDateBefore(java.util.Collection<InvoiceStatus> statuses, LocalDate date);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.invoiceDate BETWEEN :from AND :to")
    long countByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    java.util.List<Invoice> findTop10ByClientIdOrderByInvoiceDateDesc(String clientId);

    /** One row: [total billed, total paid, balance due] over the client's live (not draft/cancelled) invoices. */
    @Query("SELECT COALESCE(SUM(i.totalAmount),0), COALESCE(SUM(i.amountPaid),0), COALESCE(SUM(i.balanceDue),0) " +
           "FROM Invoice i WHERE i.clientId = :clientId AND i.status NOT IN ('CANCELLED','DRAFT')")
    java.util.List<Object[]> sumTotalsForClient(@Param("clientId") String clientId);

    @Query("SELECT i.clientId, COALESCE(SUM(i.amountPaid),0) FROM Invoice i WHERE i.clientId IN :clientIds GROUP BY i.clientId")
    java.util.List<Object[]> sumAmountPaidByClientIdIn(@Param("clientIds") java.util.List<String> clientIds);
}
