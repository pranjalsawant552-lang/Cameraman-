package com.cameraman.booking.repository;

import com.cameraman.booking.domain.BlockedDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BlockedDateRepository extends JpaRepository<BlockedDate, String> {
    boolean existsByDate(LocalDate date);
    Optional<BlockedDate> findByDate(LocalDate date);
    List<BlockedDate> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
    List<BlockedDate> findByDateGreaterThanEqualOrderByDateAsc(LocalDate from);
    void deleteByDate(LocalDate date);
}
