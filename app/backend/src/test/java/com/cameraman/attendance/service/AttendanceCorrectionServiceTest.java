package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.AttendanceCorrection;
import com.cameraman.attendance.domain.LeaveStatus;
import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.repository.AttendanceCorrectionRepository;
import com.cameraman.audit.service.AuditService;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.notification.service.NotificationPublisher;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceCorrectionServiceTest {

    @Mock private AttendanceCorrectionRepository repository;
    @Mock private StaffRepository staffRepository;
    @Mock private UserRepository userRepository;
    @Mock private AttendanceService attendanceService;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private AuditService auditService;

    private AttendanceCorrectionService service;
    private final LocalDateTime yesterday = LocalDateTime.now().minusDays(1).withNano(0);

    @BeforeEach
    void setUp() {
        service = new AttendanceCorrectionService(repository, staffRepository, userRepository, attendanceService, notificationPublisher, auditService);
        StaffProfile me = StaffProfile.builder().id("s1").userId("u1").build();
        when(staffRepository.findByUserId("u1")).thenReturn(Optional.of(me));
        when(staffRepository.findById("s1")).thenReturn(Optional.of(me));
        when(repository.save(any(AttendanceCorrection.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void request_createsPendingAndNotifiesManagers() {
        AttendanceCorrection c = service.request("u1", yesterday, "out", "Forgot to punch out");
        assertThat(c.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(c.getDirection().name()).isEqualTo("OUT");
        verify(notificationPublisher).notifyRoles(any(), org.mockito.ArgumentMatchers.eq("ATTENDANCE_CORRECTION_REQUESTED"), any(), any(), any(), any());
    }

    @Test
    void request_rejectsFutureOldBlankAndDuplicate() {
        assertThatThrownBy(() -> service.request("u1", LocalDateTime.now().plusHours(1), "IN", "x")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.request("u1", LocalDateTime.now().minusDays(60), "IN", "x")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.request("u1", yesterday, "IN", "  ")).isInstanceOf(BusinessException.class);
        when(repository.existsByStaffIdAndProposedTimeAndStatus("s1", yesterday, LeaveStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> service.request("u1", yesterday, "IN", "again")).isInstanceOf(BusinessException.class);
    }

    @Test
    void request_withoutAStaffProfile_isRejected() {
        when(staffRepository.findByUserId("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request("nobody", yesterday, "IN", "x")).isInstanceOf(BusinessException.class);
    }

    private AttendanceCorrection pending() {
        AttendanceCorrection c = AttendanceCorrection.builder().id("c1").staffId("s1").proposedTime(yesterday).reason("forgot").build();
        when(repository.findById("c1")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void approve_addsAManualPunch_linksIt_andAudits() {
        AttendanceCorrection c = pending();
        when(attendanceService.manualEntry(any())).thenReturn(AttendanceDto.RecordResponse.builder().id("rec1").build());

        AttendanceCorrection res = service.approve("c1", "boss", "ok");

        ArgumentCaptor<AttendanceDto.ManualEntryRequest> cap = ArgumentCaptor.forClass(AttendanceDto.ManualEntryRequest.class);
        verify(attendanceService).manualEntry(cap.capture());
        assertThat(cap.getValue().getStaffId()).isEqualTo("s1");
        assertThat(cap.getValue().getPunchTime()).isEqualTo(yesterday);
        assertThat(res.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(res.getRecordId()).isEqualTo("rec1");
        verify(auditService).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reject_doesNotCreateAPunch() {
        pending();
        assertThat(service.reject("c1", "boss", "not needed").getStatus()).isEqualTo(LeaveStatus.REJECTED);
        verify(attendanceService, never()).manualEntry(any());
    }

    @Test
    void reviewedRequest_cannotBeReviewedAgain() {
        AttendanceCorrection c = pending();
        c.setStatus(LeaveStatus.APPROVED);
        assertThatThrownBy(() -> service.approve("c1", "boss", null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.reject("c1", "boss", null)).isInstanceOf(BusinessException.class);
    }
}
