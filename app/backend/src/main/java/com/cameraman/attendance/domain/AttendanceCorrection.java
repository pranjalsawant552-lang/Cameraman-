package com.cameraman.attendance.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_corrections")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceCorrection {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "staff_id", nullable = false)
    private String staffId;

    @Column(name = "staff_name")
    private String staffName;

    @Column(name = "proposed_time", nullable = false)
    private LocalDateTime proposedTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PunchDirection direction = PunchDirection.UNKNOWN;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
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

    @Column(name = "record_id")
    private String recordId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
