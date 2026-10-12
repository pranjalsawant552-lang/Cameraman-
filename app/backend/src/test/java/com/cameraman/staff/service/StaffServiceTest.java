package com.cameraman.staff.service;

import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.common.exception.DuplicateResourceException;
import com.cameraman.staff.domain.StaffProfile;
import com.cameraman.staff.dto.StaffDto;
import com.cameraman.staff.repository.StaffRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.domain.UserStatus;
import com.cameraman.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffServiceTest {

    @Mock private StaffRepository staffRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookingRepository bookingRepository;

    private StaffService service;

    @BeforeEach
    void setUp() {
        service = new StaffService(staffRepository, userRepository, bookingRepository);
        when(staffRepository.save(any(StaffProfile.class))).thenAnswer(i -> i.getArgument(0));
    }

    private StaffProfile profile(String id, String userId, String employeeId) {
        StaffProfile p = StaffProfile.builder().id(id).userId(userId).employeeId(employeeId).build();
        when(staffRepository.findById(id)).thenReturn(Optional.of(p));
        User u = new User();
        u.setId(userId);
        u.setStatus(UserStatus.INACTIVE);
        u.setRoles(new HashSet<>());
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        return p;
    }

    private StaffDto.UpdateStaffRequest update(String employeeId) {
        StaffDto.UpdateStaffRequest r = new StaffDto.UpdateStaffRequest();
        r.setEmployeeId(employeeId);
        return r;
    }

    @Test
    void update_toAnEmployeeIdUsedByAnotherStaff_isRejected() {
        profile("s1", "u1", "E1");
        StaffProfile other = StaffProfile.builder().id("s2").userId("u2").employeeId("E2").build();
        when(staffRepository.findByEmployeeId("E2")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.updateStaff("s1", update("E2"))).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_keepingYourOwnEmployeeId_isFine() {
        StaffProfile me = profile("s1", "u1", "E1");
        when(staffRepository.findByEmployeeId("E1")).thenReturn(Optional.of(me));
        assertThat(service.updateStaff("s1", update("E1")).getEmployeeId()).isEqualTo("E1");
    }

    @Test
    void update_trimsAndAllowsClearing() {
        StaffProfile me = profile("s1", "u1", "E1");
        assertThat(service.updateStaff("s1", update("  ")).getEmployeeId()).isNull();
        assertThat(me.getEmployeeId()).isNull();
    }

    @Test
    void response_exposesTheLinkedAccountStatus() {
        profile("s1", "u1", "E1");
        assertThat(service.getById("s1").getUserStatus()).isEqualTo("INACTIVE");
    }
}
