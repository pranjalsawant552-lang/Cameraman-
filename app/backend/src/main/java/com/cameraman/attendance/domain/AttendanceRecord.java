package com.cameraman.attendance.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One punch/scan event. Deliberately vendor-neutral: nothing here assumes a
 * particular biometric machine's SDK or data format. Every ingestion path
 * (manual entry, CSV import, generic webhook, ZKTeco/ADMS-protocol push)
 * converges on this same shape — see AttendanceSource for how a given row
 * got here, and AttendanceIngestService for the parsing/mapping logic per
 * source.
 */
@Entity
@Table(name = "attendance_records", indexes = {
        @Index(name = "idx_attendance_staff_time", columnList = "staff_id,punch_time"),
        @Index(name = "idx_attendance_employee_code", columnList = "employee_code"),
        @Index(name = "idx_attendance_punch_time", columnList = "punch_time")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceRecord extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Raw ID the device/import reported for the person (its own user code,
     *  RFID card number, etc.) — kept even after resolution below so the
     *  original source data is never lost. */
    @Column(name = "employee_code", nullable = false, length = 64)
    private String employeeCode;

    /** Resolved once employeeCode is mapped to a StaffProfile.employeeId.
     *  Null means "seen from a device but nobody has linked this code to a
     *  staff member yet" — surfaced in the Unmatched Codes screen. */
    @Column(name = "staff_id")
    private String staffId;

    @Column(name = "staff_name")
    private String staffName;

    @Column(name = "punch_time", nullable = false)
    private LocalDateTime punchTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 20)
    @Builder.Default
    private PunchDirection direction = PunchDirection.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private AttendanceSource source;

    /** Free-text device identifier — a machine serial number for
     *  protocol/webhook pushes, a filename for CSV imports, or null for
     *  manual entry. Purely informational (which machine reported this),
     *  never used for auth. */
    @Column(name = "device_serial", length = 100)
    private String deviceSerial;

    /** The untouched original line/payload for this punch, kept so a
     *  mis-parsed row can always be traced back and re-processed by hand
     *  instead of being silently lost. */
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "notes", length = 255)
    private String notes;
}
