package com.cameraman.attendance.repository;

import com.cameraman.attendance.domain.AttendanceCorrection;
import com.cameraman.attendance.domain.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, String> {
    List<AttendanceCorrection> findByStaffIdOrderByCreatedAtDesc(String staffId);
    List<AttendanceCorrection> findByStatusOrderByCreatedAtAsc(LeaveStatus status);
    boolean existsByStaffIdAndProposedTimeAndStatus(String staffId, LocalDateTime proposedTime, LeaveStatus status);
}
