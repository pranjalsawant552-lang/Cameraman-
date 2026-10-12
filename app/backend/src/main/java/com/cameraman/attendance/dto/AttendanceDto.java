package com.cameraman.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

public class AttendanceDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RecordResponse {
        private String id;
        private String employeeCode;
        private String staffId;
        private String staffName;
        private LocalDateTime punchTime;
        private String direction;
        private String source;
        private String deviceSerial;
        private String notes;
        private LocalDateTime createdAt;
    }

    /** POST /attendance — manual entry, works regardless of hardware. */
    @Getter @Setter
    public static class ManualEntryRequest {
        @NotBlank
        private String staffId;
        @NotNull
        private LocalDateTime punchTime;
        private String direction; // IN / OUT / UNKNOWN, defaults to UNKNOWN
        private String notes;
    }

    /** POST /webhooks/attendance/generic — the vendor-neutral push path. Any device,
     *  middleware, or bridge script that can make an HTTP POST can use this. */
    @Getter @Setter
    public static class GenericWebhookRequest {
        @NotBlank
        private String key;
        @NotBlank
        private String employeeCode;
        @NotNull
        private LocalDateTime timestamp;
        private String direction;
        private String deviceSerial;
    }

    @Getter @Setter @Builder
    public static class UnmatchedCode {
        private String employeeCode;
        private long punchCount;
        private LocalDateTime lastSeen;
    }

    @Getter @Setter
    public static class LinkCodeRequest {
        @NotBlank
        private String employeeCode;
        @NotBlank
        private String staffId;
    }

    @Getter @Setter @Builder
    public static class ImportResult {
        private int totalRows;
        private int imported;
        private int skipped;
        /** Rows that were already stored (same code, time and device) and were ignored. */
        private int duplicates;
        private java.util.List<String> errors;
    }

    @Getter @Setter @Builder
    public static class DaySummary {
        private String staffId;
        private String staffName;
        private LocalDateTime firstPunch;
        private LocalDateTime lastPunch;
        private int punchCount;
        private int workedMinutes;
        private int lateMinutes;
        private int overtimeMinutes;
        /** PRESENT/LATE/HALF_DAY/ABSENT/ON_LEAVE/WEEKLY_OFF/HOLIDAY/FUTURE — same computation
         *  as MonthlyReport's per-day status, just for a single day across
         *  every staff member instead of every day for one staff member. */
        private String status;
    }

    @Getter @Setter @Builder
    public static class WebhookConfig {
        private String secret;
        private String genericWebhookUrl;
        private String iclockBaseUrl;
    }

    // ─────────────────────────────── Leave ───────────────────────────────

    @Getter @Setter
    public static class LeaveApplyRequest {
        @NotBlank
        private String staffId;
        @NotNull
        private java.time.LocalDate fromDate;
        @NotNull
        private java.time.LocalDate toDate;
        private String reason;
    }

    @Getter @Setter
    public static class LeaveReviewRequest {
        private String notes;
    }

    @Getter @Setter @Builder
    public static class LeaveResponse {
        private String id;
        private String staffId;
        private String staffName;
        private java.time.LocalDate fromDate;
        private java.time.LocalDate toDate;
        private long dayCount;
        private String reason;
        private String status;
        private String reviewedByName;
        private LocalDateTime reviewedAt;
        private String reviewNotes;
        private LocalDateTime createdAt;
    }

    // ─────────────────────────────── Monthly report ───────────────────────────────

    @Getter @Setter @Builder
    public static class DayStatus {
        private java.time.LocalDate date;
        private String status; // PRESENT / LATE / HALF_DAY / ABSENT / ON_LEAVE / WEEKLY_OFF / HOLIDAY / FUTURE
        private LocalDateTime firstPunch;
        private LocalDateTime lastPunch;
        private int punchCount;
        private int workedMinutes;
        private int lateMinutes;
        private int earlyLeaveMinutes;
        private int overtimeMinutes;
        /** One punch only: the day has an arrival but no departure. */
        private boolean missingOut;
        private String holidayName;
    }

    @Getter @Setter @Builder
    public static class MonthlyReport {
        private String staffId;
        private String staffName;
        private int year;
        private int month;
        private java.util.List<DayStatus> days;
        private int presentDays;
        private int lateDays;
        private int absentDays;
        private int leaveDays;
        private int weeklyOffDays;
        private int halfDays;
        private int holidayDays;
        /** Days the staff member was expected to work this month (month length minus weekly offs and holidays). */
        private int workingDays;
        private long workedMinutes;
        private long lateMinutes;
        private long overtimeMinutes;
        private String shiftName;
    }

    @Getter @Setter
    public static class WorkHoursConfig {
        private String shiftStart; // "HH:mm"
        private int graceMinutes;
        private String weeklyOffDay; // day-of-week name, e.g. SUNDAY
        private String shiftEnd; // "HH:mm"
        private Integer halfDayMinutes;
        private Integer minOvertimeMinutes;
        private Integer latesPerHalfDay;
    }

    // ─────────────────────────────── Shifts, schedules, holidays ───────────────────────────────

    @Getter @Setter
    public static class ShiftRequest {
        @NotBlank @jakarta.validation.constraints.Size(max = 80)
        private String name;
        @NotBlank
        private String startTime; // "HH:mm"
        @NotBlank
        private String endTime;   // "HH:mm"
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(240)
        private Integer graceMinutes;
        private Boolean active;
    }

    @Getter @Setter @Builder
    public static class ShiftResponse {
        private String id;
        private String name;
        private String startTime;
        private String endTime;
        private int graceMinutes;
        private boolean active;
        private long staffCount;
    }

    @Getter @Setter
    public static class ScheduleRequest {
        /** Null = follow the studio default shift. */
        private String shiftId;
        /** Day names; empty/null = follow the studio default. */
        private java.util.List<String> weeklyOffDays;
        @jakarta.validation.constraints.DecimalMin("0")
        private java.math.BigDecimal monthlySalary;
        @jakarta.validation.constraints.DecimalMin("0")
        private java.math.BigDecimal overtimeRatePerHour;
    }

    @Getter @Setter @Builder
    public static class ScheduleResponse {
        private String staffId;
        private String staffName;
        private String employeeId;
        private String department;
        private String shiftId;
        private String shiftName;
        private String effectiveStart;
        private String effectiveEnd;
        private java.util.List<String> weeklyOffDays;
        private boolean usesDefaultShift;
        private java.math.BigDecimal monthlySalary;
        private java.math.BigDecimal overtimeRatePerHour;
    }

    @Getter @Setter
    public static class HolidayRequest {
        @NotNull
        private java.time.LocalDate date;
        @NotBlank @jakarta.validation.constraints.Size(max = 100)
        private String name;
    }

    @Getter @Setter @Builder
    public static class HolidayResponse {
        private String id;
        private java.time.LocalDate date;
        private String name;
    }

    // ─────────────────────────────── Payroll ───────────────────────────────

    @Getter @Setter @Builder
    public static class PayrollRow {
        private String staffId;
        private String staffName;
        private String employeeId;
        private String department;
        private int workingDays;
        private int presentDays;
        private int lateDays;
        private int halfDays;
        private int absentDays;
        private int leaveDays;
        private int weeklyOffDays;
        private int holidayDays;
        private double workedHours;
        private double overtimeHours;
        /** Days of pay lost: absences + half of each half day + late penalties. */
        private double unpaidDays;
        private double latePenaltyDays;
        private java.math.BigDecimal monthlySalary;
        private java.math.BigDecimal perDayRate;
        private java.math.BigDecimal deduction;
        private java.math.BigDecimal overtimePay;
        private java.math.BigDecimal netPay;
    }

    @Getter @Setter @Builder
    public static class PayrollReport {
        private int year;
        private int month;
        /** True while the month is not over: absences so far only, figures will still change. */
        private boolean monthInProgress;
        private java.util.List<PayrollRow> rows;
        private java.math.BigDecimal totalNetPay;
    }
}
