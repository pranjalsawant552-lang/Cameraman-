package com.cameraman.booking.repository;

import com.cameraman.booking.domain.OrderStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStageHistoryRepository extends JpaRepository<OrderStageHistory, String> {
    List<OrderStageHistory> findByBookingIdOrderByCreatedAtAsc(String bookingId);
}
