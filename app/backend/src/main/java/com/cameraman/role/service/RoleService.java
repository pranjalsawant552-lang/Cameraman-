package com.cameraman.role.service;

import com.cameraman.audit.service.AuditService;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.DuplicateResourceException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.role.domain.Permission;
import com.cameraman.role.domain.Role;
import com.cameraman.role.dto.RoleDto;
import com.cameraman.role.repository.PermissionRepository;
import com.cameraman.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    /** Built-in roles the app depends on. SUPER_ADMIN is fully locked; the rest can't be deactivated. */
    static final java.util.Set<String> SYSTEM_ROLES = java.util.Set.of("SUPER_ADMIN", "ADMIN", "CLIENT");
    static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

    public RoleDto.RoleResponse createRole(RoleDto.CreateRoleRequest request) {
        return createRole(request, null);
    }

    public RoleDto.RoleResponse createRole(RoleDto.CreateRoleRequest request, String actorId) {
        String name = request.getName().trim().toUpperCase().replace(' ', '_');
        if (!NAME.matcher(name).matches()) {
            throw new BusinessException("Role names use capital letters, digits and underscores (2-50 characters)", "INVALID_ROLE_NAME");
        }
        if (roleRepository.existsByName(name)) {
            throw new DuplicateResourceException("Role", "name", name);
        }
        Role role = Role.builder().name(name).description(request.getDescription()).build();
        role = roleRepository.save(role);
        auditService.log("ROLE_CREATE", "Role", role.getId(), null, name, actorId);
        return toResponse(role);
    }

    public RoleDto.RoleResponse updateRole(String id, RoleDto.UpdateRoleRequest request) {
        return updateRole(id, request, null);
    }

    public RoleDto.RoleResponse updateRole(String id, RoleDto.UpdateRoleRequest request, String actorId) {
        Role role = findById(id);
        if ("SUPER_ADMIN".equals(role.getName())) {
            throw new BusinessException("The SUPER_ADMIN role is built in and cannot be modified", "SYSTEM_ROLE_LOCKED");
        }
        if (Boolean.FALSE.equals(request.getActive()) && SYSTEM_ROLES.contains(role.getName())) {
            throw new BusinessException("Built-in roles cannot be deactivated", "SYSTEM_ROLE_LOCKED");
        }
        if (request.getDescription() != null) role.setDescription(request.getDescription());
        if (request.getActive() != null) role.setActive(request.getActive());
        role = roleRepository.save(role);
        auditService.log("ROLE_UPDATE", "Role", id, null, role.getName(), actorId);
        return toResponse(role);
    }

    public RoleDto.RoleResponse assignPermissions(String roleId, List<String> permissionIds) {
        return assignPermissions(roleId, permissionIds, null);
    }

    public RoleDto.RoleResponse assignPermissions(String roleId, List<String> permissionIds, String actorId) {
        Role role = findById(roleId);
        if ("SUPER_ADMIN".equals(role.getName())) {
            throw new BusinessException("SUPER_ADMIN always has every permission and cannot be edited", "SYSTEM_ROLE_LOCKED");
        }
        List<String> ids = permissionIds == null ? List.of() : permissionIds;
        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(ids));
        if (permissions.size() != new HashSet<>(ids).size()) {
            throw new BusinessException("One or more permissions do not exist", "UNKNOWN_PERMISSION");
        }
        String before = String.valueOf(role.getPermissions().size());
        role.setPermissions(permissions);
        role = roleRepository.save(role);
        auditService.log("ROLE_PERMISSIONS", "Role", roleId, before + " permissions", permissions.size() + " permissions", actorId);
        return toResponse(role);
    }

    @Transactional(readOnly = true)
    public List<RoleDto.RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoleDto.PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream().map(this::toPermissionResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoleDto.RoleResponse getById(String id) {
        return toResponse(findById(id));
    }

    public RoleDto.PermissionResponse createPermission(RoleDto.CreatePermissionRequest request) {
        Permission permission = Permission.builder()
                .module(request.getModule()).action(request.getAction())
                .description(request.getDescription()).build();
        return toPermissionResponse(permissionRepository.save(permission));
    }

    private Role findById(String id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
    }

    private RoleDto.RoleResponse toResponse(Role r) {
        return RoleDto.RoleResponse.builder()
                .id(r.getId()).name(r.getName()).description(r.getDescription()).active(r.isActive())
                .permissions(r.getPermissions().stream().map(this::toPermissionResponse).collect(Collectors.toList()))
                .build();
    }

    private RoleDto.PermissionResponse toPermissionResponse(Permission p) {
        return RoleDto.PermissionResponse.builder()
                .id(p.getId()).module(p.getModule()).action(p.getAction())
                .description(p.getDescription()).active(p.isActive()).build();
    }
}
