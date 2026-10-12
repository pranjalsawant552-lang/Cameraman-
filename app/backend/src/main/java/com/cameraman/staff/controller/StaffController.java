package com.cameraman.staff.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.staff.dto.StaffDto;
import com.cameraman.staff.service.StaffService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
@Tag(name = "Staff", description = "Staff directory and task management")
public class StaffController {

    private final StaffService staffService;
    private final com.cameraman.staff.service.StaffOverviewService overviewService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:STAFF:CREATE')")
    public ResponseEntity<ApiResponse<StaffDto.StaffResponse>> create(
            @Valid @RequestBody StaffDto.CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Staff profile created", staffService.createStaff(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:STAFF:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<StaffDto.StaffResponse>>> getAll(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Boolean availableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                staffService.getAllStaff(department, availableOnly, PageRequest.of(page, size))));
    }

    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<StaffDto.StaffResponse>>> getAvailable() {
        return ResponseEntity.ok(ApiResponse.success(staffService.getAvailableStaff()));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StaffDto.StaffResponse>> getMyProfile(@com.cameraman.common.security.CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success(staffService.getByUserId(userId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:STAFF:VIEW')")
    public ResponseEntity<ApiResponse<StaffDto.StaffResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(staffService.getById(id)));
    }

    @GetMapping("/{id}/overview")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:STAFF:VIEW')")
    public ResponseEntity<ApiResponse<StaffDto.Overview>> overview(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(overviewService.overview(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:STAFF:UPDATE')")
    public ResponseEntity<ApiResponse<StaffDto.StaffResponse>> update(
            @PathVariable String id, @Valid @RequestBody StaffDto.UpdateStaffRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Staff profile updated", staffService.updateStaff(id, request)));
    }

    @PatchMapping("/{id}/toggle-availability")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:STAFF:UPDATE')")
    public ResponseEntity<ApiResponse<StaffDto.StaffResponse>> toggleAvailability(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Availability updated", staffService.toggleAvailability(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:STAFF:DELETE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        staffService.deleteStaff(id);
        return ResponseEntity.ok(ApiResponse.success("Staff profile removed", null));
    }
}
