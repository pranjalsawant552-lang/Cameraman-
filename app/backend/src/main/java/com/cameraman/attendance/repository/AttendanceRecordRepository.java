package com.cameraman.attendance.repository;

import com.cameraman.attendance.domain.AttendanceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, String> {

    @Query("SELECT a FROM AttendanceRecord a WHERE " +
            "(:staffId IS NULL OR a.staffId = :staffId) AND " +
            "(:from IS NULL OR a.punchTime >= :from) AND " +
            "(:to IS NULL OR a.punchTime <= :to) " +
            "ORDER BY a.punchTime DESC")
    Page<AttendanceRecord> search(@Param("staffId") String staffId,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to,
                                   Pageable pageable);

    List<AttendanceRecord> findByPunchTimeBetweenOrderByPunchTimeAsc(LocalDateTime from, LocalDateTime to);

    @Query("SELECT DISTINCT a.employeeCode FROM AttendanceRecord a WHERE a.staffId IS NULL ORDER BY a.employeeCode")
    List<String> findDistinctUnmatchedEmployeeCodes();

    long countByStaffIdIsNull();

    /** True if this exact punch (code + time + device, with "no device" treated as one value) already exists. */
    @Query("SELECT COUNT(a) > 0 FROM AttendanceRecord a WHERE a.employeeCode = :code AND a.punchTime = :time " +
           "AND COALESCE(a.deviceSerial, '') = COALESCE(:serial, '')")
    boolean existsPunch(@Param("code") String code, @Param("time") LocalDateTime time, @Param("serial") String serial);

    interface UnmatchedRow {
        String getEmployeeCode();
        long getPunchCount();
        LocalDateTime getFirstSeen();
        LocalDateTime getLastSeen();
    }

    /** One row per unmatched code, grouped in the database instead of loading the whole table. */
    @Query("SELECT a.employeeCode AS employeeCode, COUNT(a) AS punchCount, MIN(a.punchTime) AS firstSeen, MAX(a.punchTime) AS lastSeen " +
           "FROM AttendanceRecord a WHERE a.staffId IS NULL GROUP BY a.employeeCode ORDER BY a.employeeCode")
    List<UnmatchedRow> findUnmatchedSummary();

    /** Bulk backfill when an admin links a raw code to a staff member. */
    @Modifying
    @Query("UPDATE AttendanceRecord a SET a.staffId = :staffId, a.staffName = :staffName " +
           "WHERE a.employeeCode = :code AND a.staffId IS NULL")
    int linkCodeToStaff(@Param("code") String code, @Param("staffId") String staffId, @Param("staffName") String staffName);
}
