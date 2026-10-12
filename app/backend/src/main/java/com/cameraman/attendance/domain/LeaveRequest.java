package com.cameraman.attendance.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A staff member's leave application. Exists specifically so the attendance module can
 * tell the difference between "didn't show up" (a real absence) and "approved day off"
 * (expected, not a problem) — without this, every leave day would incorrectly show up as
 * an absence in the monthly report.
 */
@Entity
@Table(name = "leave_requests", indexes = {
        @Index(name = "idx_leave_staff_dates", columnList = "staff_id,from_date,to_date"),
        @Index(name = "idx_leave_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveRequest extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "staff_id", nullable = false)
    private String staffId;

    @Column(name = "staff_name")
    private String staffName;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_by_name")
    private String reviewedByName;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_notes", length = 500)
    private String reviewNotes;

    /** True if [day] falls within this leave's date range — used by the monthly report
     *  to mark a day ON_LEAVE instead of ABSENT. Only meaningful for APPROVED requests;
     *  callers filter by status separately. */
    public boolean covers(LocalDate day) {
        return !day.isBefore(fromDate) && !day.isAfter(toDate);
    }
}
