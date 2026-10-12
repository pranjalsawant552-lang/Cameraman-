package com.cameraman.payment.repository;

import com.cameraman.payment.domain.Payment;
import com.cameraman.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    List<Payment> findByInvoiceId(String invoiceId);
    Page<Payment> findByClientId(String clientId, Pageable pageable);
    Optional<Payment> findByRazorpayOrderId(String orderId);

    /** Row-locked lookup so a checkout verify and a webhook racing for the same order are serialized
     *  and only the first one applies the payment (the second sees SUCCESS and is a no-op). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.razorpayOrderId = :orderId")
    Optional<Payment> findByRazorpayOrderIdForUpdate(@Param("orderId") String orderId);
    Optional<Payment> findByRazorpayPaymentId(String paymentId);

    @Query("SELECT COALESCE(SUM(p.amount),0) FROM Payment p WHERE p.invoiceId = :invoiceId AND p.status = 'SUCCESS'")
    BigDecimal sumSuccessfulPaymentsByInvoice(@Param("invoiceId") String invoiceId);

    @Query("SELECT p FROM Payment p WHERE " +
           "LOWER(p.clientName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(p.transactionId) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(p.transactionReference) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(p.razorpayPaymentId) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))")
    Page<Payment> searchPayments(@Param("search") String search, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
