package com.cameraman.user.service;

import com.cameraman.audit.service.AuditService;
import com.cameraman.auth.repository.RefreshTokenRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.role.domain.Role;
import com.cameraman.user.domain.User;
import com.cameraman.user.domain.UserStatus;
import com.cameraman.user.dto.UserDto;
import com.cameraman.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditService auditService;

    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(userRepository, userService, refreshTokenRepository, auditService);
    }

    private User user(String id, String... roles) {
        User u = new User();
        u.setId(id);
        u.setStatus(UserStatus.ACTIVE);
        Set<Role> set = new HashSet<>();
        for (String r : roles) set.add(Role.builder().name(r).build());
        u.setRoles(set);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void cannotDeactivateYourself() {
        user("a1", "ADMIN");
        assertThatThrownBy(() -> service.deactivate("a1", "a1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("your own");
        verify(userService, never()).deactivateUser(anyString());
    }

    @Test
    void lastActiveSuperAdminCannotBeDeactivated() {
        user("s1", "SUPER_ADMIN");
        user("s2", "SUPER_ADMIN");
        when(userRepository.countActiveByRoleName("SUPER_ADMIN")).thenReturn(1L);
        assertThatThrownBy(() -> service.deactivate("s1", "s2"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("last active super admin");
    }

    @Test
    void superAdminCanBeDeactivatedWhenAnotherRemains() {
        user("s1", "SUPER_ADMIN");
        user("s2", "SUPER_ADMIN");
        when(userRepository.countActiveByRoleName("SUPER_ADMIN")).thenReturn(2L);
        service.deactivate("s1", "s2");
        verify(userService).deactivateUser("s1");
    }

    @Test
    void plainAdminCannotDeactivateASuperAdmin() {
        user("s1", "SUPER_ADMIN");
        user("a1", "ADMIN");
        when(userRepository.countActiveByRoleName("SUPER_ADMIN")).thenReturn(3L);
        assertThatThrownBy(() -> service.deactivate("s1", "a1")).isInstanceOf(BusinessException.class);
    }

    @Test
    void deactivation_revokesRefreshTokens_andIsAudited() {
        user("p1", "PHOTOGRAPHER");
        user("a1", "ADMIN");
        service.deactivate("p1", "a1");
        verify(refreshTokenRepository).revokeAllUserTokens("p1");
        verify(auditService).log(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void settingStatusInactiveViaUpdate_followsTheSameRules() {
        user("a1", "ADMIN");
        UserDto.UpdateUserRequest req = new UserDto.UpdateUserRequest();
        req.setStatus("INACTIVE");
        assertThatThrownBy(() -> service.update("a1", req, "a1")).isInstanceOf(BusinessException.class);
        verify(userService, never()).updateUser(anyString(), any());
    }

    @Test
    void cannotStripSuperAdminRoleFromTheLastOne() {
        user("s1", "SUPER_ADMIN");
        user("s2", "SUPER_ADMIN");
        when(userRepository.countActiveByRoleName("SUPER_ADMIN")).thenReturn(1L);
        UserDto.UpdateUserRequest req = new UserDto.UpdateUserRequest();
        req.setRoleNames(List.of("ADMIN"));
        assertThatThrownBy(() -> service.update("s1", req, "s2")).isInstanceOf(BusinessException.class);
    }

    @Test
    void onlySuperAdminCanGrantSuperAdmin() {
        user("p1", "PHOTOGRAPHER");
        user("a1", "ADMIN");
        UserDto.UpdateUserRequest req = new UserDto.UpdateUserRequest();
        req.setRoleNames(List.of("SUPER_ADMIN"));
        assertThatThrownBy(() -> service.update("p1", req, "a1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("super admin");
    }

    @Test
    void cannotChangeYourOwnRoles() {
        user("a1", "ADMIN");
        UserDto.UpdateUserRequest req = new UserDto.UpdateUserRequest();
        req.setRoleNames(List.of("MANAGER"));
        assertThatThrownBy(() -> service.update("a1", req, "a1")).isInstanceOf(BusinessException.class);
    }

    @Test
    void ordinaryProfileEdit_passesThrough() {
        user("p1", "PHOTOGRAPHER");
        user("a1", "ADMIN");
        UserDto.UpdateUserRequest req = new UserDto.UpdateUserRequest();
        req.setFirstName("Meera");
        service.update("p1", req, "a1");
        verify(userService).updateUser("p1", req);
        verify(refreshTokenRepository, never()).revokeAllUserTokens(anyString());
    }
}
