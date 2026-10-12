package com.cameraman.booking.repository;

import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.domain.BookingStatus;
import com.cameraman.booking.domain.BookingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByBookingNumber(String bookingNumber);

    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

    Page<Booking> findByClientId(String clientId, Pageable pageable);

    long countByClientId(String clientId);

    long countByClientIdAndStatus(String clientId, BookingStatus status);

    java.util.List<Booking> findTop10ByClientIdOrderByBookingDateDesc(String clientId);

    // Booking.client is a @ManyToOne User relation mapped via the client_id
    // column — there's no plain `clientId` field on the entity itself. The
    // derived-query methods above (findByClientId) work anyway because
    // Spring Data's method-name parser tries "client.id" once "clientId"
    // doesn't match a direct property, but that fallback is unique to
    // derived queries — it does NOT apply inside a hand-written @Query, so
    // `b.clientId` there is invalid HQL and fails at startup
    // (UnknownPathException). Must be written as `b.client.id`.
    @Query("SELECT b.client.id, COUNT(b) FROM Booking b WHERE b.client.id IN :clientIds GROUP BY b.client.id")
    List<Object[]> countByClientIdIn(@Param("clientIds") List<String> clientIds);

    Page<Booking> findByPhotographerId(String photographerId, Pageable pageable);

    java.util.List<Booking> findTop10ByPhotographerIdAndBookingDateGreaterThanEqualOrderByBookingDateAsc(String photographerId, LocalDate from);

    java.util.List<Booking> findTop10ByPhotographerIdAndBookingDateLessThanOrderByBookingDateDesc(String photographerId, LocalDate before);

    long countByPhotographerId(String photographerId);

    long countByPhotographerIdAndStatus(String photographerId, BookingStatus status);

    List<Booking> findByBookingDateAndStatus(LocalDate date, BookingStatus status);

    List<Booking> findByBookingDateBetween(LocalDate from, LocalDate to);

    @Query("SELECT b FROM Booking b WHERE b.bookingDate = :date")
    List<Booking> findByBookingDate(@Param("date") LocalDate date);

    @Query("SELECT b FROM Booking b WHERE b.bookingDate BETWEEN :from AND :to AND b.status = :status")
    List<Booking> findByDateRangeAndStatus(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                            @Param("status") BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE " +
           "(LOWER(b.bookingNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(b.clientName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "b.clientMobile LIKE CONCAT('%', :search, '%') OR " +
           "LOWER(b.clientEmail) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Booking> searchBookings(@Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.status = :status")
    long countByStatus(@Param("status") BookingStatus status);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.bookingDate = :date")
    long countByBookingDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE " +
           "b.bookingDate BETWEEN :from AND :to AND b.status = 'COMPLETED'")
    BigDecimal sumRevenueByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT b FROM Booking b WHERE b.reminderSent = false AND " +
           "b.bookingDate = :tomorrow AND b.status IN ('CONFIRMED','RESCHEDULED')")
    List<Booking> findBookingsForTomorrowReminder(@Param("tomorrow") LocalDate tomorrow);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.bookingDate >= :startOfMonth AND b.bookingDate <= :endOfMonth")
    long countMonthlyBookings(@Param("startOfMonth") LocalDate startOfMonth, @Param("endOfMonth") LocalDate endOfMonth);

    boolean existsByPhotographerIdAndBookingDateAndStatusNot(String photographerId, LocalDate date, BookingStatus status);
}
