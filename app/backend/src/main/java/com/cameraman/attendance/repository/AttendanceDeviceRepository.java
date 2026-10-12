package com.cameraman.attendance.repository;

import com.cameraman.attendance.domain.AttendanceDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttendanceDeviceRepository extends JpaRepository<AttendanceDevice, String> {

    List<AttendanceDevice> findAllByOrderByLastSeenAtDesc();

    @Modifying
    @Query("UPDATE AttendanceDevice d SET d.punchCount = d.punchCount + :n, d.lastSeenAt = :now WHERE d.serial = :serial")
    int addPunches(@Param("serial") String serial, @Param("n") long n, @Param("now") LocalDateTime now);
}
