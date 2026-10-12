package com.cameraman.booking.repository;

import com.cameraman.booking.domain.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, String> {
    List<TimeSlot> findBySlotDateAndAvailableTrue(LocalDate date);
    List<TimeSlot> findBySlotDate(LocalDate date);
    boolean existsBySlotDateAndStartTime(LocalDate date, java.time.LocalTime startTime);
}
