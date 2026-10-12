package com.cameraman.attendance.repository;

import com.cameraman.attendance.domain.StaffSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffScheduleRepository extends JpaRepository<StaffSchedule, String> {
    Optional<StaffSchedule> findByStaffId(String staffId);
    long countByShiftId(String shiftId);
}
