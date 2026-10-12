package com.cameraman.attendance.repository;

import com.cameraman.attendance.domain.LeaveRequest;
import com.cameraman.attendance.domain.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, String> {

    List<LeaveRequest> findByStaffIdOrderByFromDateDesc(String staffId);

    List<LeaveRequest> findByStatusOrderByFromDateAsc(LeaveStatus status);

    @Query("SELECT l FROM LeaveRequest l WHERE l.staffId = :staffId AND l.status = 'APPROVED' " +
            "AND l.fromDate <= :to AND l.toDate >= :from")
    List<LeaveRequest> findApprovedOverlapping(@Param("staffId") String staffId,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    @Query("SELECT l FROM LeaveRequest l WHERE l.status = 'APPROVED' " +
            "AND l.fromDate <= :to AND l.toDate >= :from")
    List<LeaveRequest> findApprovedOverlappingAnyStaff(@Param("from") LocalDate from,
                                                         @Param("to") LocalDate to);

    /** Any pending or approved leave of this staff member that touches the date range. */
    @Query("SELECT COUNT(l) > 0 FROM LeaveRequest l WHERE l.staffId = :staffId AND l.status IN ('PENDING','APPROVED') " +
            "AND l.fromDate <= :to AND l.toDate >= :from")
    boolean existsActiveOverlapping(@Param("staffId") String staffId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
