package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.LeaveRequest;
import com.cameraman.attendance.domain.LeaveStatus;
import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.repository.LeaveRequestRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.notification.service.NotificationPublisher;
import com.cameraman.staff.domain.StaffProfile;
import com.cameraman.staff.repository.StaffRepository;
import com.cameraman.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveServiceTest {

    @Mock private LeaveRequestRepository leaveRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationPublisher notificationPublisher;

    private LeaveService service;
    private final LocalDate soon = LocalDate.now().plusDays(7);

    @BeforeEach
    void setUp() {
        service = new LeaveService(leaveRepository, staffRepository, userRepository, notificationPublisher);
        StaffProfile mine = StaffProfile.builder().id("s1").userId("u1").build();
        StaffProfile other = StaffProfile.builder().id("s2").userId("u2").build();
        when(staffRepository.findById("s1")).thenReturn(Optional.of(mine));
        when(staffRepository.findById("s2")).thenReturn(Optional.of(other));
        when(staffRepository.findByUserId("u1")).thenReturn(Optional.of(mine));
        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    private AttendanceDto.LeaveApplyRequest req(String staffId, LocalDate from, LocalDate to) {
        AttendanceDto.LeaveApplyRequest r = new AttendanceDto.LeaveApplyRequest();
        r.setStaffId(staffId);
        r.setFromDate(from);
        r.setToDate(to);
        r.setReason("family event");
        return r;
    }

    private LeaveRequest leave(String staffId, LeaveStatus status, LocalDate from) {
        LeaveRequest l = LeaveRequest.builder().id("l1").staffId(staffId).status(status).fromDate(from).toDate(from.plusDays(1)).build();
        when(leaveRepository.findById("l1")).thenReturn(Optional.of(l));
        return l;
    }

    @Test
    void staff_canApplyForThemselves() {
        assertThat(service.apply(req("s1", soon, soon.plusDays(1)), "u1", false).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void staff_cannotApplyForSomeoneElse() {
        assertThatThrownBy(() -> service.apply(req("s2", soon, soon), "u1", false)).isInstanceOf(ResourceNotFoundException.class);
        verify(leaveRepository, never()).save(any());
    }

    @Test
    void manager_canApplyOnBehalfOfStaff() {
        assertThat(service.apply(req("s2", soon, soon), "boss", true)).isNotNull();
    }

    @Test
    void endBeforeStart_isRejected() {
        assertThatThrownBy(() -> service.apply(req("s1", soon.plusDays(2), soon), "u1", false)).isInstanceOf(BusinessException.class);
    }

    @Test
    void tooLongRange_isRejected() {
        assertThatThrownBy(() -> service.apply(req("s1", soon, soon.plusDays(60)), "u1", false)).isInstanceOf(BusinessException.class);
    }

    @Test
    void pastDates_areRejectedForStaff_butNotManagers() {
        LocalDate past = LocalDate.now().minusDays(5);
        assertThatThrownBy(() -> service.apply(req("s1", past, past), "u1", false)).isInstanceOf(BusinessException.class);
        assertThat(service.apply(req("s1", past, past), "boss", true)).isNotNull();
    }

    @Test
    void overlappingLeave_isRejected() {
        when(leaveRepository.existsActiveOverlapping("s1", soon, soon)).thenReturn(true);
        assertThatThrownBy(() -> service.apply(req("s1", soon, soon), "u1", false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("already");
    }

    @Test
    void cancel_ownPending_isAllowed() {
        leave("s1", LeaveStatus.PENDING, soon);
        assertThat(service.cancel("l1", "u1", false).getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_someoneElsesLeave_looksLikeNotFound() {
        leave("s2", LeaveStatus.PENDING, soon);
        assertThatThrownBy(() -> service.cancel("l1", "u1", false)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancel_approvedLeaveThatStarted_needsAManager() {
        leave("s1", LeaveStatus.APPROVED, LocalDate.now());
        assertThatThrownBy(() -> service.cancel("l1", "u1", false)).isInstanceOf(BusinessException.class);
        assertThat(service.cancel("l1", "boss", true).getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_alreadyRejected_isRejected() {
        leave("s1", LeaveStatus.REJECTED, soon);
        assertThatThrownBy(() -> service.cancel("l1", "u1", false)).isInstanceOf(BusinessException.class);
    }

    @Test
    void listForStaff_otherPersonsLeave_isHiddenFromStaff() {
        assertThatThrownBy(() -> service.listForStaff("s2", "u1", false)).isInstanceOf(ResourceNotFoundException.class);
        when(leaveRepository.findByStaffIdOrderByFromDateDesc("s1")).thenReturn(List.of());
        assertThat(service.listForStaff("s1", "u1", false)).isEmpty();
        assertThat(service.listForStaff("s2", "boss", true)).isEmpty();
    }

    @Test
    void approve_thenCannotBeReviewedAgain() {
        LeaveRequest l = leave("s1", LeaveStatus.PENDING, soon);
        assertThat(service.approve("l1", "boss", null).getStatus()).isEqualTo("APPROVED");
        assertThat(l.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThatThrownBy(() -> service.reject("l1", "boss", null)).isInstanceOf(BusinessException.class);
    }
}
