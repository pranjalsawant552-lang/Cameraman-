package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.AttendanceRecord;
import com.cameraman.attendance.domain.AttendanceSource;
import com.cameraman.attendance.domain.PunchDirection;
import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.repository.AttendanceRecordRepository;
import com.cameraman.attendance.repository.LeaveRequestRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.settings.service.StudioSettingsService;
import com.cameraman.staff.domain.StaffProfile;
import com.cameraman.staff.repository.StaffRepository;
import com.cameraman.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceHardeningTest {

    @Mock private AttendanceRecordRepository repo;
    @Mock private LeaveRequestRepository leaveRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private UserRepository userRepository;
    @Mock private StudioSettingsService settingsService;
    @Mock private AttendanceDeviceService deviceService;
    @Mock private AttendanceScheduleService scheduleService;

    private AttendanceService service;
    private final LocalDateTime t = LocalDateTime.of(2026, 10, 5, 9, 30, 0);

    @BeforeEach
    void setUp() {
        service = new AttendanceService(repo, leaveRepository, staffRepository, userRepository, settingsService, deviceService, scheduleService);
        when(repo.saveAndFlush(any(AttendanceRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(settingsService.getOrCreateAttendanceWebhookSecret()).thenReturn("secret");
    }

    // ───────── idempotency ─────────

    @Test
    void repeatedPunch_isIgnored_notStoredTwice() {
        when(repo.existsPunch("E1", t, "SN1")).thenReturn(true);
        Optional<AttendanceRecord> res = service.recordPunchIfNew("E1", t, PunchDirection.IN, AttendanceSource.ZK_PROTOCOL, "SN1", "x");
        assertThat(res).isEmpty();
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void uniqueIndexRace_isTreatedAsDuplicate() {
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        assertThat(service.recordPunchIfNew("E1", t, PunchDirection.IN, AttendanceSource.MANUAL, null, null)).isEmpty();
    }

    @Test
    void blankDeviceSerial_isNormalisedToNull() {
        service.recordPunchIfNew("E1", t, PunchDirection.IN, AttendanceSource.MANUAL, "  ", null);
        ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(repo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getDeviceSerial()).isNull();
    }

    @Test
    void zkBatch_countsOnlyNewPunches_andReportsToDevice() {
        when(repo.existsPunch("1", LocalDateTime.of(2026, 10, 5, 9, 0, 0), "SN9")).thenReturn(true);
        String body = "1\t2026-10-05 09:00:00\t0\t1\n2\t2026-10-05 09:05:00\t1\t1\n";
        assertThat(service.ingestZkAttendanceLog("SN9", body)).isEqualTo(1);
        verify(deviceService).addPunches("SN9", 1);
    }

    @Test
    void csvImport_reportsDuplicatesSeparately() throws Exception {
        // CSV rows have no device, so overlapping exports (any filename) are de-duplicated against each other
        when(repo.existsPunch("E1", t, null)).thenReturn(true);
        String csv = "Employee Code,Timestamp\nE1,2026-10-05 09:30:00\nE2,2026-10-05 10:00:00\nE3,not-a-date\n";
        MockMultipartFile file = new MockMultipartFile("file", "punches.csv", "text/csv", csv.getBytes());
        AttendanceDto.ImportResult r = service.importCsv(file);
        assertThat(r.getTotalRows()).isEqualTo(3);
        assertThat(r.getImported()).isEqualTo(1);
        assertThat(r.getDuplicates()).isEqualTo(1);
        assertThat(r.getSkipped()).isEqualTo(1);
    }

    // ───────── ZK direction ─────────

    @Test
    void zkStatus_mapsInOutAndUnknown() {
        assertThat(AttendanceService.zkStatusToDirection("0")).isEqualTo(PunchDirection.IN);
        assertThat(AttendanceService.zkStatusToDirection("4")).isEqualTo(PunchDirection.IN);
        assertThat(AttendanceService.zkStatusToDirection("1")).isEqualTo(PunchDirection.OUT);
        assertThat(AttendanceService.zkStatusToDirection("5")).isEqualTo(PunchDirection.OUT);
        assertThat(AttendanceService.zkStatusToDirection("2")).isEqualTo(PunchDirection.UNKNOWN);
        assertThat(AttendanceService.zkStatusToDirection("")).isEqualTo(PunchDirection.UNKNOWN);
        assertThat(AttendanceService.zkStatusToDirection(null)).isEqualTo(PunchDirection.UNKNOWN);
    }

    @Test
    void zkBatch_storesParsedDirection() {
        service.ingestZkAttendanceLog("SN9", "7\t2026-10-05 18:00:00\t1\t1\n");
        ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(repo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getDirection()).isEqualTo(PunchDirection.OUT);
    }

    // ───────── no table scans ─────────

    @Test
    void unmatchedCodes_comeFromTheGroupedQuery() {
        AttendanceRecordRepository.UnmatchedRow row = new AttendanceRecordRepository.UnmatchedRow() {
            public String getEmployeeCode() { return "99"; }
            public long getPunchCount() { return 4; }
            public LocalDateTime getFirstSeen() { return t.minusDays(2); }
            public LocalDateTime getLastSeen() { return t; }
        };
        when(repo.findUnmatchedSummary()).thenReturn(List.of(row));
        List<AttendanceDto.UnmatchedCode> res = service.getUnmatchedCodes();
        assertThat(res).hasSize(1);
        assertThat(res.get(0).getPunchCount()).isEqualTo(4);
        verify(repo, never()).findAll();
    }

    @Test
    void linkCode_usesOneBulkUpdate() {
        StaffProfile staff = StaffProfile.builder().id("s1").userId("u1").build();
        when(staffRepository.findById("s1")).thenReturn(Optional.of(staff));
        when(staffRepository.findByEmployeeId("99")).thenReturn(Optional.empty());
        AttendanceDto.LinkCodeRequest req = new AttendanceDto.LinkCodeRequest();
        req.setStaffId("s1");
        req.setEmployeeCode("99");
        service.linkCode(req);
        verify(repo).linkCodeToStaff(eq("99"), eq("s1"), any());
        verify(repo, never()).findAll();
        assertThat(staff.getEmployeeId()).isEqualTo("99");
    }

    @Test
    void linkCode_cannotStealACodeOwnedByAnotherStaff() {
        StaffProfile me = StaffProfile.builder().id("s1").userId("u1").build();
        StaffProfile other = StaffProfile.builder().id("s2").userId("u2").employeeId("99").build();
        when(staffRepository.findById("s1")).thenReturn(Optional.of(me));
        when(staffRepository.findByEmployeeId("99")).thenReturn(Optional.of(other));
        AttendanceDto.LinkCodeRequest req = new AttendanceDto.LinkCodeRequest();
        req.setStaffId("s1");
        req.setEmployeeCode("99");
        assertThatThrownBy(() -> service.linkCode(req)).isInstanceOf(BusinessException.class);
        verify(repo, never()).linkCodeToStaff(anyString(), anyString(), any());
    }

    // ───────── device authorization ─────────

    @Test
    void validKey_isAccepted_andDeviceIsRecorded() {
        service.verifyDeviceRequest("SN1", "secret");
        verify(deviceService).noteSeen("SN1");
    }

    @Test
    void unknownDevice_withoutKey_isRejected_butRegisteredForApproval() {
        when(deviceService.noteSeen("SN-NEW")).thenReturn(false);
        assertThatThrownBy(() -> service.verifyDeviceRequest("SN-NEW", null)).isInstanceOf(BusinessException.class);
        verify(deviceService).noteSeen("SN-NEW");
    }

    @Test
    void approvedDevice_withoutKey_isAccepted() {
        when(deviceService.noteSeen("SN-OK")).thenReturn(true);
        service.verifyDeviceRequest("SN-OK", null);
    }

    @Test
    void environmentWhitelist_stillWorksAsBootstrap() {
        ReflectionTestUtils.setField(service, "allowedDeviceSerials", "SN-ENV, SN-2");
        when(deviceService.noteSeen("SN-ENV")).thenReturn(false);
        service.verifyDeviceRequest("sn-env", null);
    }

    @Test
    void noKeyAndNoSerial_isRejected() {
        assertThatThrownBy(() -> service.verifyDeviceRequest(null, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void wrongKey_isRejected() {
        assertThatThrownBy(() -> service.verifyDeviceRequest("SN1", "wrong")).isInstanceOf(BusinessException.class);
    }
}
