package com.cameraman.attendance.repository;

import com.cameraman.attendance.domain.AttendanceHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceHolidayRepository extends JpaRepository<AttendanceHoliday, String> {
    List<AttendanceHoliday> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);
    boolean existsByHolidayDate(LocalDate date);
}
