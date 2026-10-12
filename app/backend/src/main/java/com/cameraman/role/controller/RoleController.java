package com.cameraman.role.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.role.dto.RoleDto;
import com.cameraman.role.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role and permission management")
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RoleDto.RoleResponse>> create(@Valid @RequestBody RoleDto.CreateRoleRequest req, @com.cameraman.common.security.CurrentUser String actorId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Role created", roleService.createRole(req, actorId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<RoleDto.RoleResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(roleService.getAllRoles()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RoleDto.RoleResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RoleDto.RoleResponse>> update(
            @PathVariable String id, @Valid @RequestBody RoleDto.UpdateRoleRequest req,
            @com.cameraman.common.security.CurrentUser String actorId) {
        return ResponseEntity.ok(ApiResponse.success("Role updated", roleService.updateRole(id, req, actorId)));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RoleDto.RoleResponse>> assignPermissions(
            @PathVariable String id, @RequestBody List<String> permissionIds,
            @com.cameraman.common.security.CurrentUser String actorId) {
        return ResponseEntity.ok(ApiResponse.success("Permissions assigned", roleService.assignPermissions(id, permissionIds, actorId)));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<RoleDto.PermissionResponse>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.success(roleService.getAllPermissions()));
    }

    @PostMapping("/permissions")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RoleDto.PermissionResponse>> createPermission(
            @Valid @RequestBody RoleDto.CreatePermissionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Permission created", roleService.createPermission(req)));
    }
}
