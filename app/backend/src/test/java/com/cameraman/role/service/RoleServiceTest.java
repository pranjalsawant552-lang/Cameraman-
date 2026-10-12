package com.cameraman.role.service;

import com.cameraman.audit.service.AuditService;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.DuplicateResourceException;
import com.cameraman.role.domain.Permission;
import com.cameraman.role.domain.Role;
import com.cameraman.role.dto.RoleDto;
import com.cameraman.role.repository.PermissionRepository;
import com.cameraman.role.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
class RoleServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private AuditService auditService;

    private RoleService service;

    @BeforeEach
    void setUp() {
        service = new RoleService(roleRepository, permissionRepository, auditService);
        when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Role role(String name) {
        Role r = Role.builder().id("r-" + name).name(name).build();
        when(roleRepository.findById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    private RoleDto.CreateRoleRequest create(String name) {
        RoleDto.CreateRoleRequest r = new RoleDto.CreateRoleRequest();
        r.setName(name);
        return r;
    }

    @Test
    void create_normalizesName() {
        assertThat(service.createRole(create(" front desk "), "a1").getName()).isEqualTo("FRONT_DESK");
    }

    @Test
    void create_rejectsBadNames() {
        assertThatThrownBy(() -> service.createRole(create("x"), "a1")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.createRole(create("bad-name!"), "a1")).isInstanceOf(BusinessException.class);
    }

    @Test
    void create_rejectsDuplicates() {
        when(roleRepository.existsByName("EDITOR")).thenReturn(true);
        assertThatThrownBy(() -> service.createRole(create("editor"), "a1")).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void superAdmin_cannotBeModifiedOrStripped() {
        Role sa = role("SUPER_ADMIN");
        assertThatThrownBy(() -> service.assignPermissions(sa.getId(), List.of(), "a1")).isInstanceOf(BusinessException.class);
        RoleDto.UpdateRoleRequest up = new RoleDto.UpdateRoleRequest();
        up.setDescription("x");
        assertThatThrownBy(() -> service.updateRole(sa.getId(), up, "a1")).isInstanceOf(BusinessException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void builtInRoles_cannotBeDeactivated_butCustomOnesCan() {
        RoleDto.UpdateRoleRequest off = new RoleDto.UpdateRoleRequest();
        off.setActive(false);
        Role admin = role("ADMIN");
        assertThatThrownBy(() -> service.updateRole(admin.getId(), off, "a1")).isInstanceOf(BusinessException.class);
        Role custom = role("FRONT_DESK");
        assertThat(service.updateRole(custom.getId(), off, "a1").isActive()).isFalse();
    }

    @Test
    void assignPermissions_rejectsUnknownIds() {
        Role r = role("EDITOR");
        Permission p = Permission.builder().id("p1").module("GALLERY").action("VIEW").build();
        when(permissionRepository.findAllById(List.of("p1", "ghost"))).thenReturn(List.of(p));
        assertThatThrownBy(() -> service.assignPermissions(r.getId(), List.of("p1", "ghost"), "a1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assignPermissions_replacesTheSet_andAudits() {
        Role r = role("EDITOR");
        Permission p = Permission.builder().id("p1").module("GALLERY").action("VIEW").build();
        when(permissionRepository.findAllById(List.of("p1"))).thenReturn(List.of(p));
        assertThat(service.assignPermissions(r.getId(), List.of("p1"), "a1").getPermissions()).hasSize(1);
        verify(auditService).log(any(), any(), any(), any(), any(), any());
    }
}
