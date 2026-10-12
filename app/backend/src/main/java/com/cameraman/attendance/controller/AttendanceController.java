package com.cameraman.attendance.controller;

import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.service.AttendanceScheduleService;
import com.cameraman.attendance.service.AttendanceService;
import com.cameraman.attendance.service.PayrollService;
import com.cameraman.common.security.CurrentUser;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "Staff attendance — device-agnostic ingestion (manual, CSV, webhook, ZK protocol)")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST')")
public class AttendanceController {

    private static final String VIEW = "hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:ATTENDANCE:VIEW')";
    private static final String MANAGE = "hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ATTENDANCE:UPDATE')";
    /** Salary figures: managers, accountants, or a custom role granted ATTENDANCE:EXPORT. */
    private static final String PAYROLL = "hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT') or hasAuthority('CUSTOM:ATTENDANCE:EXPORT')";

    private final AttendanceService attendanceService;
    private final AttendanceScheduleService scheduleService;
    private final PayrollService payrollService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:ATTENDANCE:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<AttendanceDto.RecordResponse>>> search(
            @RequestParam(required = false) String staffId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                attendanceService.search(staffId, from, to, PageRequest.of(page, size, Sort.by("punchTime").descending()))));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:ATTENDANCE:VIEW')")
    public ResponseEntity<ApiResponse<List<AttendanceDto.DaySummary>>> daySummary(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.daySummary(date)));
    }

    @GetMapping("/monthly-report")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:ATTENDANCE:VIEW')")
    public ResponseEntity<ApiResponse<AttendanceDto.MonthlyReport>> monthlyReport(
            @RequestParam String staffId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.monthlyReport(staffId, year, month)));
    }

    @GetMapping("/work-hours-config")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:ATTENDANCE:VIEW')")
    public ResponseEntity<ApiResponse<AttendanceDto.WorkHoursConfig>> workHoursConfig() {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getWorkHoursConfig()));
    }

    @PutMapping("/work-hours-config")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:ATTENDANCE:UPDATE')")
    public ResponseEntity<ApiResponse<AttendanceDto.WorkHoursConfig>> updateWorkHoursConfig(
            @RequestBody AttendanceDto.WorkHoursConfig config) {
        return ResponseEntity.ok(ApiResponse.success("Work hours updated", attendanceService.updateWorkHoursConfig(config)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:ATTENDANCE:CREATE')")
    public ResponseEntity<ApiResponse<AttendanceDto.RecordResponse>> manualEntry(
            @Valid @RequestBody AttendanceDto.ManualEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Punch recorded", attendanceService.manualEntry(request)));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:ATTENDANCE:UPDATE')")
    public ResponseEntity<ApiResponse<AttendanceDto.ImportResult>> importCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Import complete", attendanceService.importCsv(file)));
    }

    @GetMapping("/unmatched")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:ATTENDANCE:VIEW')")
    public ResponseEntity<ApiResponse<List<AttendanceDto.UnmatchedCode>>> unmatched() {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getUnmatchedCodes()));
    }

    @PostMapping("/link")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:ATTENDANCE:UPDATE')")
    public ResponseEntity<ApiResponse<Void>> link(@Valid @RequestBody AttendanceDto.LinkCodeRequest request) {
        attendanceService.linkCode(request);
        return ResponseEntity.ok(ApiResponse.success("Employee code linked", null));
    }

    @GetMapping("/webhook-config")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:ATTENDANCE:VIEW')")
    public ResponseEntity<ApiResponse<AttendanceDto.WebhookConfig>> webhookConfig() {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getWebhookConfig()));
    }

    @PostMapping("/webhook-config/regenerate")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:ATTENDANCE:UPDATE')")
    public ResponseEntity<ApiResponse<AttendanceDto.WebhookConfig>> regenerateSecret() {
        return ResponseEntity.ok(ApiResponse.success("A device configured with the old key will need updating", attendanceService.regenerateSecret()));
    }

    // ───────────── Shifts, per-staff schedules, holidays ─────────────

    @GetMapping("/shifts")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<AttendanceDto.ShiftResponse>>> shifts() {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.listShifts()));
    }

    @PostMapping("/shifts")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AttendanceDto.ShiftResponse>> createShift(
            @Valid @RequestBody AttendanceDto.ShiftRequest request, @CurrentUser String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Shift created", scheduleService.createShift(request, userId)));
    }

    @PutMapping("/shifts/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AttendanceDto.ShiftResponse>> updateShift(
            @PathVariable String id, @Valid @RequestBody AttendanceDto.ShiftRequest request, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Shift updated", scheduleService.updateShift(id, request, userId)));
    }

    @DeleteMapping("/shifts/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Void>> deleteShift(@PathVariable String id, @CurrentUser String userId) {
        scheduleService.deleteShift(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Shift deleted", null));
    }

    /** Includes salary and overtime rate, so it is limited to payroll roles. */
    @GetMapping("/schedules")
    @PreAuthorize(PAYROLL)
    public ResponseEntity<ApiResponse<List<AttendanceDto.ScheduleResponse>>> schedules() {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.listSchedules()));
    }

    @PutMapping("/schedules/{staffId}")
    @PreAuthorize(PAYROLL)
    public ResponseEntity<ApiResponse<AttendanceDto.ScheduleResponse>> updateSchedule(
            @PathVariable String staffId, @Valid @RequestBody AttendanceDto.ScheduleRequest request, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Schedule updated", scheduleService.updateSchedule(staffId, request, userId)));
    }

    @GetMapping("/holidays")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<AttendanceDto.HolidayResponse>>> holidays(@RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.listHolidays(year)));
    }

    @PostMapping("/holidays")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AttendanceDto.HolidayResponse>> addHoliday(
            @Valid @RequestBody AttendanceDto.HolidayRequest request, @CurrentUser String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Holiday added", scheduleService.addHoliday(request, userId)));
    }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Void>> deleteHoliday(@PathVariable String id, @CurrentUser String userId) {
        scheduleService.deleteHoliday(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Holiday removed", null));
    }

    // ───────────── Payroll ─────────────

    @GetMapping("/payroll")
    @PreAuthorize(PAYROLL)
    public ResponseEntity<ApiResponse<AttendanceDto.PayrollReport>> payroll(@RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(ApiResponse.success(payrollService.report(year, month)));
    }

    @GetMapping("/payroll/export")
    @PreAuthorize(PAYROLL)
    public ResponseEntity<byte[]> exportPayroll(@RequestParam int year, @RequestParam int month) {
        byte[] csv = payrollService.exportCsv(year, month);
        String filename = String.format("payroll-%d-%02d.csv", year, month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    private final com.cameraman.attendance.service.AttendanceDeviceService deviceService;

    @GetMapping("/devices")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:ATTENDANCE:VIEW')")
    public ResponseEntity<ApiResponse<List<com.cameraman.attendance.domain.AttendanceDevice>>> devices() {
        return ResponseEntity.ok(ApiResponse.success(deviceService.list()));
    }

    public record DeviceUpdate(Boolean allowed, String label) {}

    @PatchMapping("/devices/{serial}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:ATTENDANCE:UPDATE')")
    public ResponseEntity<ApiResponse<com.cameraman.attendance.domain.AttendanceDevice>> updateDevice(
            @PathVariable String serial, @RequestBody DeviceUpdate body) {
        return ResponseEntity.ok(ApiResponse.success("Device updated", deviceService.update(serial, body.allowed(), body.label())));
    }
}
