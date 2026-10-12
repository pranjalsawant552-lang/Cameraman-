package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.AttendanceRecord;
import com.cameraman.attendance.domain.AttendanceSource;
import com.cameraman.attendance.domain.LeaveRequest;
import com.cameraman.attendance.domain.PunchDirection;
import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.repository.AttendanceRecordRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.settings.service.StudioSettingsService;
import com.cameraman.staff.domain.StaffProfile;
import com.cameraman.staff.repository.StaffRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the logic every ingestion path (manual, CSV, generic webhook, ZK protocol) shares:
 * resolving a device's raw employee code to a staff member, and the lenient parsing that
 * lets different sources send timestamps/directions in whatever format they use. This is
 * the first coverage this module has had since it was built.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceRecordRepository attendanceRepository;
    @Mock private com.cameraman.attendance.repository.LeaveRequestRepository leaveRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private UserRepository userRepository;
    @Mock private StudioSettingsService settingsService;
    @Mock private AttendanceDeviceService deviceService;
    @Mock private AttendanceScheduleService scheduleService;

    private AttendanceService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(attendanceRepository, leaveRepository, staffRepository, userRepository, settingsService, deviceService, scheduleService);
    }

    private StaffProfile staff(String id, String employeeId, String userId) {
        StaffProfile s = new StaffProfile();
        s.setId(id);
        s.setEmployeeId(employeeId);
        s.setUserId(userId);
        return s;
    }

    private User user(String id, String first, String last) {
        User u = new User();
        u.setId(id);
        u.setFirstName(first);
        u.setLastName(last);
        return u;
    }

    @Test
    void recordPunch_resolvesStaffByEmployeeCode() {
        when(staffRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(staff("staff-1", "EMP001", "user-1")));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user("user-1", "Priya", "Shah")));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRecord record = service.recordPunch("EMP001", LocalDateTime.now(), PunchDirection.IN,
                AttendanceSource.ZK_PROTOCOL, "SN12345", "raw line");

        assertThat(record.getStaffId()).isEqualTo("staff-1");
        assertThat(record.getStaffName()).isEqualTo("Priya Shah");
        assertThat(record.getDirection()).isEqualTo(PunchDirection.IN);
        assertThat(record.getSource()).isEqualTo(AttendanceSource.ZK_PROTOCOL);
    }

    @Test
    void recordPunch_unrecognizedCode_savesWithNullStaffId() {
        // This is the expected, non-error path for a device code nobody has linked yet —
        // it should still be recorded (surfacing in the Unmatched Codes screen), not dropped.
        when(staffRepository.findByEmployeeId("UNKNOWN99")).thenReturn(Optional.empty());
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRecord record = service.recordPunch("UNKNOWN99", LocalDateTime.now(), PunchDirection.UNKNOWN,
                AttendanceSource.GENERIC_WEBHOOK, null, null);

        assertThat(record.getStaffId()).isNull();
        assertThat(record.getEmployeeCode()).isEqualTo("UNKNOWN99");
        verify(userRepository, never()).findById(any());
    }

    @Test
    void recordPunch_blankEmployeeCode_throwsBusinessException() {
        assertThatThrownBy(() -> service.recordPunch("", LocalDateTime.now(), PunchDirection.IN,
                AttendanceSource.MANUAL, null, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(attendanceRepository);
    }

    @Test
    void verifyWebhookKey_rejectsWrongOrMissingKey() {
        when(settingsService.getOrCreateAttendanceWebhookSecret()).thenReturn("correct-secret");

        assertThatThrownBy(() -> service.verifyWebhookKey("wrong-secret")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.verifyWebhookKey(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void verifyWebhookKey_acceptsCorrectKey() {
        when(settingsService.getOrCreateAttendanceWebhookSecret()).thenReturn("correct-secret");
        service.verifyWebhookKey("correct-secret"); // must not throw
    }

    @Test
    void ingestZkAttendanceLog_parsesTabSeparatedPushProtocolFormat() {
        when(staffRepository.findByEmployeeId(any())).thenReturn(Optional.empty());
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // Real iClock push format: PIN \t time \t status \t verify-mode \t work-code
        String body = "1001\t2026-08-08 09:05:00\t0\t1\t0\n1002\t2026-08-08 09:07:00\t0\t1\t0\n";
        int count = service.ingestZkAttendanceLog("SN98765", body);

        assertThat(count).isEqualTo(2);
        verify(attendanceRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void ingestZkAttendanceLog_skipsUnparseableLinesWithoutFailingTheWholeBatch() {
        when(staffRepository.findByEmployeeId(any())).thenReturn(Optional.empty());
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        String body = "1001\t2026-08-08 09:05:00\t0\n" + "garbage-line-no-tabs\n" + "1002\t2026-08-08 09:07:00\t0\n";
        int count = service.ingestZkAttendanceLog("SN98765", body);

        assertThat(count).isEqualTo(2); // the garbage line is silently skipped, not fatal
    }

    @Test
    void ingestZkAttendanceLog_emptyBody_returnsZero() {
        assertThat(service.ingestZkAttendanceLog("SN1", "")).isZero();
        assertThat(service.ingestZkAttendanceLog("SN1", null)).isZero();
        verifyNoInteractions(attendanceRepository);
    }

    @Test
    void importCsv_parsesValidRowsAndReportsErrorsForBadOnes() {
        when(staffRepository.findByEmployeeId(any())).thenReturn(Optional.empty());
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        String csv = "Employee Code,Timestamp,Direction\n"
                + "EMP001,2026-08-08 09:05:00,IN\n"
                + "EMP001,08/08/2026 18:10:00,OUT\n" // dd/MM/yyyy format, also supported
                + ",2026-08-08 09:05:00,IN\n"          // missing employee code — should error
                + "EMP002,not-a-date,IN\n";            // unparseable timestamp — should error

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csv.getBytes());
        AttendanceDto.ImportResult result = service.importCsv(file);

        assertThat(result.getTotalRows()).isEqualTo(4);
        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getSkipped()).isEqualTo(2);
        assertThat(result.getErrors()).hasSize(2);
    }

    @Test
    void parseDirection_acceptsCommonVariants_viaManualEntry() {
        StaffProfile s = staff("staff-1", "EMP001", "user-1");
        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(s));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user("user-1", "Rahul", "Kumar")));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AttendanceDto.ManualEntryRequest req = new AttendanceDto.ManualEntryRequest();
        req.setStaffId("staff-1");
        req.setPunchTime(LocalDateTime.now());
        req.setDirection("check-in"); // lowercase, hyphenated — should still resolve to IN

        AttendanceDto.RecordResponse response = service.manualEntry(req);
        assertThat(response.getDirection()).isEqualTo("IN");
    }

    @Test
    void monthlyReport_classifiesEveryDayCorrectly() {
        // Last month is guaranteed fully in the past relative to "today" — avoids the
        // test being flaky depending on which day of the current month it happens to run.
        java.time.YearMonth testMonth = java.time.YearMonth.now().minusMonths(1);
        LocalDate presentDay = firstDayOfWeek(testMonth, java.time.DayOfWeek.MONDAY);
        LocalDate lateDay = firstDayOfWeek(testMonth, java.time.DayOfWeek.TUESDAY);
        LocalDate absentDay = firstDayOfWeek(testMonth, java.time.DayOfWeek.WEDNESDAY);
        LocalDate leaveDay = firstDayOfWeek(testMonth, java.time.DayOfWeek.THURSDAY);
        LocalDate sunday = firstDayOfWeek(testMonth, java.time.DayOfWeek.SUNDAY);

        AttendanceRecord onTimePunch = AttendanceRecord.builder()
                .employeeCode("EMP001").staffId("staff-1")
                .punchTime(presentDay.atTime(9, 0)).direction(PunchDirection.IN)
                .source(AttendanceSource.MANUAL).build();
        AttendanceRecord latePunch = AttendanceRecord.builder()
                .employeeCode("EMP001").staffId("staff-1")
                .punchTime(lateDay.atTime(10, 0)).direction(PunchDirection.IN) // shift 09:30 + 15min grace = 09:45 cutoff
                .source(AttendanceSource.MANUAL).build();

        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(staff("staff-1", "EMP001", "user-1")));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user("user-1", "Anita", "Rao")));
        AttendanceScheduleService.Context ctx = new AttendanceScheduleService.Context();
        ctx.defaultSchedule = new AttendanceCalculator.Schedule(java.time.LocalTime.of(9, 30), java.time.LocalTime.of(18, 30),
                15, java.util.Set.of(java.time.DayOfWeek.SUNDAY)); // 09:30 + 15 min grace = 09:45 cutoff
        ctx.rules = new AttendanceCalculator.Rules(0, 30);
        when(scheduleService.loadContext(any(), any())).thenReturn(ctx);
        when(userRepository.findAllById(any())).thenReturn(java.util.List.of(user("user-1", "Anita", "Rao")));
        when(attendanceRepository.findByPunchTimeBetweenOrderByPunchTimeAsc(any(), any()))
                .thenReturn(java.util.List.of(onTimePunch, latePunch));

        LeaveRequest approvedLeave = LeaveRequest.builder()
                .staffId("staff-1").fromDate(leaveDay).toDate(leaveDay).status(com.cameraman.attendance.domain.LeaveStatus.APPROVED).build();
        when(leaveRepository.findApprovedOverlappingAnyStaff(any(), any()))
                .thenReturn(java.util.List.of(approvedLeave));

        AttendanceDto.MonthlyReport report = service.monthlyReport("staff-1", testMonth.getYear(), testMonth.getMonthValue());

        assertThat(dayStatus(report, presentDay)).isEqualTo("PRESENT");
        assertThat(dayStatus(report, lateDay)).isEqualTo("LATE");
        assertThat(dayStatus(report, absentDay)).isEqualTo("ABSENT");
        assertThat(dayStatus(report, leaveDay)).isEqualTo("ON_LEAVE");
        assertThat(dayStatus(report, sunday)).isEqualTo("WEEKLY_OFF");

        assertThat(report.getPresentDays()).isEqualTo(1);
        assertThat(report.getLateDays()).isEqualTo(1);
        assertThat(report.getLeaveDays()).isEqualTo(1);
        // Every day in the month lands in exactly one bucket — this catches an off-by-one
        // in the day-iteration loop that would otherwise silently drop or double-count a day.
        int totalClassified = report.getPresentDays() + report.getLateDays() + report.getAbsentDays()
                + report.getLeaveDays() + report.getWeeklyOffDays() + report.getHalfDays() + report.getHolidayDays();
        assertThat(totalClassified).isEqualTo(testMonth.lengthOfMonth());
    }

    private LocalDate firstDayOfWeek(java.time.YearMonth month, java.time.DayOfWeek dayOfWeek) {
        LocalDate day = month.atDay(1);
        while (day.getDayOfWeek() != dayOfWeek) { day = day.plusDays(1); }
        return day;
    }

    private String dayStatus(AttendanceDto.MonthlyReport report, LocalDate date) {
        return report.getDays().stream()
                .filter(d -> d.getDate().equals(date))
                .findFirst().orElseThrow().getStatus();
    }
}
